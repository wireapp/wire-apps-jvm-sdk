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
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.TypingStatus
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConversationsApiClientTest {
    private fun apiClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = ConversationsApiClient(
        createMockHttpClient(
            responseBody = responseBody,
            assertRequest = assertRequest
        )
    )

    @Test
    fun `when getConversation, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(CONVERSATION_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getConversation(CONVERSATION_ID)
            assertEquals(
                "/conversations/${CONVERSATION_ID.domain}/${CONVERSATION_ID.id}",
                capturedPath
            )
        }

    @Test
    fun `when getConversation, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(CONVERSATION_RESPONSE_JSON) { capturedMethod = it.method }
                .getConversation(CONVERSATION_ID)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `sendTypingStatus posts each status to the qualified conversation`() =
        runTest {
            for (status in TypingStatus.entries) {
                var capturedPath: String? = null
                var capturedMethod: HttpMethod? = null
                var capturedBody: String? = null
                apiClient("") {
                    capturedPath = it.url.encodedPath
                    capturedMethod = it.method
                    capturedBody = (it.body as TextContent).text
                }.sendTypingStatus(CONVERSATION_ID, status)

                assertEquals(
                    "/conversations/${CONVERSATION_ID.domain}/${CONVERSATION_ID.id}/typing",
                    capturedPath
                )
                assertEquals(HttpMethod.Post, capturedMethod)
                assertEquals("{\"status\":\"${status.name.lowercase()}\"}", capturedBody)
            }
        }

    @Test
    fun `sendTypingStatus uses the client retry policy`() =
        runTest {
            var requestCount = 0
            val httpClient = HttpClient(MockEngine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json) }
                install(HttpRequestRetry) { retryOnServerErrors(maxRetries = 2) }
                engine {
                    addHandler {
                        requestCount++
                        respond("", HttpStatusCode.ServiceUnavailable)
                    }
                }
            }

            try {
                assertFailsWith<ServerResponseException> {
                    ConversationsApiClient(httpClient).sendTypingStatus(
                        CONVERSATION_ID,
                        TypingStatus.STARTED
                    )
                }
                assertEquals(3, requestCount)
            } finally {
                httpClient.close()
            }
        }

    @Test
    fun `when createGroupConversation, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(CONVERSATION_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .createGroupConversation(CREATE_CONVERSATION_REQUEST)
            assertEquals("/conversations", capturedPath)
        }

    @Test
    fun `when createGroupConversation, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(CONVERSATION_RESPONSE_JSON) { capturedMethod = it.method }
                .createGroupConversation(CREATE_CONVERSATION_REQUEST)
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when updateConversationMemberRole, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient { capturedPath = it.url.encodedPath }
                .updateConversationMemberRole(CONVERSATION_ID, USER_ID, UPDATE_ROLE_REQUEST)
            assertEquals(
                "/conversations/${CONVERSATION_ID.domain}/${CONVERSATION_ID.id}" +
                    "/members/${USER_ID.domain}/${USER_ID.id}",
                capturedPath
            )
        }

    @Test
    fun `when updateConversationMemberRole, then PUT method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient { capturedMethod = it.method }
                .updateConversationMemberRole(CONVERSATION_ID, USER_ID, UPDATE_ROLE_REQUEST)
            assertEquals(HttpMethod.Put, capturedMethod)
        }

    @Test
    fun `when getConversationIds, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(CONVERSATION_IDS_PAGE_JSON) { capturedPath = it.url.encodedPath }
                .getConversationIds()
            assertEquals("/conversations/list-ids", capturedPath)
        }

    @Test
    fun `when getConversationIds, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(CONVERSATION_IDS_PAGE_JSON) { capturedMethod = it.method }
                .getConversationIds()
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when getConversationIds, then returns all qualified ids`() =
        runTest {
            val result = apiClient(CONVERSATION_IDS_PAGE_JSON).getConversationIds()
            assertEquals(2, result.size)
            assertEquals(CONVERSATION_ID, result[0])
        }

    @Test
    fun `when getConversationsById, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(CONVERSATIONS_LIST_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getConversationsById(listOf(CONVERSATION_ID))
            assertEquals("/conversations/list", capturedPath)
        }

    @Test
    fun `when getConversationsById, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(CONVERSATIONS_LIST_RESPONSE_JSON) { capturedMethod = it.method }
                .getConversationsById(listOf(CONVERSATION_ID))
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `given empty list, when getConversationsById, then no request made`() =
        runTest {
            var requestMade = false
            apiClient { requestMade = true }.getConversationsById(emptyList())
            assertEquals(false, requestMade)
        }

    @Test
    fun `when getConversationsById, then returns found conversations`() =
        runTest {
            val result = apiClient(CONVERSATIONS_LIST_RESPONSE_JSON)
                .getConversationsById(listOf(CONVERSATION_ID))
            assertEquals(1, result.size)
            assertEquals(CONVERSATION_ID, result[0].id)
        }

    @Test
    fun `when getConversationGroupInfo, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient { capturedPath = it.url.encodedPath }
                .getConversationGroupInfo(CONVERSATION_ID)
            assertEquals(
                "/conversations/${CONVERSATION_ID.domain}/${CONVERSATION_ID.id}/groupinfo",
                capturedPath
            )
        }

    @Test
    fun `when getConversationGroupInfo, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient { capturedMethod = it.method }
                .getConversationGroupInfo(CONVERSATION_ID)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when leaveConversation, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient { capturedPath = it.url.encodedPath }
                .leaveConversation(USER_ID, CONVERSATION_ID)
            assertEquals(
                "/conversations/${CONVERSATION_ID.domain}/${CONVERSATION_ID.id}" +
                    "/members/${USER_ID.domain}/${USER_ID.id}",
                capturedPath
            )
        }

    @Test
    fun `when leaveConversation, then DELETE method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient { capturedMethod = it.method }
                .leaveConversation(USER_ID, CONVERSATION_ID)
            assertEquals(HttpMethod.Delete, capturedMethod)
        }

    companion object {
        private val CONVERSATION_ID = QualifiedId(id = UUID.randomUUID(), domain = "example.com")
        private val USER_ID = QualifiedId(id = UUID.randomUUID(), domain = "example.com")
        private val TEAM_ID = TeamId(UUID.randomUUID())

        private val CREATE_CONVERSATION_REQUEST =
            CreateConversationRequest.createGroup("Test Group", TEAM_ID)
        private val UPDATE_ROLE_REQUEST =
            UpdateConversationMemberRoleRequest(ConversationRole.MEMBER)

        private val CONVERSATION_RESPONSE_JSON = """
            {
                "qualified_id": { "id": "${CONVERSATION_ID.id}", "domain": "${CONVERSATION_ID.domain}" },
                "team": null,
                "group_id": null,
                "name": "Test Group",
                "epoch": null,
                "protocol": "proteus",
                "members": {
                    "self": {
                        "qualified_id": { "id": "${USER_ID.id}", "domain": "${USER_ID.domain}" },
                        "conversation_role": "wire_member"
                    },
                    "others": []
                },
                "type": "0"
            }
        """.trimIndent()

        private val CONVERSATION_IDS_PAGE_JSON = """
            {
                "has_more": false,
                "paging_state": "page-state-1",
                "qualified_conversations": [
                    { "id": "${CONVERSATION_ID.id}", "domain": "${CONVERSATION_ID.domain}" },
                    { "id": "${USER_ID.id}", "domain": "${USER_ID.domain}" }
                ]
            }
        """.trimIndent()

        private val CONVERSATIONS_LIST_RESPONSE_JSON = """
            {
                "found": [
                    {
                        "qualified_id": { "id": "${CONVERSATION_ID.id}", "domain": "${CONVERSATION_ID.domain}" },
                        "team": null,
                        "group_id": null,
                        "name": "Test Group",
                        "epoch": null,
                        "protocol": "proteus",
                        "members": {
                            "self": {
                                "qualified_id": { "id": "${USER_ID.id}", "domain": "${USER_ID.domain}" },
                                "conversation_role": "wire_member"
                            },
                            "others": []
                        },
                        "type": "0"
                    }
                ],
                "failed": [],
                "not_found": []
            }
        """.trimIndent()
    }
}
