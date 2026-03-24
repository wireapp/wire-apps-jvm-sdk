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

import com.wire.sdk.model.QualifiedId
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class OneToOneConversationsApiClientTest {
    private fun apiClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = OneToOneConversationsApiClient(
        createMockHttpClient(
            responseBody = responseBody,
            assertRequest = assertRequest
        )
    )

    @Test
    @DisplayName(
        "given valid userId, when getOneToOneConversation is called, " +
            "then correct URL is requested"
    )
    fun `test-1`() =
        runTest {
            var capturedPath: String? = null
            apiClient(MINIMAL_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getByUserId(USER_ID)
            assertEquals(EXPECTED_URL, capturedPath)
        }

    @Test
    fun `given valid userId, when getOneToOneConversation is called, then GET method is used`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(MINIMAL_RESPONSE_JSON) { capturedMethod = it.method }
                .getByUserId(USER_ID)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    @DisplayName(
        "given userId with subdomain, when getOneToOneConversation is called, " +
            "then URL is built correctly"
    )
    fun `test-2`() =
        runTest {
            var capturedPath: String? = null
            apiClient(MINIMAL_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getByUserId(SUBDOMAIN_USER_ID)
            assertEquals(
                "/one2one-conversations/" +
                    "${SUBDOMAIN_USER_ID.domain}/${SUBDOMAIN_USER_ID.id}",
                capturedPath
            )
        }

    companion object {
        private val USER_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = "example.com"
        )

        private val SUBDOMAIN_USER_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = "sub.wire.com"
        )

        private val EXPECTED_URL =
            "/one2one-conversations/${USER_ID.domain}/${USER_ID.id}"

        private val MINIMAL_CONVERSATION_JSON = """
            {
                "qualified_id": { "id": "${USER_ID.id}", "domain": "${USER_ID.domain}" },
                "team": null,
                "group_id": null,
                "name": "John Doe",
                "epoch": null,
                "protocol": "proteus",
                "members": {
                    "self": {
                        "qualified_id": { "id": "${USER_ID.id}", "domain": "${USER_ID.domain}" },
                        "conversation_role": "wire_member"
                    },
                    "others": []
                },
                "type": "2",
                "message_timer": null
            }
        """.trimIndent()

        private val MINIMAL_RESPONSE_JSON = """
            {
                "conversation": $MINIMAL_CONVERSATION_JSON
            }
        """.trimIndent()
    }
}
