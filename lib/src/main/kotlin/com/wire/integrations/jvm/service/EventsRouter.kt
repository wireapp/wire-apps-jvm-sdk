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

package com.wire.integrations.jvm.service

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MlsException
import com.wire.crypto.Welcome
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.ConversationMember
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.model.protobuf.ProtobufDeserializer
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import io.ktor.client.plugins.ResponseException
import org.slf4j.LoggerFactory
import java.util.Base64

internal class EventsRouter internal constructor(
    private val teamStorage: TeamStorage,
    private val conversationStorage: ConversationStorage,
    private val backendClient: BackendClient,
    private val wireEventsHandler: WireEventsHandler,
    private val cryptoClient: CryptoClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Suppress("LongMethod")
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
                    wireEventsHandler.onConversationDelete(event.qualifiedConversation)
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
                    wireEventsHandler.onMemberJoin(event.qualifiedConversation, members)
                }

                is EventContentDTO.Conversation.MemberLeave -> {
                    logger.info("Leaving event from: ${event.qualifiedConversation}")
                    conversationStorage.deleteMembers(
                        conversationId = event.qualifiedConversation,
                        users = event.data.users
                    )
                    wireEventsHandler.onMemberLeave(event.qualifiedConversation, event.data.users)
                }

                is EventContentDTO.Conversation.MlsWelcome -> {
                    logger.info("Joining MLS conversation: ${event.qualifiedConversation}")
                    val welcome = Welcome(Base64.getDecoder().decode(event.data))
                    val groupId = fetchGroupIdFromWelcome(cryptoClient, welcome, event)

                    val conversation = backendClient.getConversation(event.qualifiedConversation)
                    val conversationData =
                        ConversationData(
                            id = event.qualifiedConversation,
                            name = conversation.name,
                            mlsGroupId = groupId,
                            teamId = conversation.teamId?.let { TeamId(it) }
                        )
                    val members = conversation.members.others.map {
                        ConversationMember(
                            userId = it.id,
                            role = it.conversationRole
                        )
                    }
                    // Saves the conversation in the local database, used later to decrypt messages
                    conversationStorage.save(conversationData)
                    conversationStorage.saveMembers(event.qualifiedConversation, members)

                    if (cryptoClient.hasTooFewKeyPackageCount()) {
                        backendClient.uploadMlsKeyPackages(
                            appClientId = cryptoClient.getAppClientId(),
                            mlsKeyPackages = cryptoClient.mlsGenerateKeyPackages().map { it.value }
                        )
                    }

                    wireEventsHandler.onConversationJoin(conversationData, members)
                }

                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    val groupId = fetchGroupIdFromConversation(event.qualifiedConversation)

                    val message = cryptoClient.decryptMls(
                        mlsGroupId = groupId,
                        encryptedMessage = event.message
                    )
                    if (message == null) {
                        logger.debug("Decryption success but no message, probably epoch update")
                        return
                    }

                    val genericMessage = GenericMessage.parseFrom(message)
                    val wireMessage = ProtobufDeserializer.processGenericMessage(
                        genericMessage = genericMessage,
                        conversationId = event.qualifiedConversation,
                        sender = event.qualifiedFrom
                    )

                    // TODO think about a better way to handle the same event in blocking/async
                    when (wireMessage) {
                        is WireMessage.Text -> wireEventsHandler.onNewMessageSuspending(
                            wireMessage = wireMessage
                        )

                        is WireMessage.Asset -> wireEventsHandler.onNewAssetSuspending(
                            wireMessage = wireMessage
                        )

                        is WireMessage.Composite -> wireEventsHandler.onNewCompositeSuspending(
                            wireMessage = wireMessage
                        )

                        is WireMessage.ButtonAction ->
                            wireEventsHandler.onNewButtonActionSuspending(
                                wireMessage = wireMessage
                            )

                        is WireMessage.ButtonActionConfirmation ->
                            wireEventsHandler.onNewButtonActionConfirmationSuspending(
                                wireMessage = wireMessage
                            )

                        WireMessage.Unknown -> {
                            logger.warn("Unknown event received.")
                        }
                    }
                }

                is EventContentDTO.Unknown -> {
                    logger.warn("Unknown event type: {}", event)
                }
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
                cryptoClient.joinMlsConversationRequest(GroupInfo(groupInfo))
            } else {
                logger.error("Cannot process welcome", ex)
                throw WireException.CryptographicSystemError("Cannot process welcome")
            }
        }
    }

    /**
     * Fetches the group ID of the conversation in the local database.
     */
    private fun fetchGroupIdFromConversation(conversationId: QualifiedId): MLSGroupId {
        logger.debug(
            "Searching for group ID of conversation: {}:{}",
            conversationId.id,
            conversationId.domain
        )

        val storedConversation = conversationStorage.getById(conversationId)
        return storedConversation?.mlsGroupId ?: throw WireException.EntityNotFound(
            "No local data for conversation $conversationId"
        )
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
