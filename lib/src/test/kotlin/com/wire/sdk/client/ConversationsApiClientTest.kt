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
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationsApiClientTest {
    private val conversationId = QualifiedId(
        id = UUID.fromString("aabbccdd-1234-5678-abcd-aabbccddeeff"),
        domain = "example.com"
    )

    private val userId = QualifiedId(
        id = UUID.fromString("3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a"),
        domain = "example.com"
    )

    private val teamId = TeamId(UUID.fromString("ccddaabb-5678-1234-efab-112233445566"))

    private val conversationResponseJson = """
        {
            "qualified_id": { "id": "aabbccdd-1234-5678-abcd-aabbccddeeff", "domain": "example.com" },
            "team": null,
            "group_id": null,
            "name": "Test Group",
            "epoch": null,
            "protocol": "proteus",
            "members": {
                "self": {
                    "qualified_id": { "id": "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a", "domain": "example.com" },
                    "conversation_role": "wire_member"
                },
                "others": []
            },
            "type": "0"
        }
    """.trimIndent()

    private val conversationIdsPageOneJson = """
        {
            "has_more": false,
            "paging_state": "page-state-1",
            "qualified_conversations": [
                { "id": "aabbccdd-1234-5678-abcd-aabbccddeeff", "domain": "example.com" },
                { "id": "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a", "domain": "example.com" }
            ]
        }
    """.trimIndent()

    private val conversationsListResponseJson = """
        {
            "found": [
                {
                    "qualified_id": { "id": "aabbccdd-1234-5678-abcd-aabbccddeeff", "domain": "example.com" },
                    "team": null,
                    "group_id": null,
                    "name": "Test Group",
                    "epoch": null,
                    "protocol": "proteus",
                    "members": {
                        "self": {
                            "qualified_id": { "id": "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a", "domain": "example.com" },
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

    private fun mockClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ): HttpClient =
        HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    assertRequest(request)
                    respond(
                        content = responseBody,
                        status = HttpStatusCode.OK,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                    )
                }
            }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private fun apiClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = ConversationsApiClient(mockClient(responseBody, assertRequest))

    @Test
    fun `when getConversation, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(conversationResponseJson) { capturedPath = it.url.encodedPath }
                .getConversation(conversationId)
            assertEquals(
                "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}",
                capturedPath
            )
        }

    @Test
    fun `when getConversation, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(conversationResponseJson) { capturedMethod = it.method }
                .getConversation(conversationId)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when createGroupConversation, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            val request = CreateConversationRequest.createGroup("Test Group", teamId)
            apiClient(conversationResponseJson) { capturedPath = it.url.encodedPath }
                .createGroupConversation(request)
            assertEquals("/$API_VERSION/conversations", capturedPath)
        }

    @Test
    fun `when createGroupConversation, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            val request = CreateConversationRequest.createGroup("Test Group", teamId)
            apiClient(conversationResponseJson) { capturedMethod = it.method }
                .createGroupConversation(request)
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when updateConversationMemberRole, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            val request = UpdateConversationMemberRoleRequest(ConversationRole.MEMBER)
            apiClient { capturedPath = it.url.encodedPath }
                .updateConversationMemberRole(conversationId, userId, request)
            assertEquals(
                "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}" +
                    "/members/${userId.domain}/${userId.id}",
                capturedPath
            )
        }

    @Test
    fun `when updateConversationMemberRole, then PUT method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            val request = UpdateConversationMemberRoleRequest(ConversationRole.MEMBER)
            apiClient { capturedMethod = it.method }
                .updateConversationMemberRole(conversationId, userId, request)
            assertEquals(HttpMethod.Put, capturedMethod)
        }

    @Test
    fun `when getConversationIds, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(conversationIdsPageOneJson) { capturedPath = it.url.encodedPath }
                .getConversationIds()
            assertEquals("/$API_VERSION/conversations/list-ids", capturedPath)
        }

    @Test
    fun `when getConversationIds, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(conversationIdsPageOneJson) { capturedMethod = it.method }
                .getConversationIds()
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when getConversationIds, then returns all qualified ids`() =
        runTest {
            val result = apiClient(conversationIdsPageOneJson).getConversationIds()
            assertEquals(2, result.size)
            assertEquals(conversationId, result[0])
        }

    @Test
    fun `when getConversationsById, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(conversationsListResponseJson) { capturedPath = it.url.encodedPath }
                .getConversationsById(listOf(conversationId))
            assertEquals("/$API_VERSION/conversations/list", capturedPath)
        }

    @Test
    fun `when getConversationsById, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(conversationsListResponseJson) { capturedMethod = it.method }
                .getConversationsById(listOf(conversationId))
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
            val result = apiClient(conversationsListResponseJson)
                .getConversationsById(listOf(conversationId))
            assertEquals(1, result.size)
            assertEquals(conversationId, result[0].id)
        }

    @Test
    fun `when getConversationGroupInfo, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient { capturedPath = it.url.encodedPath }
                .getConversationGroupInfo(conversationId)
            assertEquals(
                "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}/groupinfo",
                capturedPath
            )
        }

    @Test
    fun `when getConversationGroupInfo, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient { capturedMethod = it.method }
                .getConversationGroupInfo(conversationId)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when leaveConversation, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient { capturedPath = it.url.encodedPath }
                .leaveConversation(userId, conversationId)
            assertEquals(
                "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}" +
                    "/members/${userId.domain}/${userId.id}",
                capturedPath
            )
        }

    @Test
    fun `when leaveConversation, then DELETE method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient { capturedMethod = it.method }
                .leaveConversation(userId, conversationId)
            assertEquals(HttpMethod.Delete, capturedMethod)
        }
}
