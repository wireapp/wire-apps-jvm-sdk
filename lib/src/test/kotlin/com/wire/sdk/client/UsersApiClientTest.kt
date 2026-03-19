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
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
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
    private val userId = QualifiedId(
        id = UUID.fromString("3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a"),
        domain = "example.com"
    )

    private val secondUserId = QualifiedId(
        id = UUID.fromString("aabbccdd-1234-5678-abcd-aabbccddeeff"),
        domain = "example.com"
    )

    private val fullUserResponseJson = """
        {
            "qualified_id": { "id": "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a", "domain": "example.com" },
            "team": "ccddaabb-5678-1234-efab-112233445566",
            "email": "john@example.com",
            "name": "John Doe",
            "handle": "johndoe",
            "accent_id": 1,
            "supported_protocols": ["proteus", "mls"],
            "deleted": false
        }
    """.trimIndent()

    private val minimalUserResponseJson = """
        {
            "qualified_id": { "id": "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a", "domain": "example.com" },
            "team": null,
            "email": null,
            "name": "John Doe",
            "handle": null,
            "accent_id": 1,
            "supported_protocols": ["proteus"],
            "deleted": null
        }
    """.trimIndent()

    private val userClientsResponseJson = """
        [
            { "id": "client-abc" },
            { "id": "client-def" }
        ]
    """.trimIndent()

    private val listClientsResponseJson = """
        {
            "example.com": {
                "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a": [
                    { "id": "client-abc" },
                    { "id": "client-def" }
                ],
                "aabbccdd-1234-5678-abcd-aabbccddeeff": [
                    { "id": "client-xyz" }
                ]
            },
            "other.com": {
                "11111111-1111-1111-1111-111111111111": [
                    { "id": "client-remote" }
                ]
            }
        }
    """.trimIndent()

    @Test
    fun `given userId, when getUserData, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            val client = createMockHttpClient(
                responseBody = fullUserResponseJson,
                assertRequest = { capturedPath = it.url.fullPath }
            )

            UsersApiClient(client).getUserData(userId)

            assertEquals("/$API_VERSION/users/${userId.domain}/${userId.id}", capturedPath)
        }

    @Test
    fun `given userId, when getUserData, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            val client = createMockHttpClient(
                responseBody = fullUserResponseJson,
                assertRequest = { capturedMethod = it.method }
            )

            UsersApiClient(client).getUserData(userId)

            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `given full response, when getUserData, then fields deserialized`() =
        runTest {
            val client = createMockHttpClient(responseBody = fullUserResponseJson)

            val result = UsersApiClient(client).getUserData(userId)

            assertEquals(userId, result.id)
            assertEquals(UUID.fromString("ccddaabb-5678-1234-efab-112233445566"), result.teamId)
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
            val client = createMockHttpClient(responseBody = minimalUserResponseJson)

            val result = UsersApiClient(client).getUserData(userId)

            assertEquals(userId, result.id)
            assertNull(result.teamId)
            assertNull(result.email)
            assertNull(result.handle)
            assertNull(result.deleted)
            assertEquals(1, result.supportedProtocols.size)
        }

    @Test
    fun `given 404, when getUserData, then exception thrown`() =
        runTest {
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            content = """{"code": 404, "message": "Not found"}""",
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                        )
                    }
                }
                expectSuccess = true
            }

            assertFailsWith<io.ktor.client.plugins.ResponseException> {
                UsersApiClient(client).getUserData(userId)
            }
        }

    @Test
    fun `given userId, when getClientsByUserId, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            val client = createMockHttpClient(
                responseBody = userClientsResponseJson,
                assertRequest = { capturedPath = it.url.fullPath }
            )

            UsersApiClient(client).getClientsByUserId(userId)

            assertEquals("/v15/users/${userId.domain}/${userId.id}/clients", capturedPath)
        }

    @Test
    fun `given userId, when getClientsByUserId, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            val client = createMockHttpClient(
                responseBody = userClientsResponseJson,
                assertRequest = { capturedMethod = it.method }
            )

            UsersApiClient(client).getClientsByUserId(userId)

            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `given clients, when getClientsByUserId, then deserialized`() =
        runTest {
            val client = createMockHttpClient(responseBody = userClientsResponseJson)

            val result = UsersApiClient(client).getClientsByUserId(userId)

            assertEquals(2, result.size)
            assertEquals("client-abc", result[0].id)
            assertEquals("client-def", result[1].id)
        }

    @Test
    fun `given empty response, when getClientsByUserId, then empty list`() =
        runTest {
            val client = createMockHttpClient(responseBody = "[]")

            val result = UsersApiClient(client).getClientsByUserId(userId)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `given 404, when getClientsByUserId, then exception thrown`() =
        runTest {
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            content = """{"code": 404, "message": "Not found"}""",
                            status = HttpStatusCode.NotFound,
                            headers = headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                        )
                    }
                }
                expectSuccess = true
            }

            assertFailsWith<io.ktor.client.plugins.ResponseException> {
                UsersApiClient(client).getClientsByUserId(userId)
            }
        }

    @Test
    fun `given userIds, when getClientsByUserIds, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            val client = createMockHttpClient(
                responseBody = listClientsResponseJson,
                assertRequest = { capturedPath = it.url.fullPath }
            )

            UsersApiClient(client).getClientsByUserIds(listOf(userId))

            assertEquals("/v15/users/list-clients", capturedPath)
        }

    @Test
    fun `given userIds, when getClientsByUserIds, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            val client = createMockHttpClient(
                responseBody = listClientsResponseJson,
                assertRequest = { capturedMethod = it.method }
            )

            UsersApiClient(client).getClientsByUserIds(listOf(userId))

            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `given multi-domain, when getClientsByUserIds, then keyed by QualifiedId`() =
        runTest {
            val client = createMockHttpClient(responseBody = listClientsResponseJson)

            val result = UsersApiClient(client).getClientsByUserIds(listOf(userId, secondUserId))

            assertEquals(3, result.size)
            assertTrue(result.containsKey(userId))
            assertTrue(result.containsKey(secondUserId))
            assertTrue(
                result.containsKey(
                    QualifiedId(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "other.com"
                    )
                )
            )
        }

    @Test
    fun `given multi-domain, when getClientsByUserIds, then clients mapped`() =
        runTest {
            val client = createMockHttpClient(responseBody = listClientsResponseJson)

            val result = UsersApiClient(client).getClientsByUserIds(listOf(userId, secondUserId))

            assertEquals(listOf("client-abc", "client-def"), result[userId]?.map { it.id })
            assertEquals(listOf("client-xyz"), result[secondUserId]?.map { it.id })
            assertEquals(
                listOf("client-remote"),
                result[
                    QualifiedId(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "other.com"
                    )
                ]?.map { it.id }
            )
        }

    @Test
    fun `given empty response, when getClientsByUserIds, then empty map`() =
        runTest {
            val client = createMockHttpClient(responseBody = "{}")

            val result = UsersApiClient(client).getClientsByUserIds(emptyList())

            assertTrue(result.isEmpty())
        }

    @Test
    fun `given 500, when getClientsByUserIds, then exception thrown`() =
        runTest {
            val client = HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            content = """{"code": 500, "message": "Internal Server Error"}""",
                            status = HttpStatusCode.InternalServerError,
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

            assertFailsWith<io.ktor.client.plugins.ResponseException> {
                UsersApiClient(client).getClientsByUserIds(listOf(userId))
            }
        }
}
