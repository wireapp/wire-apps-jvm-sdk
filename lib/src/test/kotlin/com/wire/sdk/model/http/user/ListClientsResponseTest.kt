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

package com.wire.sdk.model.http.user

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ListClientsResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes qualified_user_map key correctly`() {
        val jsonString = """
            {
                "qualified_user_map": {
                    "example.com": {
                        "99db9768-04e3-4b5d-9268-831b6a25c4ab": [
                            { "id": "d0" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        assertTrue(result.qualifiedUserMap.containsKey("example.com"))
    }

    @Test
    fun `deserializes single domain single user single client correctly`() {
        val jsonString = """
            {
                "qualified_user_map": {
                    "example.com": {
                        "99db9768-04e3-4b5d-9268-831b6a25c4ab": [
                            { "id": "d0" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        val clients =
            result.qualifiedUserMap["example.com"]?.get("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        assertNotNull(clients)
        assertEquals(1, clients!!.size)
        assertEquals("d0", clients[0].id)
    }

    @Test
    fun `deserializes single domain single user multiple clients correctly`() {
        val jsonString = """
            {
                "qualified_user_map": {
                    "example.com": {
                        "99db9768-04e3-4b5d-9268-831b6a25c4ab": [
                            { "id": "d0" },
                            { "id": "d1" },
                            { "id": "d2" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        val clients =
            result.qualifiedUserMap["example.com"]?.get("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        assertNotNull(clients)
        assertEquals(3, clients!!.size)
        assertEquals(listOf("d0", "d1", "d2"), clients.map { it.id })
    }

    @Test
    fun `deserializes single domain multiple users correctly`() {
        val jsonString = """
            {
                "qualified_user_map": {
                    "example.com": {
                        "99db9768-04e3-4b5d-9268-831b6a25c4ab": [
                            { "id": "d0" }
                        ],
                        "1d51e2d6-9c70-605f-efc8-ff85c3dabdc7": [
                            { "id": "d1" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        val domainMap = result.qualifiedUserMap["example.com"]
        assertNotNull(domainMap)
        assertEquals(2, domainMap!!.size)
        assertEquals("d0", domainMap["99db9768-04e3-4b5d-9268-831b6a25c4ab"]?.first()?.id)
        assertEquals("d1", domainMap["1d51e2d6-9c70-605f-efc8-ff85c3dabdc7"]?.first()?.id)
    }

    @Test
    fun `deserializes multiple domains correctly`() {
        val jsonString = """
            {
                "qualified_user_map": {
                    "example.com": {
                        "99db9768-04e3-4b5d-9268-831b6a25c4ab": [
                            { "id": "d0" }
                        ]
                    },
                    "staging.zinfra.io": {
                        "1d51e2d6-9c70-605f-efc8-ff85c3dabdc7": [
                            { "id": "d1" }
                        ]
                    }
                }
            }
        """.trimIndent()

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        assertEquals(2, result.qualifiedUserMap.size)
        assertTrue(result.qualifiedUserMap.containsKey("example.com"))
        assertTrue(result.qualifiedUserMap.containsKey("staging.zinfra.io"))
    }

    @Test
    fun `deserializes empty qualified_user_map correctly`() {
        val jsonString = """{"qualified_user_map": {}}"""

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        assertTrue(result.qualifiedUserMap.isEmpty())
    }

    @Test
    fun `deserializes user with empty client list correctly`() {
        val jsonString = """
            {
                "qualified_user_map": {
                    "example.com": {
                        "99db9768-04e3-4b5d-9268-831b6a25c4ab": []
                    }
                }
            }
        """.trimIndent()

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        val clients =
            result.qualifiedUserMap["example.com"]?.get("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        assertNotNull(clients)
        assertTrue(clients!!.isEmpty())
    }

    @Test
    fun `ignores unknown keys in response`() {
        val jsonString = """
            {
                "qualified_user_map": {
                    "example.com": {
                        "99db9768-04e3-4b5d-9268-831b6a25c4ab": [
                            { "id": "d0", "unknown_field": "should_be_ignored" }
                        ]
                    }
                },
                "unexpected_top_level_key": "should_be_ignored"
            }
        """.trimIndent()

        val result = json.decodeFromString<ListClientsResponse>(jsonString)

        assertEquals(
            "d0",
            result.qualifiedUserMap["example.com"]
                ?.get("99db9768-04e3-4b5d-9268-831b6a25c4ab")
                ?.first()?.id
        )
    }

    @Test
    fun `serializes qualified_user_map key correctly`() {
        val response = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d0"))
                )
            )
        )

        val result = json.encodeToString(response)

        assertTrue(result.contains("\"qualified_user_map\""))
    }

    @Test
    fun `serializes empty map correctly`() {
        val response = ListClientsResponse(qualifiedUserMap = emptyMap())

        val result = json.encodeToString(response)
        val expected = """{"qualified_user_map":{}}"""

        assertEquals(expected, result)
    }

    @Test
    fun `two responses with same data are equal`() {
        val response1 = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d0"))
                )
            )
        )
        val response2 = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d0"))
                )
            )
        )

        assertEquals(response1, response2)
    }

    @Test
    fun `two responses with different domains are not equal`() {
        val response1 = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d0"))
                )
            )
        )
        val response2 = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "staging.zinfra.io" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d0"))
                )
            )
        )

        assertNotEquals(response1, response2)
    }

    @Test
    fun `two responses with different client ids are not equal`() {
        val response1 = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d0"))
                )
            )
        )
        val response2 = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d1"))
                )
            )
        )

        assertNotEquals(response1, response2)
    }

    @Test
    fun `serialization round-trip preserves single domain single user`() {
        val original = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(UserClientResponse(id = "d0"))
                )
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ListClientsResponse>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `serialization round-trip preserves multiple domains and users`() {
        val original = ListClientsResponse(
            qualifiedUserMap = mapOf(
                "example.com" to mapOf(
                    "99db9768-04e3-4b5d-9268-831b6a25c4ab" to listOf(
                        UserClientResponse(id = "d0"),
                        UserClientResponse(id = "d1")
                    )
                ),
                "staging.zinfra.io" to mapOf(
                    "1d51e2d6-9c70-605f-efc8-ff85c3dabdc7" to listOf(
                        UserClientResponse(id = "d2")
                    )
                )
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ListClientsResponse>(encoded)

        assertEquals(original, decoded)
    }
}
