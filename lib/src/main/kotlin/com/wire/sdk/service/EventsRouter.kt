/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.sdk.service

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import com.wire.crypto.Welcome
import com.wire.crypto.toGroupInfo
import com.wire.crypto.toWelcome
import com.wire.sdk.WireEventsHandler
import com.wire.sdk.WireEventsHandlerDefault
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.client.BackendClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.http.EventContentDTO
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.protobuf.ProtobufDeserializer
import com.wire.sdk.persistence.TeamStorage
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import com.wire.sdk.model.Conversation
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.service.conversation.ConversationService
import io.ktor.client.plugins.ResponseException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class EventsRouter internal constructor(
    private val teamStorage: TeamStorage,
    private val conversationService: ConversationService,
    private val backendClient: BackendClient,
    private val wireEventsHandler: WireEventsHandler,
    private val cryptoClient: CryptoClient,
    private val mlsFallbackStrategy: MlsFallbackStrategy,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Coroutine scope for running callbacks without blocking the main event processing
    private val handlerScope = CoroutineScope(
        SupervisorJob() + dispatcher + CoroutineExceptionHandler { _, throwable ->
            logger.error("Uncaught exception in event handler callback", throwable)
        }
    )

    /**
     * Map of conversationId to its dedicated event processing channel.
     * Each channel ensures FIFO ordering for events within the same conversation.
     * Uses BUFFERED capacity to provide backpressure when a conversation
     * is processing events slower than they arrive.
     */
    private val conversationChannels = ConcurrentHashMap<String, Channel<suspend () -> Unit>>()

    /**
     * Gets or creates a dedicated channel for a conversation.
     * Each channel has a consumer coroutine that processes events sequentially.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun getOrCreateChannel(channelKey: String): Channel<suspend () -> Unit> {
        return conversationChannels.computeIfAbsent(channelKey) {
            Channel<suspend () -> Unit>(Channel.BUFFERED).also { channel ->
                handlerScope.launch {
                    for (processor in channel) {
                        try {
                            processor()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            logger.error(
                                "Error processing event for channel {}",
                                channelKey,
                                e
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Extracts the channel name from an event using the conversationId for most events,
     * or a default one if the event is not conversation-specific.
     */
    private fun extractChannelKey(event: EventContentDTO): String {
        return if (event is EventContentDTO.Conversation) {
            "${event.qualifiedConversation.id}@${event.qualifiedConversation.domain}"
        } else {
            NON_CONVERSATION_EVENTS
        }
    }

    /**
     * Routes events to their appropriate handlers.
     * Events are dispatched to conversation-specific channels for parallel processing.
     * Events within the same conversation are processed sequentially (FIFO) to maintain ordering.
     * Returns immediately after dispatching, without waiting for processing to complete.
     */
    internal suspend fun route(eventResponse: EventResponse) {
        logger.debug("Event received: {}", eventResponse)
        logger.info(dispatcher.toString())

        eventResponse.payload?.forEach { event ->
            val channelKey = extractChannelKey(event)
            val channel = getOrCreateChannel(channelKey)
            channel.send { processEvent(event) }
        }
    }

    /**
     * Processes a single event based on its type.
     */
    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    private suspend fun processEvent(event: EventContentDTO) {
        when (event) {
            is EventContentDTO.TeamInvite -> {
                val teamId = TeamId(event.teamId)
                logger.info("Team invite from: $teamId")
                newTeamInvite(teamId)
            }
            is EventContentDTO.Conversation.MemberUpdateDTO -> {
                event.data.role?.let { newRole ->
                    conversationService.saveMembers(
                        conversationId = event.qualifiedConversation,
                        members = listOf(
                            ConversationMember(
                                userId = event.data.qualifiedUserId,
                                role = ConversationRole.fromApi(newRole)
                            )
                        )
                    )
                }
            }
            is EventContentDTO.Conversation.NewConversationDTO -> {
                logger.info("Joining conversation: $event")

                conversationService.saveConversationWithMembers(
                    qualifiedConversation = event.qualifiedConversation,
                    conversationResponse = event.data
                )
            }

            is EventContentDTO.Conversation.DeleteConversation -> {
                logger.info("Received event: ConversationDeleted, $event")
                conversationService.processDeletedConversation(event.qualifiedConversation)
                handlerScope.launch {
                    when (wireEventsHandler) {
                        is WireEventsHandlerDefault ->
                            wireEventsHandler.onConversationDeleted(event.qualifiedConversation)
                        is WireEventsHandlerSuspending ->
                            wireEventsHandler.onConversationDeleted(event.qualifiedConversation)
                    }
                }
            }

            is EventContentDTO.Conversation.MemberJoin -> {
                logger.info("Joining event from: ${event.qualifiedConversation}")
                val members = event.data.users.map {
                    ConversationMember(
                        userId = it.userId,
                        role = it.conversationRole
                    )
                }
                conversationService.saveMembers(event.qualifiedConversation, members)
                handlerScope.launch {
                    when (wireEventsHandler) {
                        is WireEventsHandlerDefault ->
                            wireEventsHandler.onUserJoinedConversation(
                                conversationId = event.qualifiedConversation,
                                members = members
                            )
                        is WireEventsHandlerSuspending ->
                            wireEventsHandler.onUserJoinedConversation(
                                conversationId = event.qualifiedConversation,
                                members = members
                            )
                    }
                }
            }

            is EventContentDTO.Conversation.MemberLeave -> {
                logger.info("Leaving event from: ${event.qualifiedConversation}")
                conversationService.deleteMembers(
                    conversationId = event.qualifiedConversation,
                    users = event.data.users
                )
                handlerScope.launch {
                    when (wireEventsHandler) {
                        is WireEventsHandlerDefault ->
                            wireEventsHandler.onUserLeftConversation(
                                conversationId = event.qualifiedConversation,
                                members = event.data.users
                            )
                        is WireEventsHandlerSuspending ->
                            wireEventsHandler.onUserLeftConversation(
                                conversationId = event.qualifiedConversation,
                                members = event.data.users
                            )
                    }
                }
            }

            is EventContentDTO.Conversation.MlsWelcome -> {
                logger.info("Joining MLS conversation: ${event.qualifiedConversation}")
                val welcome = Base64.getDecoder().decode(event.data).toWelcome()
                handleWelcomeEvent(
                    welcome = welcome,
                    qualifiedConversation = event.qualifiedConversation
                )
            }

            is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                val mlsGroupId = conversationService
                    .getConversationById(event.qualifiedConversation)
                    .mlsGroupId
                try {
                    val message = cryptoClient.decryptMls(
                        mlsGroupId = mlsGroupId,
                        encryptedMessage = event.data
                    )

                    logger.debug("Decryption successful")
                    if (message == null) {
                        logger.debug("Decryption success but no message, probably epoch update")
                        return
                    }

                    forwardMessage(
                        message = message,
                        conversationId = event.qualifiedConversation,
                        sender = event.qualifiedFrom,
                        timestamp = event.time
                    )
                } catch (exception: MlsException) {
                    logger.debug("Message decryption failed, MlsException: ", exception)
                    mlsFallbackStrategy.verifyConversationOutOfSync(
                        mlsGroupId = mlsGroupId,
                        conversationId = event.qualifiedConversation
                    )
                } catch (exception: CoreCryptoException.Mls) {
                    logger.debug(
                        "Message decryption failed, CoreCryptoException.Mls:",
                        exception
                    )
                    mlsFallbackStrategy.verifyConversationOutOfSync(
                        mlsGroupId = mlsGroupId,
                        conversationId = event.qualifiedConversation
                    )
                }
            }

            is EventContentDTO.Conversation.Typing -> {
                // Ignore silently
            }

            is EventContentDTO.Unknown -> {
                logger.warn("Unknown event type: {}", event)
            }
        }
    }

    private suspend fun handleWelcomeEvent(
        welcome: Welcome,
        qualifiedConversation: QualifiedId
    ) {
        processWelcomeMessage(
            welcome = welcome,
            qualifiedConversation = qualifiedConversation
        )
        val conversationResponse = backendClient.getConversation(qualifiedConversation)
        val (conversationEntity, members) = conversationService.saveConversationWithMembers(
            qualifiedConversation = qualifiedConversation,
            conversationResponse = conversationResponse
        )

        if (cryptoClient.hasTooFewKeyPackageCount()) {
            cryptoClient.getCryptoClientId()?.let { cryptoClientId ->
                backendClient.uploadMlsKeyPackages(
                    cryptoClientId = cryptoClientId,
                    mlsKeyPackages =
                        cryptoClient.mlsGenerateKeyPackages().map { it.copyBytes() }
                )
            }
        }

        val conversationModel = Conversation.fromEntity(conversationEntity)

        handlerScope.launch {
            when (wireEventsHandler) {
                is WireEventsHandlerDefault -> wireEventsHandler.onAppAddedToConversation(
                    conversation = conversationModel,
                    members = members
                )
                is WireEventsHandlerSuspending -> wireEventsHandler.onAppAddedToConversation(
                    conversation = conversationModel,
                    members = members
                )
            }
        }
    }

    /**
     * Forwards the message to the appropriate handler (blocking or suspending) based on its type.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun forwardMessage(
        message: ByteArray,
        conversationId: QualifiedId,
        sender: QualifiedId,
        timestamp: Instant
    ) {
        val genericMessage = GenericMessage.parseFrom(message)
        val wireMessage = ProtobufDeserializer.processGenericMessage(
            genericMessage = genericMessage,
            conversationId = conversationId,
            sender = sender,
            timestamp = timestamp
        )

        handlerScope.launch {
            when (wireEventsHandler) {
                is WireEventsHandlerDefault -> when (wireMessage) {
                    is WireMessage.Text -> wireEventsHandler.onTextMessageReceived(wireMessage)
                    is WireMessage.Asset -> wireEventsHandler.onAssetMessageReceived(wireMessage)
                    is WireMessage.ButtonAction -> wireEventsHandler.onButtonClicked(wireMessage)
                    is WireMessage.Ping -> wireEventsHandler.onPingReceived(wireMessage)
                    is WireMessage.Location ->
                        wireEventsHandler.onLocationMessageReceived(wireMessage)
                    is WireMessage.Deleted -> wireEventsHandler.onMessageDeleted(wireMessage)
                    is WireMessage.Receipt -> wireEventsHandler.onMessageDelivered(wireMessage)
                    is WireMessage.TextEdited -> wireEventsHandler.onTextMessageEdited(wireMessage)
                    is WireMessage.Reaction ->
                        wireEventsHandler.onMessageReactionReceived(wireMessage)
                    is WireMessage.InCallEmoji -> wireEventsHandler.onInCallReactionReceived(
                        wireMessage
                    )
                    is WireMessage.InCallHandRaise -> wireEventsHandler.onInCallHandRaiseReceived(
                        wireMessage
                    )
                    is WireMessage.Ignored -> logger.warn("Ignored event received.")
                    is WireMessage.Unknown -> logger.warn("Unknown event received.")
                    is WireMessage.Composite -> logger.debug("Composite event received.")
                    is WireMessage.ButtonActionConfirmation ->
                        logger.debug("ButtonActionConfirmation event received.")
                    is WireMessage.CompositeEdited ->
                        logger.debug("CompositeEdited event received.")
                }
                is WireEventsHandlerSuspending -> when (wireMessage) {
                    is WireMessage.Text -> wireEventsHandler.onTextMessageReceived(wireMessage)
                    is WireMessage.Asset -> wireEventsHandler.onAssetMessageReceived(wireMessage)
                    is WireMessage.ButtonAction -> wireEventsHandler.onButtonClicked(wireMessage)
                    is WireMessage.Ping -> wireEventsHandler.onPingReceived(wireMessage)
                    is WireMessage.Location ->
                        wireEventsHandler.onLocationMessageReceived(wireMessage)
                    is WireMessage.Deleted -> wireEventsHandler.onMessageDeleted(wireMessage)
                    is WireMessage.Receipt -> wireEventsHandler.onMessageDelivered(wireMessage)
                    is WireMessage.TextEdited -> wireEventsHandler.onTextMessageEdited(wireMessage)
                    is WireMessage.Reaction ->
                        wireEventsHandler.onMessageReactionReceived(wireMessage)
                    is WireMessage.InCallEmoji -> wireEventsHandler.onInCallReactionReceived(
                        wireMessage
                    )
                    is WireMessage.InCallHandRaise -> wireEventsHandler.onInCallHandRaiseReceived(
                        wireMessage
                    )
                    is WireMessage.Ignored -> logger.warn("Ignored event received.")
                    is WireMessage.Unknown -> logger.warn("Unknown event received.")
                    is WireMessage.Composite -> logger.debug("Composite event received.")
                    is WireMessage.ButtonActionConfirmation ->
                        logger.debug("ButtonActionConfirmation event received.")
                    is WireMessage.CompositeEdited ->
                        logger.debug("CompositeEdited event received.")
                }
            }
        }
    }

    /**
     * Processes the MLS welcome package.
     * Orphan welcomes are recovered by sending a join request to the Backend.
     */
    private suspend fun processWelcomeMessage(
        welcome: Welcome,
        qualifiedConversation: QualifiedId
    ) {
        try {
            cryptoClient.processWelcomeMessage(welcome)
        } catch (ex: CoreCryptoException.Mls) {
            if (ex.mlsError is MlsException.OrphanWelcome) {
                logger.info("Cannot process welcome, ask to join the conversation")
                val groupInfo =
                    backendClient.getConversationGroupInfo(qualifiedConversation)
                cryptoClient.joinMlsConversationRequest(groupInfo.toGroupInfo())
            } else {
                logger.error("Cannot process welcome -- ${ex.mlsError}", ex)
                throw WireException.CryptographicSystemError("Cannot process welcome")
            }
        }
    }

    private suspend fun newTeamInvite(teamId: TeamId) {
        try {
            logger.debug("Confirming team: {}", teamId.value.toString())
            backendClient.confirmTeam(teamId)
            teamStorage.save(teamId) // Can be done async ?
        } catch (e: ResponseException) {
            logger.error("Error fetching events from the backend", e)
        } catch (e: WireException) {
            logger.error("Internal error", e)
        }
    }

    override fun close() {
        conversationChannels.values.forEach { it.close() }
        conversationChannels.clear()
        handlerScope.cancel()
    }

    companion object {
        private const val NON_CONVERSATION_EVENTS = "NON_CONVERSATION_EVENTS"
    }
}
