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
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.toGroupId
import com.wire.sdk.TestUtils
import com.wire.sdk.TestUtils.V
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.CoreCryptoClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.AppClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.utils.MlsTransportLastWelcome
import io.ktor.http.HttpStatusCode
import io.ktor.util.encodeBase64
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireApplicationManagerTest {
    @Test
    fun whenCreatingGroupConversationIsHandledSuccessfullyThenReturnsConversationId() =
        runTest {
            // Given
            TestUtils.setupWireMockStubs(wireMockServer)
            val eventsHandler = object : WireEventsHandlerSuspending() {}
            TestUtils.setupSdk(eventsHandler)

            newPackages = generateUser2Packages()

            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/conversations"
                    )
                ).willReturn(
                    WireMock.jsonResponse(
                        CREATE_GROUP_CONVERSATION_RESPONSE,
                        HttpStatusCode.Created.value
                    )
                )
            )
            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/mls/key-packages/claim/${USER_1.domain}/${USER_1.id}"
                    )
                ).willReturn(
                    WireMock.okJson(MLS_KEYPACKAGE_CLAIMED_USER_1)
                )
            )
            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/mls/key-packages/claim/${USER_2.domain}/${USER_2.id}"
                    )
                ).willReturn(
                    WireMock.okJson(
                        getDynamicKeyPackageClaimedUser(
                            userId = USER_2.id.toString(),
                            keyPackage = newPackages[0].value.copyBytes().encodeBase64()
                        )
                    )
                )
            )

            val manager = IsolatedKoinContext.koinApp.koin.get<WireApplicationManager>()
            val cryptoClient = IsolatedKoinContext.koinApp.koin.get<CryptoClient>()

            // when
            val result = manager.createGroupConversation(
                name = CONVERSATION_NAME,
                userIds = listOf(
                    USER_1,
                    USER_2
                )
            )

            // then
            assertEquals(
                CONVERSATION_ID.id,
                result.id
            )
            assertTrue(
                cryptoClient.conversationExists(
                    GROUP_CONVERSATION_MLS_GROUP_ID
                )
            )
        }

    @Test
    fun whenCreatingOneToOneConversationIsHandledSuccessfullyThenReturnsConversationId() =
        runTest {
            // Given
            TestUtils.setupWireMockStubs(wireMockServer)
            val eventsHandler = object : WireEventsHandlerSuspending() {}
            TestUtils.setupSdk(eventsHandler)

            newPackages = generateUser2Packages()

            wireMockServer.stubFor(
                WireMock.get(
                    WireMock.urlPathTemplate(
                        "/$V/one2one-conversations/${USER_2.domain}/${USER_2.id}"
                    )
                ).willReturn(
                    WireMock.jsonResponse(
                        CREATE_ONE_TO_ONE_CONVERSATION_RESPONSE,
                        HttpStatusCode.Created.value
                    )
                )
            )

            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/mls/key-packages/claim/${USER_2.domain}/${USER_2.id}"
                    )
                ).willReturn(
                    WireMock.okJson(
                        getDynamicKeyPackageClaimedUser(
                            userId = USER_2.id.toString(),
                            keyPackage = newPackages[1].value.copyBytes().encodeBase64()
                        )
                    )
                )
            )

            val manager = IsolatedKoinContext.koinApp.koin.get<WireApplicationManager>()
            val cryptoClient = IsolatedKoinContext.koinApp.koin.get<CryptoClient>()

            // when
            val result = manager.createOneToOneConversation(
                userId = USER_2
            )

            // then
            assertEquals(
                CONVERSATION_ID.id,
                result.id
            )
            assertTrue(
                cryptoClient.conversationExists(
                    ONE_TO_ONE_CONVERSATION_MLS_GROUP_ID
                )
            )
        }

    @Test
    fun whenCreatingChannelConversationIsHandledSuccessfullyThenReturnsConversationId() =
        runTest {
            // Given
            TestUtils.setupWireMockStubs(wireMockServer)
            val eventsHandler = object : WireEventsHandlerSuspending() {}
            TestUtils.setupSdk(eventsHandler)

            newPackages = generateUser2Packages()

            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/conversations"
                    )
                ).willReturn(
                    WireMock.jsonResponse(
                        CREATE_CHANNEL_CONVERSATION_RESPONSE,
                        HttpStatusCode.Created.value
                    )
                )
            )

            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/mls/key-packages/claim/${USER_1.domain}/${USER_1.id}"
                    )
                ).willReturn(
                    WireMock.okJson(MLS_KEYPACKAGE_CLAIMED_USER_1)
                )
            )
            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/mls/key-packages/claim/${USER_2.domain}/${USER_2.id}"
                    )
                ).willReturn(
                    WireMock.okJson(
                        getDynamicKeyPackageClaimedUser(
                            userId = USER_2.id.toString(),
                            keyPackage = newPackages[2].value.copyBytes().encodeBase64()
                        )
                    )
                )
            )

            val manager = IsolatedKoinContext.koinApp.koin.get<WireApplicationManager>()
            val cryptoClient = IsolatedKoinContext.koinApp.koin.get<CryptoClient>()

            // when
            val result = manager.createChannelConversation(
                name = CONVERSATION_NAME,
                userIds = listOf(
                    USER_1,
                    USER_2
                )
            )

            // then
            assertEquals(
                CONVERSATION_ID.id,
                result.id
            )
            assertTrue(
                cryptoClient.conversationExists(
                    CHANNEL_CONVERSATION_MLS_GROUP_ID
                )
            )
        }

    @Test
    fun whenCreatingChannelConversationAndTeamIdIsNullThenThrowException() =
        runTest {
            // Given
            TestUtils.setupWireMockStubs(wireMockServer)
            val eventsHandler = object : WireEventsHandlerSuspending() {}
            TestUtils.setupSdk(eventsHandler)

            wireMockServer.stubFor(
                WireMock.get(
                    WireMock.urlPathTemplate("/$V/self")
                ).willReturn(
                    WireMock.okJson(
                        SELF_USER_NO_TEAM_ID_RESPONSE
                    )
                )
            )

            val manager = IsolatedKoinContext.koinApp.koin.get<WireApplicationManager>()

            // then
            assertThrows<WireException.MissingParameter> {
                // when
                manager.createChannelConversation(
                    name = CONVERSATION_NAME,
                    userIds = listOf(
                        USER_1,
                        USER_2
                    )
                )
            }
        }

    private suspend fun generateUser2Packages(): List<MLSKeyPackage> =
        CoreCryptoClient.create(
            userId = USER_2.id.toString(),
            ciphersuiteCode = 1
        ).use { cryptoClientUser2 ->
            cryptoClientUser2.initializeMlsClient(
                appClientId = AppClientId("user_${USER_2.id}"),
                mlsTransport = testMlsTransport
            )
            cryptoClientUser2.mlsGenerateKeyPackages(10U)
        }

    companion object {
        private val wireMockServer = WireMockServer(8086)
        private val testMlsTransport = MlsTransportLastWelcome()
        private lateinit var newPackages: List<MLSKeyPackage>

        private const val CONVERSATION_NAME = "Conversation Name"
        private const val DOMAIN = "wire.com"
        private val CONVERSATION_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = DOMAIN
            )
        private val TEAM_ID = TeamId(UUID.randomUUID())
        private val USER_1 = QualifiedId(
            id = UUID.randomUUID(),
            domain = DOMAIN
        )
        private val USER_2 = QualifiedId(
            id = UUID.randomUUID(),
            domain = DOMAIN
        )

        val GROUP_CONVERSATION_MLS_GROUP_ID = UUID.randomUUID().toString().toGroupId()
        val GROUP_CONVERSATION_MLS_GROUP_ID_BASE64 =
            Base64.getEncoder().encodeToString(GROUP_CONVERSATION_MLS_GROUP_ID.copyBytes())

        val ONE_TO_ONE_CONVERSATION_MLS_GROUP_ID = UUID.randomUUID().toString().toGroupId()
        val ONE_TO_ONE_CONVERSATION_MLS_GROUP_ID_BASE64 =
            Base64.getEncoder().encodeToString(ONE_TO_ONE_CONVERSATION_MLS_GROUP_ID.copyBytes())

        val CHANNEL_CONVERSATION_MLS_GROUP_ID = UUID.randomUUID().toString().toGroupId()
        val CHANNEL_CONVERSATION_MLS_GROUP_ID_BASE64 =
            Base64.getEncoder().encodeToString(CHANNEL_CONVERSATION_MLS_GROUP_ID.copyBytes())

        private val CREATE_GROUP_CONVERSATION_RESPONSE =
            """
            {
                "qualified_id": {
                    "id": "${CONVERSATION_ID.id}",
                    "domain": "${CONVERSATION_ID.domain}"
                },
                "name": "Test conversation",
                "epoch": 0,
                "members": {
                    "others": []
                },
                "group_id": "$GROUP_CONVERSATION_MLS_GROUP_ID_BASE64",
                "team": "${TEAM_ID.value}",
                "type": 0
                "protocol": "mls"
            }
            """.trimIndent()

        private val CREATE_CHANNEL_CONVERSATION_RESPONSE =
            """
            {
                "qualified_id": {
                    "id": "${CONVERSATION_ID.id}",
                    "domain": "${CONVERSATION_ID.domain}"
                },
                "name": "Test conversation",
                "epoch": 0,
                "members": {
                    "others": []
                },
                "group_id": "$CHANNEL_CONVERSATION_MLS_GROUP_ID_BASE64",
                "team": "${TEAM_ID.value}",
                "type": 0,
                "protocol": "mls"
            }
            """.trimIndent()

        private val CREATE_ONE_TO_ONE_CONVERSATION_RESPONSE =
            """
            {
                "conversation": {
                    "qualified_id": {
                        "id": "${CONVERSATION_ID.id}",
                        "domain": "${CONVERSATION_ID.domain}"
                    },
                    "name": "Test conversation",
                    "epoch": 0,
                    "members": {
                        "others": []
                    },
                    "group_id": "$ONE_TO_ONE_CONVERSATION_MLS_GROUP_ID_BASE64",
                    "team": "${TEAM_ID.value}",
                    "type": 1,
                    "protocol": "mls"
                },
                "public_keys": {
                    "removal": {
                        "ecdsa_secp256r1_sha256": "BGBbuHvwWYBrTru7sFzzcK/oT9XVzGkdNv/6iBHNtEo9QVDmYKbtW2FA+f+iNoOBgvhjp6mYQKmypa+z63u5/Qs=",
                        "ecdsa_secp384r1_sha384": "BMW56MVt4zR1oCHv40t/Q9VDqMBPsetBzESkCY3lXhyQmEMaJRO293D4v94qTrSwSFNHG9859anU03OtQo2CXz5Tsgr2HTL7cNBpGWrROPSmS+dx/mKx4sugHn2zakM9hA==",
                        "ecdsa_secp521r1_sha512": "BACrVVw3tK68GL8F7FP05mUp5y2zSV5eofS48BVoYNLdcNOBlKokO0f3mtGqLEiKPbgVncKeMskaZap2wL/kc1v/1wFCBdoSx5lS+efz1Fe3sx+lwjuhwkGW891lsjpbXzdkWGsM0yHY83DCgGT3XGaITURmL4I+EqEiMqtgi4VWo26+Nw==",
                        "ed25519": "3AEFMpXsnJ28RcyA7CIRuaDL7L0vGmKaGjD206SANZw="
                    }
                }
            }
            """.trimIndent()

        private val MLS_KEYPACKAGE_CLAIMED_USER_1 =
            """
            {
                "key_packages": []
            }
            """.trimIndent()

        private fun getDynamicKeyPackageClaimedUser(
            userId: String,
            keyPackage: String
        ): String =
            """
            {
                "key_packages": [
                    {
                        "client": "a0991ebb1935c08",
                        "domain": "wire.com",
                        "key_package": "$keyPackage",
                        "key_package_ref": "RGQI8whr1iZI+LDdHGU1Ulaq4FIfSVBAompRGMBzvb0=",
                        "user": "$userId"
                    }
                ]
            }
            """.trimIndent()

        private val SELF_USER_NO_TEAM_ID_RESPONSE =
            """
            {
              "qualified_id": {
                "domain": "staging.zinfra.io",
                "id": "b82c3381-37b0-4545-b555-ca32a3a093d0"
              },
              "email": "sdk.user@wire.com",
              "name": "SDK User"
            }
            """.trimIndent()

        @JvmStatic
        @BeforeAll
        fun before() {
            IsolatedKoinContext.start()
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
            IsolatedKoinContext.stop()
        }
    }
}
