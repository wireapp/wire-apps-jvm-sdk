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
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import io.ktor.client.plugins.ResponseException
import org.slf4j.LoggerFactory
import java.util.Base64

internal class EventsRouter internal constructor(
    private val teamStorage: TeamStorage,
    private val conversationStorage: ConversationStorage,
    private val backendClient: BackendClient,
    private val wireEventsHandler: WireEventsHandler
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    internal fun route(
        eventResponse: EventResponse,
        cryptoClient: CryptoClient
    ) {
        eventResponse.payload?.forEach { event ->
            when (event) {
                is EventContentDTO.TeamInvite -> {
                    val teamId = TeamId(event.teamId)
                    logger.info("Team invite from: $teamId")
                    newTeamInvite(teamId)
                }
                is EventContentDTO.Conversation.NewConversationDTO -> {
                    // Given that the conversation has been created by the App itself beforehand
                    // it is safe to assume that the conversation is already in the local database
                    wireEventsHandler.onNewConversation(event.time.toString())
                }

                is EventContentDTO.Conversation.MemberJoin -> {
                    logger.info("Joining event from: ${event.qualifiedConversation}")
                    wireEventsHandler.onMemberJoin(event.time.toString())
                }

                is EventContentDTO.Conversation.MlsWelcome -> {
                    logger.info("MLS welcome from: ${event.qualifiedConversation}")
                    val welcome = Welcome(Base64.getDecoder().decode(event.data))
                    val groupId = fetchGroupIdFromWelcome(cryptoClient, welcome, event)

                    // Saves the groupId in the local database, used later to decrypt messages
                    conversationStorage.saveWithMlsGroupId(
                        conversationId = event.qualifiedConversation,
                        mlsGroupId = groupId
                    )
                }

                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    val groupId = fetchGroupIdFromConversation(event)

                    val message = cryptoClient.decryptMls(
                        mlsGroupId = groupId,
                        encryptedMessage = event.message
                    )
                    // TODO Add mapping to Protobuf (com.waz.model)
                    wireEventsHandler.onNewMLSMessage(String(message))
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
    private fun fetchGroupIdFromWelcome(
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
                cryptoClient.createJoinMlsConversationRequest(GroupInfo(groupInfo))
            } else {
                logger.error("Cannot process welcome", ex)
                throw WireException.CryptographicSystemError("Cannot process welcome")
            }
        }
    }

    /**
     * Fetches the group ID of the conversation in the local database.
     * If missing, tries to recover it by fetching the conversation from the Backend.
     */
    private fun fetchGroupIdFromConversation(
        event: EventContentDTO.Conversation.NewMLSMessageDTO
    ): MLSGroupId {
        val storedConversation =
            conversationStorage.getById(event.qualifiedConversation)
        return if (storedConversation?.mlsGroupId != null) {
            storedConversation.mlsGroupId
        } else {
            logger.error(
                "Missing group ID for conversation {}, no mls-welcome received yet",
                storedConversation?.id
            )
            val conversation =
                backendClient.getConversation(event.qualifiedConversation)
            val groupId = MLSGroupId(Base64.getDecoder().decode(conversation.groupId))

            conversationStorage.save(
                conversationId = conversation.id,
                // 1:1 conversations don't have a teamId
                teamId = conversation.teamId?.let { TeamId(it) },
                mlsGroupId = groupId
            )
            groupId
        }
    }

    private fun newTeamInvite(teamId: TeamId) {
        try {
            backendClient.confirmTeam(teamId)
            teamStorage.save(teamId) // Can be done async ?
        } catch (e: ResponseException) {
            logger.error("Error fetching events from the backend", e)
        } catch (e: WireException) {
            logger.error("Internal error", e)
        }
    }
}
