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

import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.model.http.ConsumableNotificationResponse
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventDataDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.model.http.NotificationCount
import com.wire.integrations.jvm.utils.KtxSerializer
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.websocket.Frame
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

class WireTeamEventsListenerTest {
    @Test
    fun eventNotificationAreParsedAndRoutedAndAcknowledged() =
        runTest {
            // Arrange
            val backendClient = mockk<BackendClient>()
            val eventsRouter = mockk<EventsRouter>()
            val session = mockk<DefaultClientWebSocketSession>()
            val eventResponse = EventResponse(
                id = UUID.randomUUID().toString(),
                payload = listOf(EventContentDTO.TeamInvite(teamId = UUID.randomUUID()))
            )
            val incomingChannel = Channel<Frame>(Channel.UNLIMITED)
            val outgoingChannel = Channel<Frame>(Channel.UNLIMITED)

            val missedNotification = ConsumableNotificationResponse.MissedNotification
            val encodedMissing = encodeNotification(missedNotification)
            val messageCount = ConsumableNotificationResponse.MessageCount(
                data = NotificationCount(count = 5U)
            )
            val encodedCount = encodeNotification(messageCount)
            val eventNotification = ConsumableNotificationResponse.EventNotification(
                data = EventDataDTO(
                    event = eventResponse,
                    deliveryTag = 1U
                )
            )
            val encodedEvent = encodeNotification(eventNotification)

            every { session.incoming } returns incomingChannel
            every { session.outgoing } returns outgoingChannel
            coEvery { eventsRouter.route(any()) } returns Unit
            coEvery { backendClient.connectWebSocket(captureLambda()) } coAnswers {
                lambda<suspend (DefaultClientWebSocketSession) -> Unit>().captured.invoke(session)
            }

            val listener = WireTeamEventsListener(backendClient, eventsRouter)

            // Act
            launch { listener.connect() }

            incomingChannel.send(Frame.Binary(false, encodedMissing.encodeToByteArray()))
            incomingChannel.send(Frame.Binary(false, encodedCount.encodeToByteArray()))
            incomingChannel.send(Frame.Binary(false, encodedEvent.encodeToByteArray()))
            delay(100) // Allow time for processing

            // Assert, only the event is routed, but all three frames are acknowledged
            coVerify(exactly = 1) { eventsRouter.route(any()) }

            // Count frames in the outgoing channel
            val count = generateSequence {
                outgoingChannel.tryReceive().getOrNull()
            }.count()

            assertEquals(3, count)

            incomingChannel.close()
            outgoingChannel.close()
        }

    private fun encodeNotification(notification: ConsumableNotificationResponse): String {
        return KtxSerializer.json.encodeToString(notification)
    }

    @Test
    fun webSocketExceptionIsRethrown() =
        runTest {
            // Arrange
            val backendClient = mockk<BackendClient>()
            val eventsRouter = mockk<EventsRouter>()

            coEvery { backendClient.connectWebSocket(any()) } throws
                WebSocketException("Connection refused")

            val listener = WireTeamEventsListener(backendClient, eventsRouter)

            // Act & Assert
            assertThrows<InterruptedException> {
                listener.connect()
            }
        }

    @Test
    fun nonWebSocketExceptionIsHandledGracefully() =
        runTest {
            // Arrange
            val backendClient = mockk<BackendClient>()
            val eventsRouter = mockk<EventsRouter>()

            coEvery { backendClient.connectWebSocket(any()) } throws
                ConnectTimeoutException("Some other error")

            val listener = WireTeamEventsListener(backendClient, eventsRouter)

            // Act and Assert
            // Should not throw exception
            listener.connect()
        }
}
