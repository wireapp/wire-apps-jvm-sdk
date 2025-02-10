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

import com.wire.integrations.jvm.cryptography.CryptoClient
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.utils.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

// TODO Check if this class can be created via Koin factory, with httpClient and wireEventsHandler as parameters,
//  with the Team being passed by WireApplicationManager
internal class WireTeamEventsListener internal constructor(
    private val team: Team,
    private val httpClient: HttpClient,
    private val cryptoClient: CryptoClient,
    private val eventsRouter: EventsRouter
) {
    private val logger = LoggerFactory.getLogger(this::class.java.canonicalName)
    private lateinit var currentJob: Job

    @OptIn(DelicateCoroutinesApi::class)
    fun connect(): Job {
        currentJob =
            GlobalScope.launch(Dispatchers.IO) {
                httpClient.webSocket(
                    path = "/await?access_token=${team.accessToken}&client=${team.clientId}"
                ) {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Binary -> {
                                // assuming here the byteArray is an ASCII character set
                                val jsonString = frame.data.decodeToString(0, frame.data.size)
                                logger.debug("Binary frame content: '$jsonString'")
                                val event =
                                    KtxSerializer.json.decodeFromString<EventResponse>(jsonString)

                                // Deserialize the frame
                                // Handle different event.types, decrypt if necessary
                                // Delegate to wireTeamEventsHandler, then later
                                // to wireEventsHandler created by the Developer
                                eventsRouter.routeEvents(
                                    team = team,
                                    event = event,
                                    cryptoClient = cryptoClient
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
        return currentJob
    }

    fun disconnect() {
        cryptoClient.close()
        // Disconnect from the team's WebSocket
        currentJob.cancel()
    }
}
