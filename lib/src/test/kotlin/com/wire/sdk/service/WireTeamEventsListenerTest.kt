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

package com.wire.sdk.service

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.wire.sdk.BackendConnectionListener
import com.wire.sdk.client.BackendClient
import com.wire.sdk.client.BackendClientDemo
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.config.MAX_RETRY_NUMBER_ON_SERVER_ERROR
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.EventContentDTO
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.NotificationsResponse
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.utils.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSocketException
import io.ktor.websocket.Frame
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.time.Instant

class WireTeamEventsListenerTest {
    @Test
    fun syncedAndWebSocketEventsAreDeduplicatedBeforeRouting() =
        runTest {
            // Arrange
            val backendClient = mockk<BackendClient>()
            val eventsRouter = mockk<EventsRouter>()
            val appStorage = mockk<AppStorage>()
            val mockConnectionListener = mockk<BackendConnectionListener>()
            val session = mockk<DefaultClientWebSocketSession>()
            val lastNotificationId = UUID.randomUUID().toString()
            val webSocketEventResponse = EventResponse(
                id = UUID.randomUUID().toString(),
                payload = listOf(EventContentDTO.TeamInvite(teamId = UUID.randomUUID()))
            )

            val conversationId = QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
            val userId = QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
            val notificationEventResponse1 = EventResponse(
                id = UUID.randomUUID().toString(),
                payload = listOf(
                    EventContentDTO.Conversation.NewMLSMessageDTO(
                        qualifiedConversation = conversationId,
                        qualifiedFrom = userId,
                        time = Instant.DISTANT_PAST,
                        message = "random message 1",
                        subconversation = null
                    )
                ),
                transient = false
            )
            val notificationEventResponse2 = EventResponse(
                id = UUID.randomUUID().toString(),
                payload = listOf(
                    EventContentDTO.Conversation.NewMLSMessageDTO(
                        qualifiedConversation = conversationId,
                        qualifiedFrom = userId,
                        time = Instant.DISTANT_PAST,
                        message = "random message 1",
                        subconversation = null
                    )
                ),
                transient = false
            )

            val incomingChannel = Channel<Frame>(Channel.UNLIMITED)

            every { session.incoming } returns incomingChannel
            coEvery { mockConnectionListener.onConnected() } just Runs
            coEvery { mockConnectionListener.onDisconnected() } just Runs
            coEvery { eventsRouter.route(any()) } returns Unit
            coEvery {
                backendClient.getPaginatedNotifications(any(), any())
            } returns NotificationsResponse(
                hasMore = false,
                events = listOf(
                    notificationEventResponse1,
                    notificationEventResponse2,
                    webSocketEventResponse
                ),
                time = Instant.DISTANT_PAST
            )
            coEvery { backendClient.connectWebSocket(captureLambda()) } coAnswers {
                lambda<suspend (DefaultClientWebSocketSession) -> Unit>().captured.invoke(session)
            }
            coEvery { appStorage.getLastNotificationId() } returns lastNotificationId
            coEvery { appStorage.setLastNotificationId(any()) } returns Unit

            val listener = WireTeamEventsListener(backendClient, eventsRouter, appStorage)
            listener.setBackendConnectionListener(mockConnectionListener)

            // Act
            val connectJob = backgroundScope.launch { listener.connect() }

            incomingChannel.send(
                Frame.Binary(
                    false,
                    encodeEvent(webSocketEventResponse).encodeToByteArray()
                )
            )
            delay(100) // Allow time for processing

            // Assert the events are routed and there is no duplicate
            coVerify(exactly = 1) { eventsRouter.route(notificationEventResponse1) }
            coVerify(exactly = 1) { eventsRouter.route(notificationEventResponse2) }
            coVerify(exactly = 1) { eventsRouter.route(webSocketEventResponse) }

            // Close channel & job
            incomingChannel.close()
            connectJob.join()

            // Verify onDisconnected is correctly invoked after incomingChannel.onCompletion
            coVerify(atLeast = 1, atMost = 1) { mockConnectionListener.onDisconnected() }
        }

    @Test
    fun webSocketExceptionIsRethrown() =
        runTest {
            // Arrange
            val backendClient = mockk<BackendClient>()
            val eventsRouter = mockk<EventsRouter>()
            val appStorage = mockk<AppStorage>()

            coEvery { backendClient.connectWebSocket(any()) } throws
                WebSocketException("Connection refused")

            val listener = WireTeamEventsListener(backendClient, eventsRouter, appStorage)

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
            val appStorage = mockk<AppStorage>()

            coEvery { backendClient.connectWebSocket(any()) } throws
                ConnectTimeoutException("Some other error")

            val listener = WireTeamEventsListener(backendClient, eventsRouter, appStorage)

            // Act and Assert
            // Should not throw exception
            listener.connect()
        }

    @Test
    fun whenServerIsDownThenApplyExponentialBackoffStrategy() =
        runTest {
            // Arrange
            val wireMockServer = WireMockServer(8086)
            IsolatedKoinContext.start()
            IsolatedKoinContext.koinApp.koin.setProperty("API_HOST", "http://localhost:8086")
            wireMockServer.start()
            wireMockServer.stubFor(
                WireMock.post(WireMock.anyUrl())
                    .willReturn(
                        aResponse()
                            .withStatus(503)
                            .withHeader("Content-Type", "text/html")
                            .withBody("<html><body>Service Temporarily Unavailable</body></html>")
                    )
            )
            val httpClient = IsolatedKoinContext.koinApp.koin.get<HttpClient>()
            val appStorage = mockk<AppStorage>()
            val backendClient = BackendClientDemo(
                httpClient = httpClient,
                appStorage = appStorage
            )
            val eventsRouter = mockk<EventsRouter>()
            val mockConnectionListener = mockk<BackendConnectionListener>()
            coEvery { mockConnectionListener.onConnected() } just Runs
            coEvery { mockConnectionListener.onDisconnected() } just Runs
            val listener = WireTeamEventsListener(backendClient, eventsRouter, appStorage)
            listener.setBackendConnectionListener(mockConnectionListener)

            // Assert
            assertThrows<InterruptedException> {
                // Act
                listener.connect()
            }
            wireMockServer.verify(
                1 + MAX_RETRY_NUMBER_ON_SERVER_ERROR,
                postRequestedFor(WireMock.anyUrl())
            )

            coVerify(atLeast = 1, atMost = 1) { mockConnectionListener.onDisconnected() }

            // Clean up
            wireMockServer.stop()
            IsolatedKoinContext.stop()
        }

    private fun encodeEvent(event: EventResponse): String = KtxSerializer.json.encodeToString(event)
}
