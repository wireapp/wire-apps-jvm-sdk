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

import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.client.BackendClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.EventContentDTO
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.model.http.conversation.Member
import com.wire.sdk.model.http.conversation.MemberJoinEventData
import com.wire.sdk.persistence.TeamStorage
import com.wire.sdk.service.conversation.ConversationService
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Tests for EventsRouter's concurrent event processing with channels.
 *
 * These tests verify that:
 * - Events for the same conversation are processed in order (FIFO)
 * - Events for different conversations can be processed in parallel
 * - Exception handling doesn't block other events
 * - Channel isolation prevents cross-conversation failures
 */
class EventsRouterConcurrencyTest {
    @Test
    fun `events for same conversation are processed in order`() =
        runTest {
            // Arrange
            val conversationId = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val addedUsers = mutableListOf<QualifiedId>()
            val processedUsers = Collections.synchronizedList(mutableListOf<QualifiedId>())
            val conversationService = mockk<ConversationService>()

            coEvery {
                conversationService.saveMembers(any(), any())
            } coAnswers {
                val members = secondArg<List<ConversationMember>>()
                val eventNumber = members.first().userId
                processedUsers.add(eventNumber)
                delay(10) // Simulate processing time
            }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val eventsRouter = createEventsRouter(
                conversationService = conversationService,
                dispatcher = testDispatcher
            )

            // Act - Send 10 events for the same conversation
            val events = (1..10).map { _ ->
                val userId = QualifiedId(UUID.randomUUID(), "wire.test")
                addedUsers.add(userId)
                createMemberJoinEvent(conversationId, userId)
            }

            eventsRouter.route(
                EventResponse(
                    id = UUID.randomUUID().toString(),
                    payload = events
                )
            )

            // Advance time to allow all events to be processed
            testScheduler.advanceUntilIdle()

            // Assert - Events should be processed in order
            assertEquals(addedUsers, processedUsers)

            eventsRouter.close()
        }

    @Test
    fun `events for different conversations are processed in parallel`() =
        runTest {
            // Arrange
            val conversationA = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val conversationB = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val processingStartTimes = Collections.synchronizedMap(mutableMapOf<String, Long>())
            val conversationService = mockk<ConversationService>()

            coEvery {
                conversationService.saveMembers(any(), any())
            } coAnswers {
                val convId = firstArg<QualifiedId>()
                processingStartTimes[convId.id.toString()] = System.currentTimeMillis()
                delay(50) // Simulate processing time (real delay)
            }

            // Use real dispatcher for parallel processing
            val eventsRouter = createEventsRouter(
                conversationService = conversationService,
                dispatcher = Dispatchers.Default
            )

            val userId1 = QualifiedId(UUID.randomUUID(), "wire.test")
            val userId2 = QualifiedId(UUID.randomUUID(), "wire.test")

            // Act - Send events for two different conversations
            val events = listOf(
                createMemberJoinEvent(conversationA, userId1),
                createMemberJoinEvent(conversationB, userId2)
            )

            eventsRouter.route(
                EventResponse(
                    id = UUID.randomUUID().toString(),
                    payload = events
                )
            )

            // Wait for processing to complete (use real thread sleep for real dispatcher)
            Thread.sleep(200)

            // Assert - Both should have started processing around the same time (parallel)
            assertEquals(2, processingStartTimes.size)
            val times = processingStartTimes.values.sorted()
            val timeDifference = times[1] - times[0]
            assertTrue(
                timeDifference < 40,
                "Events should start processing in parallel (diff: ${timeDifference}ms)"
            )

            eventsRouter.close()
        }

