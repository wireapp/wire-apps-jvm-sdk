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
import com.wire.integrations.jvm.TestUtils
import com.wire.integrations.jvm.TestUtils.V
import com.wire.integrations.jvm.WireEventsHandlerSuspending
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.utils.MockCoreCryptoClient.Companion.MLS_GROUP_ID
import com.wire.integrations.jvm.utils.MockCoreCryptoClient.Companion.MLS_GROUP_ID_BASE64
import io.ktor.http.HttpStatusCode
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WireApplicationManagerTest {
    @Test
    fun whenCreatingConversationIsHandledSuccessfullyThenReturnsConversationId() =
        runTest {
            // Given
            TestUtils.setupWireMockStubs(wireMockServer)
            val eventsHandler = object : WireEventsHandlerSuspending() {}
            TestUtils.setupSdk(eventsHandler)

            wireMockServer.stubFor(
                WireMock.post(
                    WireMock.urlPathTemplate(
                        "/$V/conversations"
                    )
                ).willReturn(
                    WireMock.jsonResponse(
                        CREATE_CONVERSATION_RESPONSE,
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
                    WireMock.okJson(MLS_KEYPACKAGE_CLAIMED_USER_2)
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
            assertTrue(cryptoClient.conversationExists(MLS_GROUP_ID))
        }

    companion object {
        private val wireMockServer = WireMockServer(8086)

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

        private val CREATE_CONVERSATION_RESPONSE =
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
                "group_id": "$MLS_GROUP_ID_BASE64",
                "team": "${TEAM_ID.value}",
                "type": 0
            }
            """.trimIndent()

        private val MLS_KEYPACKAGE_CLAIMED_USER_1 =
            """
            {
                "key_packages": []
            }
            """.trimIndent()

        private val MLS_KEYPACKAGE_CLAIMED_USER_2 =
            """
            {
                "key_packages": [
                    "client": "a0991ebb1935c09",
                    "domain": "wire.com",
                    "key_package": "AAEAASCVE6WPHxIa8Vft67p+n3ddwPttze/srwh88h3T9kBbKSAjQQ6esCwjBIVnF03H/AP0RM1qdR5hoOUwG7xFzt3OOSAGIwGQeRsCW2OOW0S+H0++tAr8P6E3qrSqoTg+UKTo9gABQEQ5ZjU0ZDEzNC02NDZlLTQ3NGYtYmQwYS1jYTg3MDJhZDZlNDA6YTA5OTFlYmIxOTM1YzA5QGNoYWxhLndpcmUubGluawIAAQoAAQACAAMABwAFAAAEAAEAAgEAAAAAaHjXmgAAAABo56OqAEBAb7RP7rdbTlHxfMma8JV1iv8JAtJYMwnnrtYzo9LLkUqZSFN7+mcMkMN6qjRbY4lpWZ9TDMTqWqciVSUTyYlAAgBAQMR5ag9/BRA4CQCOQYMQQ2jd6jRwjaWO0qZ9ShDhcJDMuZ0HQasVtbTVVylVWoUwxPaSorWLPuQ9TUpkA2w46gc=",
                    "key_package_ref": "RGQI8whr1iZI+LDdHGU1Ulaq4FIfSVBAompRGMBzvb0=",
                    "user": "${USER_2.id}"
                ]
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
