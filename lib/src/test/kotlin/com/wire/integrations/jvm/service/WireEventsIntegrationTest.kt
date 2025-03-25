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
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.Welcome
import com.wire.integrations.jvm.WireAppSdk
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
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
            setupWireMockStubs()
            val eventsHandler = object : WireEventsHandler() {
                override fun onEvent(event: String) {
                    println(event)
                }
            }
            setupSdk(eventsHandler)

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
        setupWireMockStubs()

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
        var capturedMessage: String? = null
        val customHandler = object : WireEventsHandler() {
            override fun onNewMLSMessage(value: String) {
                capturedMessage = value
            }
        }

        // Create SDK with our custom handler
        setupSdk(customHandler)

        // Execute
        val conversationStorage = get<ConversationStorage>()
        val conversationPrevious = conversationStorage.getById(CONVERSATION_ID)
        assertNull(conversationPrevious)

        val eventsRouter = get<EventsRouter>()
        runBlocking {
            eventsRouter.route(
                eventResponse = NEW_MLS_MESSAGE_EVENT,
                cryptoClient = mockCryptoClient(mockDecryptedMessage)
            )
        }

        // Verify
        assertEquals(mockDecryptedMessage, capturedMessage)

        val conversation = conversationStorage.getById(CONVERSATION_ID)
        assertEquals(conversation?.id, CONVERSATION_ID)
        assertEquals(conversation?.teamId, TEAM_ID)
    }

    private fun setupSdk(eventsHandler: WireEventsHandler) {
        WireAppSdk(
            applicationId = APPLICATION_ID,
            apiToken = API_TOKEN,
            apiHost = API_HOST,
            cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
            eventsHandler
        )
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

    private fun setupWireMockStubs() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/v7/apps")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "client_id": "dummyClientId",
                        "app_type": "dummyAppType",
                        "app_command": "dummyAppCommand"
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlMatching("/v7/clients")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "id": "dummyClientId"
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlMatching("/v7/login")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "access_token": "demoAccessToken",
                        "expires_in" : 3600
                    }
                    """.trimIndent()
                ).withHeaders(HttpHeaders(HttpHeader("set-cookie", "zuid=demoCookie")))
            )
        )
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/v7/access"))
                .withQueryParam("client_id", WireMock.matching(".*")).willReturn(
                    WireMock.okJson(
                        """
                        {
                            "access_token": "demoAccessToken",
                            "expires_in" : 3600
                        }
                        """.trimIndent()
                    )
                )
        )
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/v7/feature-configs")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "mls": {
                            "config": {
                                "allowedCipherSuites": [1],
                                "defaultCipherSuite": 1,
                                "defaultProtocol": "mls",
                                "supportedProtocols": ["mls", "proteus"]
                            },
                            "status": "enabled"
                        }
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.put(WireMock.urlPathTemplate("/v7/clients/{appClientId}")).willReturn(
                ok()
            )
        )
        wireMockServer.stubFor(
            WireMock.post(
                WireMock.urlPathTemplate("/v7/mls/key-packages/self/{appClientId}")
            ).willReturn(ok())
        )
    }

    companion object {
        private val APPLICATION_ID = UUID.randomUUID()
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "http://localhost:8086"
        private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "dummyPassword"
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