    @Test
    fun `events in different EventResponses are processed in parallel`() =
        runTest {
            // Arrange
            val conversationA = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val conversationB = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val processingStartTimes = Collections.synchronizedMap(mutableMapOf<String, Long>())
            val conversationService = mockk<ConversationService>()

            coEvery {
                conversationService.saveMembers(any(), any())
            } coAnswers {
                val convId = firstArg<QualifiedId>()
                processingStartTimes[convId.id.toString()] = System.currentTimeMillis()
                delay(50) // Simulate processing time (real delay)
            }

            // Use real dispatcher for parallel processing
            val eventsRouter = createEventsRouter(
                conversationService = conversationService,
                dispatcher = Dispatchers.Default
            )

            val userId1 = QualifiedId(UUID.randomUUID(), "wire.test")
            val userId2 = QualifiedId(UUID.randomUUID(), "wire.test")

            // Act - Send events for two different conversations, in different EventResponses
            val events1 = listOf(
                createMemberJoinEvent(conversationA, userId1),
                createMemberJoinEvent(conversationA, userId2)
            )
            val events2 = listOf(
                createMemberJoinEvent(conversationB, userId1),
                createMemberJoinEvent(conversationB, userId2)
            )

            eventsRouter.route(
                EventResponse(
                    id = UUID.randomUUID().toString(),
                    payload = events1
                )
            )
            eventsRouter.route(
                EventResponse(
                    id = UUID.randomUUID().toString(),
                    payload = events2
                )
            )

            // Wait for processing to complete (use real thread sleep for real dispatcher)
            Thread.sleep(200)

            // Assert - Both should have started processing around the same time (parallel)
            assertEquals(2, processingStartTimes.size)
            val times = processingStartTimes.values.sorted()
            val timeDifference = times[1] - times[0]
            assertTrue(
                timeDifference < 40,
                "Events should start processing in parallel (diff: ${timeDifference}ms)"
            )

            eventsRouter.close()
        }

    @Test
    fun `exception in one event does not block other events in same conversation`() =
        runTest {
            // Arrange
            val conversationId = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val processedUsers = Collections.synchronizedList(mutableListOf<QualifiedId>())
            val conversationService = mockk<ConversationService>()

            val userId1 = QualifiedId(UUID.randomUUID(), "wire.test")
            val userId2 = QualifiedId(UUID.randomUUID(), "wire.test")
            val userId3 = QualifiedId(UUID.randomUUID(), "wire.test")

            coEvery {
                conversationService.saveMembers(any(), any<List<ConversationMember>>())
            } coAnswers {
                val members = secondArg<List<ConversationMember>>()
                val eventUser = members.first().userId
                if (eventUser == userId2) {
                    throw RuntimeException("Simulated error for userId2")
                }
                processedUsers.add(eventUser)
            }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val eventsRouter = createEventsRouter(
                conversationService = conversationService,
                dispatcher = testDispatcher
            )

            // Act - Send 3 events, second one will fail
            val events = listOf(
                createMemberJoinEvent(conversationId, userId1),
                createMemberJoinEvent(conversationId, userId2),
                createMemberJoinEvent(conversationId, userId3)
            )

            eventsRouter.route(
                EventResponse(
                    id = UUID.randomUUID().toString(),
                    payload = events
                )
            )

            // Advance time to allow all events to be processed
            testScheduler.advanceUntilIdle()

            // Assert - Events 1 and 3 should be processed, 2 should have failed
            assertEquals(listOf(userId1, userId3), processedUsers)

            eventsRouter.close()
        }

