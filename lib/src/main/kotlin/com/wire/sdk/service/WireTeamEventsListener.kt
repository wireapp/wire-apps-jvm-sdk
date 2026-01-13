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

package com.wire.sdk.service

import com.wire.sdk.BackendConnectionListener
import com.wire.sdk.client.BackendClient
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.utils.KtxSerializer
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.websocket.Frame
import java.util.Collections
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.io.IOException
import org.slf4j.LoggerFactory

/**
 * Opens the webSocket receiving events from all registered teams.
 */
internal class WireTeamEventsListener internal constructor(
    private val backendClient: BackendClient,
    private val eventsRouter: EventsRouter,
    private val appStorage: AppStorage
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var backendConnectionListener: BackendConnectionListener? = null
    private val processedEventIds = Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Keeps the webSocket connection open and listens for incoming events, while handling errors
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun connect() {
        try {
            backendClient.connectWebSocket { session ->
                backendConnectionListener?.onConnected()
                logger.info("WebSocket connection established")

                syncMissedNotifications()

                session.incoming
                    .consumeAsFlow()
                    .buffer()
                    .onCompletion { _ ->
                        logger.warn(
                            "WebSocket connection closed, stopping Wire Team Events Listener"
                        )
                        backendConnectionListener?.onDisconnected()
                    }
                    .collect { frame ->
                        if (frame is Frame.Binary) {
                            handleEvent(frame)
                        } else {
                            logger.error("Received unsupported frame type: $frame")
                        }
                    }
            }
        } catch (exception: Exception) {
            when (exception) {
                is SocketTimeoutException -> logger.error(
                    "WebSocket timeout, log exception but close gracefully",
                    exception
                )
                is ConnectTimeoutException -> logger.error(
                    "WebSocket connection timeout, log exception but close gracefully",
                    exception
                )
                is IOException -> logger.error(
                    "WebSocket IO issue, log exception but close gracefully",
                    exception
                )
                else -> {
                    val error = exception.message
                        ?: "Error connecting to WebSocket or establishing MLS client"
                    logger.error(error, exception)
                    backendConnectionListener?.onDisconnected()
                    throw InterruptedException(error)
                }
            }
        }
    }

    /**
     * Updates the backend connection listener that will receive connection state notifications.
     * This can be called after initialization to dynamically change the listener.
     *
     * @param listener The new listener to use, or null to remove the current listener
     */
    fun setBackendConnectionListener(listener: BackendConnectionListener?) {
        backendConnectionListener = listener
    }

    /**
     * Fetches and syncs missed notifications while the SDK was offline.
     */
    suspend fun syncMissedNotifications() {
        var lastNotificationId: String = getLastNotificationId()

        var hasMore = true
        while (hasMore) {
            val notifications = backendClient.getPaginatedNotifications(
                querySince = lastNotificationId
            )

            notifications.events.forEach { event ->
                runCatching {
                    eventsRouter.route(event)
                }.onSuccess {
                    processedEventIds.add(event.id)
                }.onFailure { error ->
                    logger.error("Failed to process event ${event.id}", error)
                }
            }

            if (notifications.events.isNotEmpty()) {
                lastNotificationId = notifications.events.last().id
                appStorage.setLastNotificationId(lastNotificationId)
            }
            hasMore = notifications.hasMore
        }
    }

    private suspend fun getLastNotificationId(): String =
        appStorage.getLastNotificationId() ?: run {
            val lastNotificationEvent = backendClient.getLastNotification()
            appStorage.setLastNotificationId(lastNotificationEvent.id)
            lastNotificationEvent.id
        }

    /**
     * Handles incoming binary frames, deserializes them into [EventResponse],
     * and routes the events to the [EventsRouter].
     */
    private suspend fun handleEvent(frame: Frame.Binary) {
        // Assuming byteArray is a UTF-8 character set
        val jsonString = frame.data.decodeToString(0, frame.data.size)
        logger.trace("Binary frame content: '$jsonString'")

        val event = KtxSerializer.json.decodeFromString<EventResponse>(jsonString)

        try {
            if (event.transient.not() && event.id !in processedEventIds) {
                processedEventIds.clear()
                eventsRouter.route(event)
                appStorage.setLastNotificationId(event.id)

                // TODO: Send back ACK event (To be done when we have Async notifications again)
            }
        } catch (e: Exception) {
            logger.error("Error processing event: $event", e)
        }
    }
}
