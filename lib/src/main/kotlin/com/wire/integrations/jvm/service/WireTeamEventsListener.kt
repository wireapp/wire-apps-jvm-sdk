/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CoreCryptoClient
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.utils.KtxSerializer
import io.ktor.websocket.Frame
import org.slf4j.LoggerFactory

/**
 * Opens the webSocket receiving events from all registered teams.
 *
 * Initializes the [CoreCryptoClient] used for any message on the first startup.
 */
internal class WireTeamEventsListener internal constructor(
    private val backendClient: BackendClient,
    private val eventsRouter: EventsRouter
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Keeps the connection open and listens for incoming events, provides deserialization and
     * basic error handling. Delegates event handling to [EventsRouter].
     */
    suspend fun connect() {
        try {
            // TODO Change endpoint to /events and add consumable-notifications client
            //  capability once v8 is released
            backendClient.connectWebSocket { incoming ->
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            // Assuming byteArray is a UTF-8 character set
                            val jsonString = frame.data.decodeToString(0, frame.data.size)
                            logger.debug("Binary frame content: '$jsonString'")
                            val event =
                                KtxSerializer.json.decodeFromString<EventResponse>(jsonString)

                            try {
                                eventsRouter.route(event)
                                // Send back ACK event
                            } catch (e: Exception) {
                                logger.error("Error processing event: $event", e)
                            }
                        }

                        else -> {
                            logger.error("Received unsupported frame type: $frame")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            val error = e.message ?: "Error connecting to WebSocket or establishing MLS client"
            logger.error(error, e)
            throw InterruptedException(error)
        }
    }
}
