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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsersApiClientTest {
    private fun apiClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = UsersApiClient(
        createMockHttpClient(responseBody = responseBody, assertRequest = assertRequest)
    )

    @Test
    fun `given userId, when getUserData, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(FULL_USER_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getUserData(USER_ID)
            assertEquals("/users/${USER_ID.domain}/${USER_ID.id}", capturedPath)
        }

    @Test
    fun `given userId, when getUserData, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(FULL_USER_RESPONSE_JSON) { capturedMethod = it.method }
                .getUserData(USER_ID)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `given full response, when getUserData, then fields deserialized`() =
        runTest {
            val result = apiClient(FULL_USER_RESPONSE_JSON).getUserData(USER_ID)
            assertEquals(USER_ID, result.id)
            assertEquals(TEAM_UUID, result.teamId)
            assertEquals("john@example.com", result.email)
            assertEquals("John Doe", result.name)
            assertEquals("johndoe", result.handle)
            assertEquals(1L, result.accentId)
            assertEquals(2, result.supportedProtocols.size)
            assertEquals(false, result.deleted)
        }

    @Test
    fun `given minimal response, when getUserData, then nullables are null`() =
        runTest {
            val result = apiClient(MINIMAL_USER_RESPONSE_JSON).getUserData(USER_ID)
            assertEquals(USER_ID, result.id)
            assertNull(result.teamId)
            assertNull(result.email)
            assertNull(result.handle)
            assertNull(result.deleted)
            assertEquals(1, result.supportedProtocols.size)
        }

    @Test
    fun `given 404, when getUserData, then exception thrown`() =
        runTest {
            assertFailsWith<io.ktor.client.plugins.ResponseException> {
                errorClient(HttpStatusCode.NotFound).getUserData(USER_ID)
            }
        }

    @Test
    fun `given userId, when getClientsByUserId, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(USER_CLIENTS_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getClientsByUserId(USER_ID)
            assertEquals("/users/${USER_ID.domain}/${USER_ID.id}/clients", capturedPath)
        }

    @Test
    fun `given userId, when getClientsByUserId, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(USER_CLIENTS_RESPONSE_JSON) { capturedMethod = it.method }
                .getClientsByUserId(USER_ID)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `given clients, when getClientsByUserId, then deserialized`() =
        runTest {
            val result = apiClient(USER_CLIENTS_RESPONSE_JSON).getClientsByUserId(USER_ID)
            assertEquals(2, result.size)
            assertEquals("client-abc", result[0].id)
            assertEquals("client-def", result[1].id)
        }

    @Test
    fun `given empty response, when getClientsByUserId, then empty list`() =
        runTest {
            val result = apiClient("[]").getClientsByUserId(USER_ID)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `given 404, when getClientsByUserId, then exception thrown`() =
        runTest {
            assertFailsWith<io.ktor.client.plugins.ResponseException> {
                errorClient(HttpStatusCode.NotFound).getClientsByUserId(USER_ID)
            }
        }

    @Test
    fun `given userIds, when getClientsByUserIds, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(LIST_CLIENTS_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getClientsByUserIds(listOf(USER_ID))
            assertEquals("/users/list-clients", capturedPath)
        }

    @Test
    fun `given userIds, when getClientsByUserIds, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(LIST_CLIENTS_RESPONSE_JSON) { capturedMethod = it.method }
                .getClientsByUserIds(listOf(USER_ID))
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `given multi-domain, when getClientsByUserIds, then keyed by QualifiedId`() =
        runTest {
            val result = apiClient(LIST_CLIENTS_RESPONSE_JSON)
                .getClientsByUserIds(listOf(USER_ID, SECOND_USER_ID))
            assertEquals(3, result.size)
            assertTrue(result.containsKey(USER_ID))
            assertTrue(result.containsKey(SECOND_USER_ID))
            assertTrue(result.containsKey(REMOTE_USER_ID))
        }

    @Test
    fun `given multi-domain, when getClientsByUserIds, then clients mapped`() =
        runTest {
            val result = apiClient(LIST_CLIENTS_RESPONSE_JSON)
                .getClientsByUserIds(listOf(USER_ID, SECOND_USER_ID))
            assertEquals(listOf("client-abc", "client-def"), result[USER_ID]?.map { it.id })
            assertEquals(listOf("client-xyz"), result[SECOND_USER_ID]?.map { it.id })
            assertEquals(listOf("client-remote"), result[REMOTE_USER_ID]?.map { it.id })
        }

    @Test
    fun `given empty response, when getClientsByUserIds, then empty map`() =
        runTest {
            val result = apiClient("{}").getClientsByUserIds(emptyList())
            assertTrue(result.isEmpty())
        }

    @Test
    fun `given 500, when getClientsByUserIds, then exception thrown`() =
        runTest {
            assertFailsWith<io.ktor.client.plugins.ResponseException> {
                errorClient(HttpStatusCode.InternalServerError).getClientsByUserIds(listOf(USER_ID))
            }
        }

    companion object {
        private val USER_ID = QualifiedId(id = UUID.randomUUID(), domain = "example.com")
        private val SECOND_USER_ID = QualifiedId(id = UUID.randomUUID(), domain = "example.com")
        private val REMOTE_USER_ID = QualifiedId(id = UUID.randomUUID(), domain = "other.com")
        private val TEAM_UUID = UUID.randomUUID()

        private fun errorClient(status: HttpStatusCode) =
            UsersApiClient(
                HttpClient(MockEngine) {
                    engine {
                        addHandler {
                            respond(
                                content = """{"code": ${status.value}, 
                                    |"message": "${status.description}"}
                                """.trimMargin(),
                                status = status,
                                headers = headersOf(
                                    HttpHeaders.ContentType,
                                    ContentType.Application.Json.toString()
                                )
                            )
                        }
                    }
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                    expectSuccess = true
                }
            )

        private val FULL_USER_RESPONSE_JSON = """
            {
                "qualified_id": { "id": "${USER_ID.id}", "domain": "${USER_ID.domain}" },
                "team": "$TEAM_UUID",
                "email": "john@example.com",
                "name": "John Doe",
                "handle": "johndoe",
                "accent_id": 1,
                "supported_protocols": ["proteus", "mls"],
                "deleted": false
            }
        """.trimIndent()

        private val MINIMAL_USER_RESPONSE_JSON = """
            {
                "qualified_id": { "id": "${USER_ID.id}", "domain": "${USER_ID.domain}" },
                "team": null,
                "email": null,
                "name": "John Doe",
                "handle": null,
                "accent_id": 1,
                "supported_protocols": ["proteus"],
                "deleted": null
            }
        """.trimIndent()

        private val USER_CLIENTS_RESPONSE_JSON = """
            [
                { "id": "client-abc" },
                { "id": "client-def" }
            ]
        """.trimIndent()

        private val LIST_CLIENTS_RESPONSE_JSON = """
            {
                "example.com": {
                    "${USER_ID.id}": [
                        { "id": "client-abc" },
                        { "id": "client-def" }
                    ],
                    "${SECOND_USER_ID.id}": [
                        { "id": "client-xyz" }
                    ]
                },
                "other.com": {
                    "${REMOTE_USER_ID.id}": [
                        { "id": "client-remote" }
                    ]
                }
            }
        """.trimIndent()
    }
}