    @Test
    fun `exception in one conversation channel does not affect other channels`() =
        runTest {
            // Arrange
            val conversationA = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val conversationB = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val userId1 = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val userId2 = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val userId3 = QualifiedId(id = UUID.randomUUID(), domain = "wire.test")
            val processedEvents = Collections.synchronizedMap(
                mutableMapOf<QualifiedId, MutableList<QualifiedId>>()
            )
            val conversationService = mockk<ConversationService>()

            coEvery {
                conversationService.saveMembers(any(), any())
            } coAnswers {
                val convId = firstArg<QualifiedId>()
                val members = secondArg<List<ConversationMember>>()
                val eventNumber = members.first().userId

                // Conversation A: event 2 throws exception
                if (convId == conversationA && eventNumber == userId2) {
                    throw RuntimeException("Simulated error in conversation A")
                }

                processedEvents.computeIfAbsent(convId) { mutableListOf() }
                    .add(eventNumber)
            }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val eventsRouter = createEventsRouter(
                conversationService = conversationService,
                dispatcher = testDispatcher
            )

            // Act - Send events for both conversations
            val events = listOf(
                createMemberJoinEvent(conversationA, userId1),
                createMemberJoinEvent(conversationB, userId1),
                createMemberJoinEvent(conversationA, userId2), // This will fail
                createMemberJoinEvent(conversationB, userId2),
                createMemberJoinEvent(conversationA, userId3),
                createMemberJoinEvent(conversationB, userId3)
            )

            eventsRouter.route(
                EventResponse(
                    id = UUID.randomUUID().toString(),
                    payload = events
                )
            )

            // Advance time to allow all events to be processed
            testScheduler.advanceUntilIdle()

            // Assert
            // Conversation A: events 1 and 3 processed (2 failed)
            assertEquals(listOf(userId1, userId3), processedEvents[conversationA] ?: emptyList())
            // Conversation B: all events processed
            assertEquals(
                listOf(userId1, userId2, userId3),
                processedEvents[conversationB] ?: emptyList()
            )

            eventsRouter.close()
        }

    @Test
    fun `non-conversation events are processed in dedicated channel`() =
        runTest {
            // Arrange
            val teamStorage = mockk<TeamStorage>()
            val backendClient = mockk<BackendClient>()
            val processedTeamInvites = Collections.synchronizedList(mutableListOf<UUID>())

            coEvery { backendClient.confirmTeam(any()) } just Runs
            coEvery { teamStorage.save(any()) } coAnswers {
                // This should be TeamId, but there is an issue with mockk
                //     and value classes, so we use UUID directly
                val teamId = firstArg<UUID>()
                processedTeamInvites.add(teamId)
            }

            val testDispatcher = StandardTestDispatcher(testScheduler)
            val eventsRouter = createEventsRouter(
                teamStorage = teamStorage,
                backendClient = backendClient,
                dispatcher = testDispatcher
            )

            // Act - Send team invite events
            val teamId1 = UUID.randomUUID()
            val teamId2 = UUID.randomUUID()
            val events = listOf(
                EventContentDTO.TeamInvite(teamId = teamId1),
                EventContentDTO.TeamInvite(teamId = teamId2)
            )

            eventsRouter.route(
                EventResponse(
                    id = UUID.randomUUID().toString(),
                    payload = events
                )
            )

            // Advance time to allow all events to be processed
            testScheduler.advanceUntilIdle()

            // Assert
            assertEquals(2, processedTeamInvites.size)
            assertTrue(processedTeamInvites.contains(teamId1))
            assertTrue(processedTeamInvites.contains(teamId2))

            eventsRouter.close()
        }

    private fun createMemberJoinEvent(
        conversationId: QualifiedId,
        userId: QualifiedId
    ) = EventContentDTO.Conversation.MemberJoin(
        qualifiedConversation = conversationId,
        qualifiedFrom = QualifiedId(UUID.randomUUID(), "wire.test"),
        time = Clock.System.now(),
        data = MemberJoinEventData(
            users = listOf(
                Member(
                    userId = userId,
                    conversationRole = ConversationRole.MEMBER
                )
            )
        )
    )

    private fun createEventsRouter(
        teamStorage: TeamStorage = mockk(relaxed = true),
        conversationService: ConversationService = mockk(relaxed = true),
        backendClient: BackendClient = mockk(relaxed = true),
        cryptoClient: CryptoClient = mockk(relaxed = true),
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): EventsRouter {
        val wireEventsHandler = object : WireEventsHandlerSuspending() {}
        val mlsFallbackStrategy = mockk<MlsFallbackStrategy>(relaxed = true)

        return EventsRouter(
            teamStorage = teamStorage,
            conversationService = conversationService,
            backendClient = backendClient,
            wireEventsHandler = wireEventsHandler,
            cryptoClient = cryptoClient,
            mlsFallbackStrategy = mlsFallbackStrategy,
            dispatcher = dispatcher
        )
    }
}
