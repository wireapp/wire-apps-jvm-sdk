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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.Welcome
import com.wire.integrations.jvm.TestUtils
import com.wire.integrations.jvm.TestUtils.V
import com.wire.integrations.jvm.WireEventsHandlerSuspending
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.model.http.conversation.ConversationCreateData
import com.wire.integrations.jvm.model.http.conversation.ConversationCreateMembers
import com.wire.integrations.jvm.model.http.conversation.ConversationRole
import com.wire.integrations.jvm.model.http.conversation.Member
import com.wire.integrations.jvm.model.http.conversation.MemberJoinEventData
import com.wire.integrations.jvm.model.http.conversation.MemberLeaveEventData
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * This integrations test verifies the behavior of the WireEventsHandler and EventsRouter classes.
 * It mocks the CryptoClient and uses WireMock to send real http call to a temporary server.
 * All the other components are real and use the real Koin context.
 */
class WireEventsIntegrationTest {
    @Test
    fun givenKoinInjectionsWhenCallingHandleEventsThenTheCorrectMethodIsCalled() {
        runBlocking {
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

            val teamStorage = IsolatedKoinContext.koinApp.koin.get<TeamStorage>()
            assertTrue { teamStorage.getAll().size == 1 }
        }
    }

    @Test
    fun givenNewConversationsThenMembersAreCreatedAndDeleted() {
        // Setup
        TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)

