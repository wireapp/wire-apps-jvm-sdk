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

import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.cryptography.CryptoClient
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import org.slf4j.LoggerFactory

internal class EventsRouter internal constructor(
    private val wireEventsHandler: WireEventsHandler
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun routeEvents(
        event: EventResponse,
        cryptoClient: CryptoClient
    ) {
        logger.debug("Team: {} | client: {}", cryptoClient.team.id, cryptoClient.team.clientId)
        event.payload?.forEach { eventContentDTO ->
            when (eventContentDTO) {
                is EventContentDTO.Conversation.NewConversationDTO -> {
                    // Check if there are enough Proteus keys or start MLS join procedure
                    wireEventsHandler.onNewConversation(eventContentDTO.time.toString())
                }
                is EventContentDTO.Conversation.NewMessageDTO -> {
                    // Decrypt Proteus with received cryptoClient
                    wireEventsHandler.onNewMessage(eventContentDTO.time.toString())
                }
                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    // Decrypt MLS with received cryptoClient
                    wireEventsHandler.onNewMLSMessage(eventContentDTO.time.toString())
                }
                is EventContentDTO.Unknown -> {
                    logger.warn("Unknown event type: {}", eventContentDTO)
                }
            }
        }
    }
}
