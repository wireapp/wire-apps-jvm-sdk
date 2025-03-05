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

import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.utils.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Opens the webSocket receiving events from all registered teams.
 */
internal class WireTeamEventsListener internal constructor(
    private val httpClient: HttpClient,
    private val eventsRouter: EventsRouter
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun connect() {
        runBlocking {
            eventsRouter.openCurrentTeamClients()

            logger.info("Connecting to the webSocket, waiting team events and new team invites")
            // TODO Change endpoint to /events and add consumable-notifications client
            //  capability once v8 is released
            httpClient.webSocket(
                host = "localhost",
                port = 8086,
                path = "/await",
                request = {
                    header(HttpHeaders.Authorization, "Bearer ${IsolatedKoinContext.getApiToken()}")
                }
            ) {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Binary -> {
                            // Assuming byteArray is a UTF-8 character set
                            val jsonString = frame.data.decodeToString(0, frame.data.size)
                            logger.debug("Binary frame content: '$jsonString'")
                            val event =
                                KtxSerializer.json.decodeFromString<EventResponse>(jsonString)

                            eventsRouter.routeEvents(
                                event = event
                            )

                            // Send back ACK event
                        }

                        else -> {
                            logger.error("Received unsupported frame type: $frame")
                        }
                    }
                }
            }
        }
    }
}
