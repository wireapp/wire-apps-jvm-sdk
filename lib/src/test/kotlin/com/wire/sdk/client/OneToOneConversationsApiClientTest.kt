/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.sdk.client

import com.wire.sdk.client.BackendClient.Companion.API_VERSION
import com.wire.sdk.model.QualifiedId
import io.ktor.http.HttpMethod
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OneToOneConversationsApiClientTest {
    private val userId = QualifiedId(
        id = UUID.fromString("3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a"),
        domain = "example.com"
    )

    private val expectedUrl =
        "/$API_VERSION/one2one-conversations/example.com/3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a"

    // Minimal valid ConversationResponse JSON — no publicKeys, no team, no groupId, no messageTimer
    private val minimalConversationJson = """
        {
            "qualified_id": { "id": "aabbccdd-1234-5678-abcd-aabbccddeeff", "domain": "example.com" },
            "team": null,
            "group_id": null,
            "name": "John Doe",
            "epoch": null,
            "protocol": "proteus",
            "members": {
                "self": { "qualified_id": { "id": "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a", "domain": "example.com" }, "conversation_role": "wire_member" },
                "others": []
            },
            "type": "2",
            "message_timer": null
        }
    """.trimIndent()

    private val minimalResponseJson = """
        {
            "conversation": $minimalConversationJson
        }
    """.trimIndent()

    @Test
    @DisplayName(
        "given valid userId, when getOneToOneConversation is called, " +
            "then correct URL is requested"
    )
    fun `test-1`() =
        runTest {
            var capturedPath: String? = null
            val client = createMockHttpClient(
                responseBody = minimalResponseJson,
                assertRequest = { capturedPath = it.url.fullPath }
            )

            OneToOneConversationsApiClient(client).getByUserId(userId)

            assertEquals(expectedUrl, capturedPath)
        }

    @Test
    fun `given valid userId, when getOneToOneConversation is called, then GET method is used`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            val client = createMockHttpClient(
                responseBody = minimalResponseJson,
                assertRequest = { capturedMethod = it.method }
            )

            OneToOneConversationsApiClient(client).getByUserId(userId)

            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    @DisplayName(
        "given userId with subdomain, when getOneToOneConversation is called, " +
            "then URL is built correctly"
    )
    fun `test-2`() =
        runTest {
            val subdomainUserId = QualifiedId(
                id = UUID.fromString("aabbccdd-1234-5678-abcd-aabbccddeeff"),
                domain = "sub.wire.com"
            )
            var capturedPath: String? = null
            val client = createMockHttpClient(
                responseBody = minimalResponseJson,
                assertRequest = { capturedPath = it.url.fullPath }
            )

            OneToOneConversationsApiClient(client).getByUserId(subdomainUserId)

            assertEquals(
                "/$API_VERSION/one2one-conversations/" +
                    "sub.wire.com/aabbccdd-1234-5678-abcd-aabbccddeeff",
                capturedPath
            )
        }
}
