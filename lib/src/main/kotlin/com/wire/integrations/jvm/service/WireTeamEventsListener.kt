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
import com.wire.integrations.jvm.model.EventAcknowledgeRequest
import com.wire.integrations.jvm.model.http.ConsumableNotificationResponse
import com.wire.integrations.jvm.utils.KtxSerializer
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.io.IOException
import org.slf4j.LoggerFactory

/**
 * Opens the webSocket receiving events from all registered teams.
 */
internal class WireTeamEventsListener internal constructor(
    private val backendClient: BackendClient,
    private val eventsRouter: EventsRouter
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Keeps the webSocket connection open and listens for incoming events, while handling errors
     */
    suspend fun connect() {
        try {
            backendClient.connectWebSocket { session ->
                for (frame in session.incoming) {
                    if (frame is Frame.Binary) {
                        handleEvent(frame, session)
                    } else {
                        logger.error("Received unsupported frame type: $frame")
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            logger.error("WebSocket timeout, log exception but close gracefully", e)
        } catch (e: ConnectTimeoutException) {
            logger.error("WebSocket connection timeout, log exception but close gracefully", e)
        } catch (e: IOException) {
            logger.error("WebSocket IO issue, log exception but close gracefully", e)
        } catch (e: Exception) {
            val error = e.message ?: "Error connecting to WebSocket or establishing MLS client"
            logger.error(error, e)
            throw InterruptedException(error)
        } finally {
            logger.warn("WebSocket connection closed, stopping Wire Team Events Listener")
        }
    }

    /**
     * Handles incoming binary frames, deserializes them into [ConsumableNotificationResponse],
     * and routes the events to the [EventsRouter]. Acknowledges the event by sending an
     * [EventAcknowledgeRequest] back to the server.
     */
    private suspend fun handleEvent(
        frame: Frame.Binary,
        session: DefaultClientWebSocketSession
    ) {
        // Assuming byteArray is a UTF-8 character set
        val jsonString = frame.data.decodeToString(0, frame.data.size)
        logger.debug("Binary frame content: '$jsonString'")
        val notification =
            KtxSerializer.json.decodeFromString<ConsumableNotificationResponse>(jsonString)
        when (notification) {
            is ConsumableNotificationResponse.EventNotification -> {
                try {
                    eventsRouter.route(notification.data.event)
                    val ackRequest = EventAcknowledgeRequest.basicAck(notification.data.deliveryTag)
                    ackEvent(ackRequest, session)
                } catch (e: Exception) {
                    logger.error("Error processing event: $notification", e)
                }
            }

            is ConsumableNotificationResponse.MissedNotification -> {
                logger.warn("App was offline for too long, missed some notifications")
                val ackRequest = EventAcknowledgeRequest.notificationMissedAck()
                ackEvent(ackRequest, session)
            }

            is ConsumableNotificationResponse.SynchronizationNotification -> {
                notification.data.deliveryTag?.let { deliveryTag ->
                    val ackRequest = EventAcknowledgeRequest.basicAck(deliveryTag)
                    ackEvent(ackRequest, session)
                }
                val currentSyncMarker = backendClient.getCurrentSyncMarker()
                if (notification.data.markerId == currentSyncMarker.toString()) {
                    logger.info("Notifications are up to date since last sync marker.")
                } else {
                    logger.debug(
                        "Skipping sync marker [${notification.data.markerId}], " +
                            "as it is not valid for this session."
                    )
                }
            }
        }
    }

    private fun ackEvent(
        ackRequest: EventAcknowledgeRequest,
        session: DefaultClientWebSocketSession
    ) {
        KtxSerializer.json.encodeToString(ackRequest).let { json ->
            val result = session.outgoing.trySend(Frame.Text(json))
            if (result.isSuccess) {
                logger.debug("Acknowledge event sent successfully $json")
            } else {
                logger.error("Failed to send acknowledge event $json", result.exceptionOrNull())
            }
        }
    }
}
