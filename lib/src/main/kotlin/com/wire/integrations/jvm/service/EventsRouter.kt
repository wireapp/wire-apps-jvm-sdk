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
        event: EventResponse,
        cryptoClient: CryptoClient
    ) {
        event.payload?.forEach { eventContentDTO ->
            when (eventContentDTO) {
                is EventContentDTO.TeamInvite -> {
                    logger.debug("Team invite: ${eventContentDTO.teamId}")
                    newTeamInvite(TeamId(eventContentDTO.teamId))
                }
                is EventContentDTO.Conversation.NewConversationDTO -> {
                    // Given that the conversation has been created by the App itself beforehand
                    // it is safe to assume that the conversation is already in the local database
                    wireEventsHandler.onNewConversation(eventContentDTO.time.toString())
                }

                is EventContentDTO.Conversation.MemberJoin -> {
                    val conversation =
                        backendClient.getConversation(eventContentDTO.qualifiedConversation)
                    conversationStorage.saveWithTeam(
                        conversationId = conversation.id,
                        teamId = TeamId(conversation.teamId)
                    )
                    wireEventsHandler.onMemberJoin(eventContentDTO.time.toString())
                }

                is EventContentDTO.Conversation.MlsWelcome -> {
                    val welcome = Welcome(Base64.getDecoder().decode(eventContentDTO.data))
                    val groupId = cryptoClient.processWelcomeMessage(welcome)

                    conversationStorage.saveWithMlsGroupId(
                        conversationId = eventContentDTO.qualifiedConversation,
                        mlsGroupId = groupId
                    )
                }
                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    val conversation =
                        conversationStorage.getById(eventContentDTO.qualifiedConversation)
                    if (conversation.mlsGroupId == null) {
                        logger.error(
                            "Missing group ID for conversation {},no mls-welcome received yet",
                            conversation.id
                        )
                        throw WireException.EntityNotFound()
                    }

                    val message = cryptoClient.decryptMls(
                        conversation.mlsGroupId,
                        eventContentDTO.message
                    )
                    // TODO Add mapping to Protobuf (com.waz.model)
                    wireEventsHandler.onNewMLSMessage(message.toString())
                }
                is EventContentDTO.Unknown -> {
                    logger.warn("Unknown event type: {}", eventContentDTO)
                }
            }
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
