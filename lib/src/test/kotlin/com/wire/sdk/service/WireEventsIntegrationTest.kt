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
import com.wire.sdk.TestUtils
import com.wire.sdk.TestUtils.V
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.Conversation
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.http.EventContentDTO
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.conversation.ConversationMembers
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.model.http.conversation.Member
import com.wire.sdk.model.http.conversation.MemberJoinEventData
import com.wire.sdk.model.http.conversation.MemberLeaveEventData
import com.wire.sdk.persistence.ConversationStorage
import com.wire.sdk.persistence.TeamStorage
import com.wire.sdk.utils.MockCoreCryptoClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * This integrations test verifies the behavior of the WireEventsHandler and EventsRouter classes.
 * It mocks the CryptoClient and uses WireMock to send real http call to a temporary server.
 * All the other components are real and use the real Koin context.
 */
class WireEventsIntegrationTest {
    @Test
    fun givenKoinInjectionsWhenCallingHandleEventsThenTheCorrectMethodIsCalled() =
        runTest {
            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
            val eventsHandler = object : WireEventsHandlerSuspending() {}
            TestUtils.setupSdk(eventsHandler)

            val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()
            eventsRouter.route(
                eventResponse = NEW_TEAM_INVITE_EVENT
            )
            eventsRouter.route(
                eventResponse = NEW_CONVERSATION_EVENT
            )

            // Give async processing time to complete (no handler callback for these events)
            delay(500)

            val teamStorage = IsolatedKoinContext.koinApp.koin.get<TeamStorage>()
            assertTrue { teamStorage.getAll().size == 1 }
        }

    @Test
    fun givenNewConversationsThenMembersAreCreatedAndDeleted() =
        runTest {
            // Setup
            val welcomeLatch = CountDownLatch(1)
            val joinLatch = CountDownLatch(1)
            val leaveLatch = CountDownLatch(1)

            val customHandler = object : WireEventsHandlerSuspending() {
                override suspend fun onAppAddedToConversation(
                    conversation: Conversation,
                    members: List<ConversationMember>
                ) {
                    welcomeLatch.countDown()
                }

                override suspend fun onUserJoinedConversation(
                    conversationId: QualifiedId,
                    members: List<ConversationMember>
                ) {
                    joinLatch.countDown()
                }

                override suspend fun onUserLeftConversation(
                    conversationId: QualifiedId,
                    members: List<QualifiedId>
                ) {
                    leaveLatch.countDown()
                }
            }

            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)

            wireMockServer.stubFor(
                WireMock.get(
                    WireMock.urlPathTemplate(
                        "/$V/conversations/{conversationDomain}/{conversationId}"
                    )
                ).willReturn(
                    WireMock.okJson(NEW_CONVERSATION_RESPONSE)
                )
            )

            // Create SDK with our custom handler
            TestUtils.setupSdk(customHandler)

            // Load Koin Modules
            val mockCoreCryptoClient = MockCoreCryptoClient.create(
                userId = UUID.randomUUID().toString(),
                ciphersuiteCode = 1
            )
            IsolatedKoinContext.koin.loadModules(
                listOf(
                    module {
                        single<CryptoClient> { mockCoreCryptoClient }
                    }
                )
            )

            val conversationId =
                QualifiedId(
                    id = UUID.randomUUID(),
                    domain = "wire.com"
                )

            // Execute
            val conversationStorage = IsolatedKoinContext.koinApp.koin.get<ConversationStorage>()
            val conversationPrevious = conversationStorage.getById(conversationId)
            assertNull(conversationPrevious)