        wireMockServer.stubFor(
            WireMock.get(
                WireMock.urlPathTemplate("/$V/conversations/{conversationDomain}/{conversationId}")
            ).willReturn(
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
                                            "domain": "${CONVERSATION_ID.domain}"
                                        },
                                        "conversation_role": "wire_admin"
                                    }
                                ]
                            },
                            "group_id": "${Base64.getEncoder().encodeToString(MLS_GROUP_ID.value)}",
                            "team": "${TEAM_ID.value}",
                            "type": 0
                        }
                    """.trimIndent()
                )
            )
        )

        // Create SDK with our custom handler
        TestUtils.setupSdk(wireEventsHandler)

        // Load Koin Modules
        IsolatedKoinContext.koin.loadModules(
            listOf(
                module {
                    single<CryptoClient> { mockCryptoClient() }
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

        runBlocking {
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
        }

        val conversation = conversationStorage.getById(conversationId)
        assertEquals(conversation?.id, conversationId)
        assertEquals(conversation?.teamId, TEAM_ID)
        var conversationMember = conversationStorage.getMembersByConversationId(conversationId)
        // First member received when fetching whole conversation after welcome
        assertEquals(1, conversationMember.size)

        val newUser1 = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString())
        val newUser2 = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString())
        val newUser3 = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString())

        runBlocking {
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
        }
        conversationMember = conversationStorage.getMembersByConversationId(conversationId)
        assertEquals(4, conversationMember.size)

        runBlocking {
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
        }
        conversationMember = conversationStorage.getMembersByConversationId(conversationId)
        assertEquals(2, conversationMember.size)
    }

    @Test
    fun givenNewMLSMessageEventWhenRouterProcessesItThenMessageIsDecryptedAndHandled() {
        // Setup
        TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
        wireMockServer.stubFor(
            WireMock.get(
                WireMock.urlPathTemplate("/$V/conversations/{conversationDomain}/{conversationId}")
            ).willReturn(
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
                                            "domain": "${CONVERSATION_ID.domain}"
                                        },
                                        "conversation_role": "wire_admin"
                                    }
                                ]
                            },
                            "group_id": "${Base64.getEncoder().encodeToString(MLS_GROUP_ID.value)}",
                            "team": "${TEAM_ID.value}",
                            "type": 0
                        }
                    """.trimIndent()
                )
            )
        )

        // Create SDK with our custom handler
        TestUtils.setupSdk(wireEventsHandler)

        // Load Koin Modules
        IsolatedKoinContext.koin.loadModules(
            listOf(
                module {
                    single<CryptoClient> { mockCryptoClient() }
                }
            )
        )

        // Execute
        val conversationStorage = IsolatedKoinContext.koinApp.koin.get<ConversationStorage>()
        val conversationPrevious = conversationStorage.getById(CONVERSATION_ID)
        assertNull(conversationPrevious)

        val eventsRouter = IsolatedKoinContext.koinApp.koin.get<EventsRouter>()

        runBlocking {
            eventsRouter.route(
                eventResponse = NEW_CONVERSATION_EVENT
            )
            eventsRouter.route(
                eventResponse = NEW_WELCOME_EVENT
            )

            val encryptedBase64Message = Base64
                .getEncoder()
                .encodeToString(
                    GENERIC_TEXT_MESSAGE.toByteArray()
                )

            eventsRouter.route(
                eventResponse = NEW_MLS_MESSAGE_EVENT.copy(
                    payload = listOf(
                        (
                            NEW_MLS_MESSAGE_EVENT.payload?.first() as EventContentDTO
                                .Conversation
                                .NewMLSMessageDTO
                        ).copy(
                            message = encryptedBase64Message.toString()
                        )
                    )
                )
            )
        }

        val conversation = conversationStorage.getById(CONVERSATION_ID)
        assertEquals(conversation?.id, CONVERSATION_ID)
        assertEquals(conversation?.teamId, TEAM_ID)

        val conversationMember = conversationStorage.getMembersByConversationId(CONVERSATION_ID)
        assertEquals(1, conversationMember.size)
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
        private val MLS_GROUP_ID = MLSGroupId(UUID.randomUUID().toString().toByteArray())

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
                            data = ConversationCreateData(
                                "DEMO",
                                ConversationCreateMembers(emptyList())
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
                            message = MOCK_DECRYPTED_MESSAGE,
                            subconversation = null
                        )
                    ),
                transient = true
            )
        private val GENERIC_TEXT_MESSAGE = GenericMessage
            .newBuilder()
            .setMessageId(UUID.randomUUID().toString())
            .setText(
                Messages.Text.newBuilder()
                    .setContent("Decrypted message content")
                    .build()
            )
            .build()

        private val wireMockServer = WireMockServer(8086)

        // Replace the real crypto client with a mock one for MLS decryption
        private fun mockCryptoClient() =
            object : CryptoClient {
                val conversationExist = mutableSetOf<MLSGroupId>()

                override suspend fun decryptMls(
                    mlsGroupId: MLSGroupId,
                    encryptedMessage: String
                ): ByteArray = GENERIC_TEXT_MESSAGE.toByteArray()

                // Throw OrphanWelcome, testing the fallback to createJoinMlsConversationRequest
                override suspend fun processWelcomeMessage(welcome: Welcome): MLSGroupId =
                    throw CoreCryptoException.Mls(MlsException.OrphanWelcome())

                // Mock joining the conversation, assume the backend accepts the invitation
                override suspend fun joinMlsConversationRequest(groupInfo: GroupInfo): MLSGroupId =
                    MLS_GROUP_ID

                override fun getAppClientId(): AppClientId {
                    TODO("Not yet implemented")
                }

                override suspend fun encryptMls(
                    mlsGroupId: MLSGroupId,
                    message: ByteArray
                ): ByteArray {
                    TODO("Not yet implemented")
                }

                override suspend fun mlsGetPublicKey(): MlsPublicKeys {
                    TODO("Not yet implemented")
                }

                override suspend fun mlsGenerateKeyPackages(
                    packageCount: UInt
                ): List<MLSKeyPackage> {
                    TODO("Not yet implemented")
                }

                override suspend fun hasTooFewKeyPackageCount(): Boolean = false

                override fun close() {
                    TODO("Not yet implemented")
                }

                override suspend fun createConversation(groupId: MLSGroupId) {
                    TODO("Not yet implemented")
                }

                override suspend fun addMemberToMlsConversation(
                    mlsGroupId: MLSGroupId,
                    keyPackages: List<MLSKeyPackage>
                ) {
                    TODO("Not yet implemented")
                }

                override suspend fun conversationExists(mlsGroupId: MLSGroupId): Boolean {
                    val wasConversationAdded = conversationExist.add(mlsGroupId)
                    return !wasConversationAdded
                }

                override suspend fun conversationEpoch(mlsGroupId: MLSGroupId): ULong = 0UL
            }

        private val wireEventsHandler =
            object : WireEventsHandlerSuspending() {
                override suspend fun onMessage(wireMessage: WireMessage.Text) {
                    // Verify
                    assertEquals(
                        MOCK_DECRYPTED_MESSAGE,
                        wireMessage.text
                    )
                }
            }

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
                            "group_id": "${Base64.getEncoder().encodeToString(MLS_GROUP_ID.value)}",
                            "team": "${TEAM_ID.value}"
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
