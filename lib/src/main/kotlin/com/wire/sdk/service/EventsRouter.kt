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

import com.wire.crypto.ClientId
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MLSGroupId
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
import com.wire.sdk.model.ConversationData
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.http.EventContentDTO
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.getDecodedMlsGroupId
import com.wire.sdk.model.protobuf.ProtobufDeserializer
import com.wire.sdk.persistence.ConversationStorage
import com.wire.sdk.persistence.TeamStorage
import com.wire.sdk.utils.obfuscateGroupId
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import com.wire.sdk.calling.CallManager
import io.ktor.client.plugins.ResponseException
import java.util.Base64
import kotlin.time.Instant
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class EventsRouter internal constructor(
    private val teamStorage: TeamStorage,
    private val conversationStorage: ConversationStorage,
    private val backendClient: BackendClient,
    private val wireEventsHandler: WireEventsHandler,
    private val cryptoClient: CryptoClient,
    private val mlsFallbackStrategy: MlsFallbackStrategy,
    private val callingManager: CallManager
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Suppress("LongMethod", "NestedBlockDepth", "CyclomaticComplexMethod")
    internal suspend fun route(eventResponse: EventResponse) {
        logger.debug("Event received: {}", eventResponse)
        eventResponse.payload?.forEach { event ->
            when (event) {
                is EventContentDTO.TeamInvite -> {
                    val teamId = TeamId(event.teamId)
                    logger.info("Team invite from: $teamId")
                    newTeamInvite(teamId)
                }
                is EventContentDTO.Conversation.NewConversationDTO -> {
                    // The Application has created a conversation or got invited to one.
                    // The mls-welcome event is used to allow the Application react
                    // to the invitation, this event is simply logged
                    logger.info("Joining conversation: $event")
                }

                is EventContentDTO.Conversation.DeleteConversation -> {
                    logger.info("Delete conversation: $event")
                    conversationStorage.delete(event.qualifiedConversation)
                    when (wireEventsHandler) {
                        is WireEventsHandlerDefault -> wireEventsHandler.onConversationDelete(
                            event.qualifiedConversation
                        )
                        is WireEventsHandlerSuspending -> wireEventsHandler.onConversationDelete(
                            event.qualifiedConversation
                        )
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
                    conversationStorage.saveMembers(event.qualifiedConversation, members)
                    when (wireEventsHandler) {
                        is WireEventsHandlerDefault -> wireEventsHandler.onMemberJoin(
                            conversationId = event.qualifiedConversation,
                            members = members
                        )
                        is WireEventsHandlerSuspending -> wireEventsHandler.onMemberJoin(
                            conversationId = event.qualifiedConversation,
                            members = members
                        )
                    }
                }

                is EventContentDTO.Conversation.MemberLeave -> {
                    logger.info("Leaving event from: ${event.qualifiedConversation}")
                    conversationStorage.deleteMembers(
                        conversationId = event.qualifiedConversation,
                        users = event.data.users
                    )
                    when (wireEventsHandler) {
                        is WireEventsHandlerDefault -> wireEventsHandler.onMemberLeave(
                            conversationId = event.qualifiedConversation,
                            members = event.data.users
                        )
                        is WireEventsHandlerSuspending -> wireEventsHandler.onMemberLeave(
                            conversationId = event.qualifiedConversation,
                            members = event.data.users
                        )
                    }
                }

                is EventContentDTO.Conversation.MlsWelcome -> {
                    logger.info("Joining MLS conversation: ${event.qualifiedConversation}")
                    val welcome = Base64.getDecoder().decode(event.data).toWelcome()
                    val groupId = fetchGroupIdFromWelcome(
                        cryptoClient = cryptoClient,
                        welcome = welcome,
                        event = event
                    )

                    handleJoiningConversation(
                        qualifiedConversation = event.qualifiedConversation,
                        groupId = groupId
                    )
                }

                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    val groupId = fetchGroupIdFromConversation(event.qualifiedConversation)
                    try {
                        val decrypted = cryptoClient.decryptMls(
                            mlsGroupId = groupId,
                            encryptedMessage = event.message
                        )

                        logger.debug("Decryption successful")
                        if (decrypted.message == null || decrypted.senderClientId == null) {
                            logger.debug("Decrypt success but no message, probably epoch update")
                            return
                        } else {
                            forwardMessage(
                                message = decrypted.message!!,
                                conversationId = event.qualifiedConversation,
                                senderUser = event.qualifiedFrom,
                                senderClient = decrypted.senderClientId!!,
                                timestamp = event.time
                            )
                        }
                    } catch (exception: MlsException) {
                        logger.debug(
                            "Message decryption failed," +
                                " MlsException: ",
                            exception
                        )
                        mlsFallbackStrategy.verifyConversationOutOfSync(
                            mlsGroupId = groupId,
                            conversationId = event.qualifiedConversation
                        )
                    } catch (exception: CoreCryptoException.Mls) {
                        logger.debug(
                            "Message decryption failed, CoreCryptoException.Mls:",
                            exception
                        )
                        mlsFallbackStrategy.verifyConversationOutOfSync(
                            mlsGroupId = groupId,
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
    }

    private suspend fun handleJoiningConversation(
        qualifiedConversation: QualifiedId,
        groupId: MLSGroupId?
    ): MLSGroupId {
        val conversation = backendClient.getConversation(qualifiedConversation)
        val mlsGroupId = groupId ?: conversation.getDecodedMlsGroupId()

        val conversationName = if (conversation.type == ConversationResponse.Type.ONE_TO_ONE) {
            backendClient.getUserData(userId = conversation.members.others.first().id).name
        } else {
            conversation.name
        }

        val conversationData =
            ConversationData(
                id = qualifiedConversation,
                name = conversationName,
                mlsGroupId = mlsGroupId,
                teamId = conversation.teamId?.let { TeamId(it) },
                type = ConversationData.Type.fromApi(value = conversation.type)
            )
        val members = conversation.members.others.map {
            ConversationMember(
                userId = it.id,
                role = it.conversationRole
            )
        }
        logger.debug("Conversation data: {}", conversationData)
        logger.debug("Conversation members: {}", members)

        // Saves the conversation in the local database, used later to decrypt messages
        conversationStorage.save(conversationData)
        conversationStorage.saveMembers(qualifiedConversation, members)

        if (cryptoClient.hasTooFewKeyPackageCount()) {
            cryptoClient.getAppClientId()?.let { appClientId ->
                backendClient.uploadMlsKeyPackages(
                    appClientId = appClientId,
                    mlsKeyPackages =
                        cryptoClient.mlsGenerateKeyPackages().map { it.value.copyBytes() }
                )
            }
        }

        groupId?.run {
            when (wireEventsHandler) {
                is WireEventsHandlerDefault -> wireEventsHandler.onConversationJoin(
                    conversation = conversationData,
                    members = members
                )
                is WireEventsHandlerSuspending -> wireEventsHandler.onConversationJoin(
                    conversation = conversationData,
                    members = members
                )
            }
        }

        return mlsGroupId
    }

    /**
     * Forwards the message to the appropriate handler (blocking or suspending) based on its type.
     */
    @Suppress("CyclomaticComplexMethod")
    private suspend fun forwardMessage(
        message: ByteArray,
        conversationId: QualifiedId,
        senderUser: QualifiedId,
        senderClient: ClientId,
        timestamp: Instant
    ) {
        val genericMessage = GenericMessage.parseFrom(message)
        val wireMessage = ProtobufDeserializer.processGenericMessage(
            genericMessage = genericMessage,
            conversationId = conversationId,
            sender = senderUser,
            timestamp = timestamp
        )

        when (wireEventsHandler) {
            is WireEventsHandlerDefault -> when (wireMessage) {
                is WireMessage.Text -> wireEventsHandler.onMessage(wireMessage)
                is WireMessage.Asset -> wireEventsHandler.onAsset(wireMessage)
                is WireMessage.Composite -> wireEventsHandler.onComposite(wireMessage)
                is WireMessage.ButtonAction -> wireEventsHandler.onButtonAction(wireMessage)
                is WireMessage.ButtonActionConfirmation ->
                    wireEventsHandler.onButtonActionConfirmation(wireMessage)
                is WireMessage.Knock -> wireEventsHandler.onKnock(wireMessage)
                is WireMessage.Location -> wireEventsHandler.onLocation(wireMessage)
                is WireMessage.Deleted -> wireEventsHandler.onDeletedMessage(wireMessage)
                is WireMessage.Receipt -> wireEventsHandler.onReceiptConfirmation(wireMessage)
                is WireMessage.TextEdited -> wireEventsHandler.onTextEdited(wireMessage)
                is WireMessage.CompositeEdited -> wireEventsHandler.onCompositeEdited(wireMessage)
                is WireMessage.Reaction -> wireEventsHandler.onReaction(wireMessage)
                is WireMessage.InCallEmoji -> wireEventsHandler.onInCallEmoji(wireMessage)
                is WireMessage.InCallHandRaise -> wireEventsHandler.onInCallHandRaise(wireMessage)
                is WireMessage.Calling -> callingManager.onCallingMessageReceived(
                    wireMessage,
                    senderClient
                )
                is WireMessage.Ignored -> logger.warn("Ignored event received.")
                is WireMessage.Unknown -> logger.warn("Unknown event received.")
            }
            is WireEventsHandlerSuspending -> when (wireMessage) {
                is WireMessage.Text -> wireEventsHandler.onMessage(wireMessage)
                is WireMessage.Asset -> wireEventsHandler.onAsset(wireMessage)
                is WireMessage.Composite -> wireEventsHandler.onComposite(wireMessage)
                is WireMessage.ButtonAction -> wireEventsHandler.onButtonAction(wireMessage)
                is WireMessage.ButtonActionConfirmation ->
                    wireEventsHandler.onButtonActionConfirmation(wireMessage)
                is WireMessage.Knock -> wireEventsHandler.onKnock(wireMessage)
                is WireMessage.Location -> wireEventsHandler.onLocation(wireMessage)
                is WireMessage.Deleted -> wireEventsHandler.onDeletedMessage(wireMessage)
                is WireMessage.Receipt -> wireEventsHandler.onReceiptConfirmation(wireMessage)
                is WireMessage.TextEdited -> wireEventsHandler.onTextEdited(wireMessage)
                is WireMessage.CompositeEdited -> wireEventsHandler.onCompositeEdited(wireMessage)
                is WireMessage.Reaction -> wireEventsHandler.onReaction(wireMessage)
                is WireMessage.InCallEmoji -> wireEventsHandler.onInCallEmoji(wireMessage)
                is WireMessage.InCallHandRaise -> wireEventsHandler.onInCallHandRaise(wireMessage)
                is WireMessage.Calling -> callingManager.onCallingMessageReceived(
                    wireMessage,
                    senderClient
                )
                is WireMessage.Ignored -> logger.warn("Ignored event received.")
                is WireMessage.Unknown -> logger.warn("Unknown event received.")
            }
        }
    }

    /**
     * Processes the MLS welcome package and returns the group ID.
     * Orphan welcomes are recovered by sending a join request to the Backend,
     * which still returns the groupId after accepting the proposal.
     */
    private suspend fun fetchGroupIdFromWelcome(
        cryptoClient: CryptoClient,
        welcome: Welcome,
        event: EventContentDTO.Conversation.MlsWelcome
    ): MLSGroupId {
        return try {
            cryptoClient.processWelcomeMessage(welcome)
        } catch (ex: CoreCryptoException.Mls) {
            if (ex.exception is MlsException.OrphanWelcome) {
                logger.info("Cannot process welcome, ask to join the conversation")
                val groupInfo =
                    backendClient.getConversationGroupInfo(event.qualifiedConversation)
                cryptoClient.joinMlsConversationRequest(groupInfo.toGroupInfo())
            } else {
                logger.error("Cannot process welcome -- ${ex.exception}", ex)
                throw WireException.CryptographicSystemError("Cannot process welcome")
            }
        }
    }

    /**
     * Fetches the group ID of the conversation in the local database.
     */
    private suspend fun fetchGroupIdFromConversation(conversationId: QualifiedId): MLSGroupId {
        logger.debug(
            "Searching for group ID of conversation: {}:{}",
            conversationId.id,
            conversationId.domain
        )

        val conversation = conversationStorage.getById(conversationId)
        var mlsGroupId = conversation?.mlsGroupId

        if (mlsGroupId == null) {
            mlsGroupId = handleJoiningConversation(
                qualifiedConversation = conversationId,
                groupId = null
            )
        }

        logger.debug("Returning mlsGroupId: ${mlsGroupId.obfuscateGroupId()}")
        return mlsGroupId
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
}
