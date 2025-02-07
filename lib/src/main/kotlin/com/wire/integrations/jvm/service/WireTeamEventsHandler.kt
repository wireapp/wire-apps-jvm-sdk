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
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import org.slf4j.LoggerFactory

internal class WireTeamEventsHandler internal constructor(
    private val wireEventsHandler: WireEventsHandler
) {
    private val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    fun handleEvents(
        team: Team,
        event: EventResponse,
        cryptoClient: CryptoClient
    ) {
        logger.debug("Team: {} | cryptoClient: {}", team.id, cryptoClient.getId())
        event.payload?.forEach { eventContentDTO ->
            when (eventContentDTO) {
                is EventContentDTO.Conversation.NewConversationDTO -> {
                    // do something extra?
                    // wireEventsHandler.onNewConversation(eventContentDTO)
                    wireEventsHandler.onNewConversation(eventContentDTO.time.toString())
                }
                is EventContentDTO.Conversation.NewMessageDTO -> {
                    // decrypt with received cryptoClient
                    // do something extra?
                    // wireEventsHandler.onNewMessage(eventContentDTO)
                    wireEventsHandler.onNewMessage(eventContentDTO.time.toString())
                }
                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    // decrypt with received cryptoClient
                    // do something extra?
                    // wireEventsHandler.onNewMLSMessage(eventContentDTO)
                    wireEventsHandler.onNewMLSMessage(eventContentDTO.time.toString())
                }
                else -> {
                    // do nothing and log received event as its unknown?
                }
            }
        }
    }
}
