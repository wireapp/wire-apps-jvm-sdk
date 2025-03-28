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
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.Welcome
import com.wire.integrations.jvm.TestUtils
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.test.KoinTest
import org.koin.test.get
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WireEventsIntegrationTest : KoinTest {
    // Override the Koin instance as we use an isolated context
    override fun getKoin(): Koin = IsolatedKoinContext.koinApp.koin

    @Test
    fun givenKoinInjectionsWhenCallingHandleEventsThenTheCorrectMethodIsCalled() {
        runBlocking {
            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
            val eventsHandler = object : WireEventsHandler() {
                override fun onEvent(event: String) {
                    println(event)
                }
            }
            TestUtils.setupSdk(eventsHandler)

            val eventsRouter = get<EventsRouter>()
            val listener = get<WireTeamEventsListener>()
            val client = listener.getOrInitCryptoClient()
            eventsRouter.route(
                eventResponse = NEW_TEAM_INVITE_EVENT,
                cryptoClient = client
            )
            eventsRouter.route(
                eventResponse = NEW_CONVERSATION_EVENT,
                cryptoClient = client
            )

            val teamStorage = get<TeamStorage>()
            assertTrue { teamStorage.getAll().size == 1 }
        }
    }

    @Test
    fun givenNewMLSMessageEventWhenRouterProcessesItThenMessageIsDecryptedAndHandled() {
        // Setup
        TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)

        // Mock MLSMessage decryption result
        val mockDecryptedMessage = "Decrypted message content"
        val stubConvPath = "/v7/conversations/${CONVERSATION_ID.domain}/${CONVERSATION_ID.id}"
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlPathMatching(stubConvPath))
                .willReturn(
                    WireMock.okJson(
                        """
                        {
                            "qualified_id": {
                                "id": "${CONVERSATION_ID.id}",
                                "domain": "${CONVERSATION_ID.domain}"
                            },
                            "group_id": "${Base64.getEncoder().encodeToString(MLS_GROUP_ID.value)}",
                            "team": "${TEAM_ID.value}"
                        }
                        """.trimIndent()
                    )
                )
        )

        // Custom event handler to capture the event
        var capturedMessage: WireMessage? = null
        val customHandler = object : WireEventsHandler() {
            override fun onNewMLSMessage(wireMessage: WireMessage) {
                capturedMessage = wireMessage
            }
        }

        // Create SDK with our custom handler
        TestUtils.setupSdk(customHandler)

        // Execute
        val conversationStorage = get<ConversationStorage>()
        val conversationPrevious = conversationStorage.getById(CONVERSATION_ID)
        assertNull(conversationPrevious)

        val eventsRouter = get<EventsRouter>()
        runBlocking {
            val textMessage = GenericMessage
                .newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setText(
                    Messages.Text.newBuilder()
                        .setContent(mockDecryptedMessage)
                        .build()
                )
                .build()
            val stream = ByteArrayOutputStream()
            textMessage.writeTo(stream)
            val textMessageSerialized = stream.toByteArray()

            eventsRouter.route(
                eventResponse = NEW_MLS_MESSAGE_EVENT,
                cryptoClient = mockCryptoClient(String(textMessageSerialized))
            )
        }

        // Verify
        assertEquals(mockDecryptedMessage, (capturedMessage as WireMessage.Text).text)

        val conversation = conversationStorage.getById(CONVERSATION_ID)
        assertEquals(conversation?.id, CONVERSATION_ID)
        assertEquals(conversation?.teamId, TEAM_ID)
    }

    // Replace the real crypto client with a mock one for MLS decryption
    private fun mockCryptoClient(mockDecryptedMessage: String) =
        object : CryptoClient {
            override suspend fun decryptMls(
                mlsGroupId: MLSGroupId,
                encryptedMessage: String
            ): ByteArray = mockDecryptedMessage.toByteArray()

            // Throw OrphanWelcome, testing the fallback to createJoinMlsConversationRequest
            override suspend fun processWelcomeMessage(welcome: Welcome): MLSGroupId {
                throw MlsException.OrphanWelcome()
            }

            // Mock joining the conversation, assume the backend accepts the invitation
            override suspend fun joinMlsConversationRequest(groupInfo: GroupInfo): MLSGroupId =
                MLS_GROUP_ID

            override suspend fun encryptMls(
                mlsGroupId: MLSGroupId,
                plainMessage: ByteArray
            ): ByteArray {
                TODO("Not yet implemented")
            }

            override suspend fun mlsGetPublicKey(): MlsPublicKeys {
                TODO("Not yet implemented")
            }

            override suspend fun mlsGenerateKeyPackages(packageCount: UInt): List<MLSKeyPackage> {
                TODO("Not yet implemented")
            }

            override suspend fun mlsConversationExists(mlsGroupId: MLSGroupId): Boolean {
                TODO("Not yet implemented")
            }

            override suspend fun validKeyPackageCount(): Long {
                TODO("Not yet implemented")
            }

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
        }

    companion object {
        private val EXPECTED_NEW_CONVERSATION_VALUE = Instant.DISTANT_FUTURE
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
        private val MLS_GROUP_ID = MLSGroupId(ByteArray(32) { 1 })

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
                            time = EXPECTED_NEW_CONVERSATION_VALUE
                        )
                    ),
                transient = true
            )
        private val NEW_MLS_MESSAGE_EVENT =
            EventResponse(
                id = "event_id3",
                payload =
                    listOf(
                        EventContentDTO.Conversation.NewMLSMessageDTO(
                            qualifiedConversation = CONVERSATION_ID,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            message = "Encrypted message content",
                            subconversation = null
                        )
                    ),
                transient = true
            )
        private val wireMockServer = WireMockServer(8086)

        @JvmStatic
        @BeforeAll
        fun before() {
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
        }
    }
}