            val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()

            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_id10",
                    payload =
                        listOf(
                            EventContentDTO.Conversation.MlsWelcome(
                                qualifiedConversation = conversationId,
                                qualifiedFrom = USER_ID,
                                time = EXPECTED_NEW_CONVERSATION_VALUE,
                                data = "xyz"
                            )
                        ),
                    transient = true
                )
            )

            assertTrue(
                welcomeLatch.await(1000, TimeUnit.MILLISECONDS),
                "Welcome event should be processed"
            )

            var conversation = conversationStorage.getById(conversationId)
            assertEquals(conversation?.id, conversationId)
            assertEquals(conversation?.teamId, TEAM_ID)
            var conversationMember = conversationStorage.getMembersByConversationId(conversationId)
            // First member received when fetching whole conversation after welcome
            assertEquals(2, conversationMember.size)

            val newUser1 = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString())
            val newUser2 = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString())
            val newUser3 = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString())

            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_id11",
                    payload =
                        listOf(
                            EventContentDTO.Conversation.MemberJoin(
                                qualifiedConversation = conversationId,
                                qualifiedFrom = USER_ID,
                                time = EXPECTED_NEW_CONVERSATION_VALUE,
                                data = MemberJoinEventData(
                                    users = listOf(
                                        Member(newUser1, ConversationRole.ADMIN),
                                        Member(newUser2, ConversationRole.MEMBER),
                                        Member(newUser3, ConversationRole.MEMBER)
                                    )
                                )
                            )
                        ),
                    transient = true
                )
            )

            assertTrue(
                joinLatch.await(1000, TimeUnit.MILLISECONDS),
                "Join event should be processed"
            )

            conversationMember = conversationStorage.getMembersByConversationId(conversationId)
            assertEquals(5, conversationMember.size)

            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_id12",
                    payload =
                        listOf(
                            EventContentDTO.Conversation.MemberLeave(
                                qualifiedConversation = conversationId,
                                qualifiedFrom = USER_ID,
                                time = EXPECTED_NEW_CONVERSATION_VALUE,
                                data = MemberLeaveEventData(
                                    users = listOf(newUser1, newUser2),
                                    reason = "deletion"
                                )
                            )
                        ),
                    transient = true
                )
            )

            assertTrue(
                leaveLatch.await(1000, TimeUnit.MILLISECONDS),
                "Leave event should be processed"
            )

            conversationMember = conversationStorage.getMembersByConversationId(conversationId)
            assertEquals(3, conversationMember.size)

            // If the user leaving is the App, remove all the members and the whole conversation
            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_id13",
                    payload =
                        listOf(
                            EventContentDTO.Conversation.MemberLeave(
                                qualifiedConversation = conversationId,
                                qualifiedFrom = USER_ID,
                                time = EXPECTED_NEW_CONVERSATION_VALUE,
                                data = MemberLeaveEventData(
                                    users = listOf(
                                        QualifiedId(
                                            id = TestUtils.APPLICATION_ID,
                                            domain = "staging.zinfra.io"
                                        )
                                    ),
                                    reason = "deletion"
                                )
                            )
                        ),
                    transient = true
                )
            )
            conversationMember = conversationStorage.getMembersByConversationId(conversationId)
            assertEquals(0, conversationMember.size)
            conversation = conversationStorage.getById(conversationId)
            assertNull(conversation)
        }

    @Test
    fun givenNewMLSMessageEventWhenRouterProcessesItThenMessageIsDecryptedAndHandled() =
        runTest {
            // Setup
            val welcomeLatch = CountDownLatch(1)
            val messageLatch = CountDownLatch(1)

            val customHandler = object : WireEventsHandlerSuspending() {
                override suspend fun onAppAddedToConversation(
                    conversation: Conversation,
                    members: List<ConversationMember>
                ) {
                    welcomeLatch.countDown()
                }

                override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
                    // Verify
                    assertEquals(
                        MOCK_DECRYPTED_MESSAGE,
                        wireMessage.text
                    )
                    messageLatch.countDown()
                }
            }

            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
            wireMockServer.stubFor(
                WireMock.get(
                    WireMock.urlPathTemplate(
                        "/$V/conversations/{conversationDomain}/{conversationId}"
                    )
                ).willReturn(
                    WireMock.okJson(CONVERSATION_RESPONSE)
                )
            )

            // Create SDK with our custom handler
            TestUtils.setupSdk(customHandler)

            // Load Koin Modules
            val mockCoreCryptoClient = MockCoreCryptoClient.create(
                userId = UUID.randomUUID().toString(),
                ciphersuiteCode = 1
            )
            IsolatedKoinContext.koin.loadModules(
                listOf(
                    module {
                        single<CryptoClient> { mockCoreCryptoClient }
                    }
                )
            )

            // Execute
            val conversationStorage = IsolatedKoinContext.koinApp.koin.get<ConversationStorage>()
            val conversationPrevious = conversationStorage.getById(CONVERSATION_ID)
            assertNull(conversationPrevious)

            val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()

            eventsRouter.route(
                eventResponse = NEW_CONVERSATION_EVENT
            )
            eventsRouter.route(
                eventResponse = NEW_WELCOME_EVENT
            )

            assertTrue(
                welcomeLatch.await(1000, TimeUnit.MILLISECONDS),
                "Welcome event should be processed"
            )

            val encryptedBase64Message = Base64
                .getEncoder()
                .encodeToString(
                    MockCoreCryptoClient.GENERIC_TEXT_MESSAGE.toByteArray()
                )

            eventsRouter.route(
                eventResponse = NEW_MLS_MESSAGE_EVENT.copy(
                    payload = listOf(
                        (
                            NEW_MLS_MESSAGE_EVENT.payload?.first() as EventContentDTO
                                .Conversation
                                .NewMLSMessageDTO
                        ).copy(
                            data = encryptedBase64Message.toString()
                        )
                    )
                )
            )

            assertTrue(
                messageLatch.await(1000, TimeUnit.MILLISECONDS),
                "Message event should be processed"
            )

            val conversation = conversationStorage.getById(CONVERSATION_ID)
            assertEquals(conversation?.id, CONVERSATION_ID)
            assertEquals(conversation?.teamId, TEAM_ID)

            val conversationMember = conversationStorage.getMembersByConversationId(CONVERSATION_ID)
            assertEquals(3, conversationMember.size)
        }

    @Test
    fun givenAppAddedToSelfConversationThenExceptionIsThrown() =
        runTest {
            // Setup
            val handlerCalledLatch = CountDownLatch(1)
            var handlerWasCalled = false

            val customHandler = object : WireEventsHandlerSuspending() {
                override suspend fun onAppAddedToConversation(
                    conversation: Conversation,
                    members: List<ConversationMember>
                ) {
                    handlerWasCalled = true
                    handlerCalledLatch.countDown()
                }
            }

            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)

            wireMockServer.stubFor(
                WireMock.get(
                    WireMock.urlPathTemplate(
                        "/$V/conversations/{conversationDomain}/{conversationId}"
                    )
                ).willReturn(
                    WireMock.okJson(SELF_CONVERSATION_RESPONSE)
                )
            )

            // Create SDK with our custom handler
            TestUtils.setupSdk(customHandler)

            // Load Koin Modules
            val mockCoreCryptoClient = MockCoreCryptoClient.create(
                userId = UUID.randomUUID().toString(),
                ciphersuiteCode = 1
            )
            IsolatedKoinContext.koin.loadModules(
                listOf(
                    module {
                        single<CryptoClient> { mockCoreCryptoClient }
                    }
                )
            )

            // Execute
            val conversationStorage = IsolatedKoinContext.koinApp.koin.get<ConversationStorage>()
            val conversationPrevious = conversationStorage.getById(CONVERSATION_ID)
            assertNull(conversationPrevious)

            val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()

            eventsRouter.route(
                eventResponse = NEW_CONVERSATION_EVENT
            )

            // Route the welcome event - this should throw an exception during processing
            eventsRouter.route(
                eventResponse = NEW_WELCOME_EVENT
            )

            // Wait for processing to complete (or timeout)
            // The handler should NOT be called due to the exception
            val handlerCompleted = handlerCalledLatch.await(1000, TimeUnit.MILLISECONDS)

            // Verify the handler was not called (because an exception should have been thrown)
            assertTrue(
                !handlerCompleted || !handlerWasCalled,
                "Handler should not be called for self conversation"
            )
        }

    @Test
    fun givenLongRunningHandlerWhenProcessingMultipleEventsThenSubsequentEventsAreNotBlocked() =
        runTest {
            // Setup - track order of handler execution
            val handlerEvents = CopyOnWriteArrayList<String>()
            val eventsLatch = CountDownLatch(2)
            val joinStartedLatch = CountDownLatch(1)

            val slowHandler = object : WireEventsHandlerSuspending() {
                override suspend fun onUserJoinedConversation(
                    conversationId: QualifiedId,
                    members: List<ConversationMember>
                ) {
                    handlerEvents.add("join_started")
                    joinStartedLatch.countDown()
                    // Simulate a very long operation (e.g., external API call)
                    delay(2000)
                    handlerEvents.add("join_completed")
                    eventsLatch.countDown()
                }

                override suspend fun onUserLeftConversation(
                    conversationId: QualifiedId,
                    members: List<QualifiedId>
                ) {
                    handlerEvents.add("leave_started")
                    handlerEvents.add("leave_completed")
                    eventsLatch.countDown()
                }
            }

            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
            TestUtils.setupSdk(slowHandler)

            val conversationId = QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )

            val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()

            val user1 = QualifiedId(UUID.randomUUID(), "wire.com")
            val user2 = QualifiedId(UUID.randomUUID(), "wire.com")

            // Send MemberJoin event (which triggers the slow handler)
            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_join",
                    payload = listOf(
                        EventContentDTO.Conversation.MemberJoin(
                            qualifiedConversation = conversationId,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = MemberJoinEventData(
                                users = listOf(Member(user1, ConversationRole.MEMBER))
                            )
                        )
                    ),
                    transient = true
                )
            )

            // Wait for the join handler to actually start executing before sending leave event
            assertTrue(
                joinStartedLatch.await(1000, TimeUnit.MILLISECONDS),
                "Join handler should start"
            )

            // Now send MemberLeave event - this should NOT be blocked by the slow join handler
            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_leave",
                    payload = listOf(
                        EventContentDTO.Conversation.MemberLeave(
                            qualifiedConversation = conversationId,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = MemberLeaveEventData(
                                users = listOf(user2),
                                reason = "deletion"
                            )
                        )
                    ),
                    transient = true
                )
            )

            eventsLatch.await() // Wait for both events to be processed

            // Verify the order: leave should start after join but complete before it
            assertEquals(handlerEvents[0], "join_started")
            assertEquals(handlerEvents[1], "leave_started")
            assertEquals(handlerEvents[2], "leave_completed")
            assertEquals(handlerEvents[3], "join_completed")
        }

    @Test
    fun givenMultipleLongRunningHandlersWhenProcessingEventsThenAllRunConcurrently() =
        runTest {
            val allHandlersStarted = CountDownLatch(3)
            val allHandlersCompleted = CountDownLatch(3)

            val concurrentHandler = object : WireEventsHandlerSuspending() {
                override suspend fun onUserJoinedConversation(
                    conversationId: QualifiedId,
                    members: List<ConversationMember>
                ) {
                    allHandlersStarted.countDown()
                    delay(500) // All handlers take time
                    allHandlersCompleted.countDown()
                }
            }

            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
            TestUtils.setupSdk(concurrentHandler)

            val conversationId = QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )

            val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()

            // Send 3 MemberJoin events in quick succession
            repeat(3) { index ->
                val user = QualifiedId(UUID.randomUUID(), "wire.com")
                eventsRouter.route(
                    eventResponse = EventResponse(
                        id = "event_join_$index",
                        payload = listOf(
                            EventContentDTO.Conversation.MemberJoin(
                                qualifiedConversation = conversationId,
                                qualifiedFrom = USER_ID,
                                time = EXPECTED_NEW_CONVERSATION_VALUE,
                                data = MemberJoinEventData(
                                    users = listOf(Member(user, ConversationRole.MEMBER))
                                )
                            )
                        ),
                        transient = true
                    )
                )
            }

            // Wait for all handlers to start
            assertTrue(
                allHandlersStarted.await(500, TimeUnit.MILLISECONDS),
                "All handlers should start quickly"
            )

            // Wait for completion
            assertTrue(
                allHandlersCompleted.await(1000, TimeUnit.MILLISECONDS),
                "All handlers should complete"
            )
        }

    @Test
    fun givenHandlerThrowsExceptionWhenProcessingEventsThenOtherHandlersStillComplete() =
        runTest {
            val successfulHandlerCompleted = CountDownLatch(1)
            val throwingHandlerStarted = CountDownLatch(1)

            val handlerWithException = object : WireEventsHandlerSuspending() {
                override suspend fun onUserJoinedConversation(
                    conversationId: QualifiedId,
                    members: List<ConversationMember>
                ) {
                    throwingHandlerStarted.countDown()
                    // Small delay to ensure this coroutine is running when the other starts
                    delay(100)
                    throw RuntimeException("Simulated handler failure")
                }

                override suspend fun onUserLeftConversation(
                    conversationId: QualifiedId,
                    members: List<QualifiedId>
                ) {
                    // Wait for the throwing handler to start first
                    throwingHandlerStarted.await(500, TimeUnit.MILLISECONDS)
                    // This handler should still complete despite the other one throwing
                    successfulHandlerCompleted.countDown()
                }
            }

            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
            TestUtils.setupSdk(handlerWithException)

            val conversationId = QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )

            val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()

            val user1 = QualifiedId(UUID.randomUUID(), "wire.com")
            val user2 = QualifiedId(UUID.randomUUID(), "wire.com")

            // Send MemberJoin event (which will throw an exception in handler)
            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_join_throw",
                    payload = listOf(
                        EventContentDTO.Conversation.MemberJoin(
                            qualifiedConversation = conversationId,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = MemberJoinEventData(
                                users = listOf(Member(user1, ConversationRole.MEMBER))
                            )
                        )
                    ),
                    transient = true
                )
            )

            // Send MemberLeave event - this should NOT be affected by the exception
            eventsRouter.route(
                eventResponse = EventResponse(
                    id = "event_leave_success",
                    payload = listOf(
                        EventContentDTO.Conversation.MemberLeave(
                            qualifiedConversation = conversationId,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = MemberLeaveEventData(
                                users = listOf(user2),
                                reason = "deletion"
                            )
                        )
                    ),
                    transient = true
                )
            )

            // Verify the successful handler completed despite the other one throwing
            assertTrue(
                successfulHandlerCompleted.await(1000, TimeUnit.MILLISECONDS),
                "Handler should complete even when sibling coroutine throws"
            )
        }

    companion object {
        private val EXPECTED_NEW_CONVERSATION_VALUE = Instant.DISTANT_FUTURE
        private const val MOCK_DECRYPTED_MESSAGE = "Decrypted message content"
        private val CONVERSATION_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
        private val USER_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
        private val TEAM_ID = TeamId(UUID.randomUUID())

        private val NEW_TEAM_INVITE_EVENT =
            EventResponse(
                id = "event_id1",
                payload =
                    listOf(
                        EventContentDTO.TeamInvite(
                            teamId = TEAM_ID.value
                        )
                    ),
                transient = true
            )
        private val NEW_CONVERSATION_EVENT =
            EventResponse(
                id = "event_id2",
                payload =
                    listOf(
                        EventContentDTO.Conversation.NewConversationDTO(
                            qualifiedConversation = CONVERSATION_ID,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = ConversationResponse(
                                id = CONVERSATION_ID,
                                name = "Test Conversation",
                                epoch = 0,
                                members = ConversationMembers(
                                    others = emptyList(),
                                    self = TestUtils.dummyConversationMemberSelf(
                                        ConversationRole.MEMBER
                                    )
                                ),
                                groupId = MockCoreCryptoClient.MLS_GROUP_ID_BASE64,
                                teamId = TEAM_ID.value,
                                type = ConversationResponse.Type.GROUP,
                                protocol = CryptoProtocol.MLS
                            )
                        )
                    ),
                transient = true
            )
        private val NEW_WELCOME_EVENT =
            EventResponse(
                id = "event_id3",
                payload =
                    listOf(
                        EventContentDTO.Conversation.MlsWelcome(
                            qualifiedConversation = CONVERSATION_ID,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = "xyz"
                        )
                    ),
                transient = true
            )
        private val NEW_MLS_MESSAGE_EVENT =
            EventResponse(
                id = "event_id4",
                payload =
                    listOf(
                        EventContentDTO.Conversation.NewMLSMessageDTO(
                            qualifiedConversation = CONVERSATION_ID,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = MOCK_DECRYPTED_MESSAGE,
                            subconversation = null
                        )
                    ),
                transient = true
            )

        private val CONVERSATION_MEMBER_SELF_JSON =
            """
            {
              "conversation_role": "wire_member",
              "qualified_id": {
                "domain": "example.com",
                "id": "11111118-04e3-4b5d-9268-83111111c4ab"
              }
            }
            """.trimIndent()

        private val CONVERSATION_RESPONSE =
            """
                {
                    "qualified_id": {
                        "id": "${CONVERSATION_ID.id}",
                        "domain": "${CONVERSATION_ID.domain}"
                    },
                    "name": "Test conversation",
                    "epoch": 0,
                    "members": {
                        "others": [
                            {
                                "qualified_id": {
                                    "id": "${UUID.randomUUID()}",
                                    "domain": "${CONVERSATION_ID.domain}"
                                },
                                "conversation_role": "wire_admin"
                            }
                        ],
                        "self": $CONVERSATION_MEMBER_SELF_JSON
                    },
                    "group_id": "${MockCoreCryptoClient.MLS_GROUP_ID_BASE64}",
                    "team": "${TEAM_ID.value}",
                    "type": 0,
                    "protocol": "mls"
                }
            """.trimIndent()
        private val SELF_CONVERSATION_RESPONSE =
            """
                {
                    "qualified_id": {
                        "id": "${CONVERSATION_ID.id}",
                        "domain": "${CONVERSATION_ID.domain}"
                    },
                    "name": "Test conversation",
                    "epoch": 0,
                    "members": {
                        "others": [
                            {
                                "qualified_id": {
                                    "id": "${UUID.randomUUID()}",
                                    "domain": "${CONVERSATION_ID.domain}"
                                },
                                "conversation_role": "wire_admin"
                            }
                        ],
                        "self": $CONVERSATION_MEMBER_SELF_JSON
                    },
                    "group_id": "${MockCoreCryptoClient.MLS_GROUP_ID_BASE64}",
                    "team": "${TEAM_ID.value}",
                    "type": 1,
                    "protocol": "mls"
                }
            """.trimIndent()
        private val NEW_CONVERSATION_RESPONSE =
            """
                {
                    "qualified_id": {
                        "id": "${CONVERSATION_ID.id}",
                        "domain": "${CONVERSATION_ID.domain}"
                    },
                    "name": "Test conversation",
                    "epoch": 0,
                    "members": {
                        "others": [
                            {
                                "qualified_id": {
                                    "id": "${UUID.randomUUID()}",
                                    "domain": "${CONVERSATION_ID.domain}"
                                },
                                "conversation_role": "wire_admin"
                            }
                        ],
                        "self": $CONVERSATION_MEMBER_SELF_JSON
                    },
                    "group_id": "${MockCoreCryptoClient.MLS_GROUP_ID_BASE64}",
                    "team": "${TEAM_ID.value}",
                    "type": 0,
                    "protocol": "mls"
                }
            """.trimIndent()

        private val wireMockServer = WireMockServer(8086)

        @JvmStatic
        @BeforeAll
        fun before() {
            IsolatedKoinContext.start()

            wireMockServer.start()

            // Mock conversation fetching
            val stubConvPath = "/$V/conversations/{CONVERSATION_DOMAIN}/{CONVERSATION_ID}"
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathTemplate(stubConvPath)).willReturn(
                    WireMock.okJson(
                        """
                        {
                            "qualified_id": {
                                "id": "${CONVERSATION_ID.id}",
                                "domain": "${CONVERSATION_ID.domain}"
                            },
                            "name": "Test conversation",
                            "epoch": 0,
                            "members": {
                                "others": [
                                    {
                                        "qualified_id": {
                                            "id": "${UUID.randomUUID()}",
                                            "domain": "${USER_ID.domain}"
                                        },
                                        "conversation_role": "wire_admin"
                                    }
                                ]
                            },
                            "group_id": "${MockCoreCryptoClient.MLS_GROUP_ID_BASE64}",
                            "team": "${TEAM_ID.value}",
                            "protocol": "mls"
                        }
                        """.trimIndent()
                    )
                )
            )
            val stubConvGroupInfoPath =
                "/$V/conversations/{CONVERSATION_DOMAIN}/{CONVERSATION_ID}/groupinfo"
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathTemplate(stubConvGroupInfoPath))
                    .willReturn(
                        WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "message/mls")
                            .withBody(ByteArray(128) { 1 })
                    )
            )
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
            IsolatedKoinContext.stop()
        }
    }
}
