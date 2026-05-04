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

import com.wire.sdk.model.QualifiedId
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ListClientsRequestTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `serializes qualified_users key correctly`() {
        val userId = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        val request = ListClientsRequest(
            qualifiedUsers = listOf(QualifiedId(id = userId, domain = "example.com"))
        )

        val result = json.encodeToString(request)

        assertTrue(result.contains("\"qualified_users\""))
    }

    @Test
    fun `serializes single user correctly`() {
        val userId = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        val request = ListClientsRequest(
            qualifiedUsers = listOf(QualifiedId(id = userId, domain = "example.com"))
        )

        val result = json.encodeToString(request)
        val expected =
            """{"qualified_users":[{"id":"99db9768-04e3-4b5d-9268-831b6a25c4ab","domain":"example.com"}]}"""

        assertEquals(expected, result)
    }

    @Test
    fun `serializes multiple users correctly`() {
        val userId1 = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        val userId2 = UUID.fromString("1d51e2d6-9c70-605f-efc8-ff85c3dabdc7")
        val request = ListClientsRequest(
            qualifiedUsers = listOf(
                QualifiedId(id = userId1, domain = "example.com"),
                QualifiedId(id = userId2, domain = "staging.zinfra.io")
            )
        )

        val result = json.encodeToString(request)
        val expected =
            """{"qualified_users":[{"id":"99db9768-04e3-4b5d-9268-831b6a25c4ab","domain":"example.com"},{"id":"1d51e2d6-9c70-605f-efc8-ff85c3dabdc7","domain":"staging.zinfra.io"}]}"""

        assertEquals(expected, result)
    }

    @Test
    fun `serializes empty user list correctly`() {
        val request = ListClientsRequest(qualifiedUsers = emptyList())

        val result = json.encodeToString(request)
        val expected = """{"qualified_users":[]}"""

        assertEquals(expected, result)
    }

    @Test
    fun `does not serialize as bare array`() {
        val userId = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        val request = ListClientsRequest(
            qualifiedUsers = listOf(QualifiedId(id = userId, domain = "example.com"))
        )

        val result = json.encodeToString(request)

        assertFalse(
            result.startsWith("["),
            "Body must not be a bare array — API expects an object with qualified_users key"
        )
    }

    @Test
    fun `deserializes single user correctly`() {
        val jsonString =
            """{"qualified_users":[{"id":"99db9768-04e3-4b5d-9268-831b6a25c4ab","domain":"example.com"}]}"""

        val result = json.decodeFromString<ListClientsRequest>(jsonString)

        assertEquals(1, result.qualifiedUsers.size)
        assertEquals(
            UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab"),
            result.qualifiedUsers[0].id
        )
        assertEquals("example.com", result.qualifiedUsers[0].domain)
    }

    @Test
    fun `deserializes multiple users correctly`() {
        val jsonString =
            """{"qualified_users":[{"id":"99db9768-04e3-4b5d-9268-831b6a25c4ab","domain":"example.com"},{"id":"1d51e2d6-9c70-605f-efc8-ff85c3dabdc7","domain":"staging.zinfra.io"}]}"""

        val result = json.decodeFromString<ListClientsRequest>(jsonString)

        assertEquals(2, result.qualifiedUsers.size)
        assertEquals("example.com", result.qualifiedUsers[0].domain)
        assertEquals("staging.zinfra.io", result.qualifiedUsers[1].domain)
    }

    @Test
    fun `deserializes empty user list correctly`() {
        val jsonString = """{"qualified_users":[]}"""

        val result = json.decodeFromString<ListClientsRequest>(jsonString)

        assertTrue(result.qualifiedUsers.isEmpty())
    }

    @Test
    fun `two requests with same users are equal`() {
        val userId = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        val request1 = ListClientsRequest(
            qualifiedUsers = listOf(QualifiedId(id = userId, domain = "example.com"))
        )
        val request2 = ListClientsRequest(
            qualifiedUsers = listOf(QualifiedId(id = userId, domain = "example.com"))
        )

        assertEquals(request1, request2)
    }

    @Test
    fun `two requests with different users are not equal`() {
        val request1 = ListClientsRequest(
            qualifiedUsers = listOf(
                QualifiedId(
                    id = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab"),
                    domain = "example.com"
                )
            )
        )
        val request2 = ListClientsRequest(
            qualifiedUsers = listOf(
                QualifiedId(
                    id = UUID.fromString("1d51e2d6-9c70-605f-efc8-ff85c3dabdc7"),
                    domain = "example.com"
                )
            )
        )

        assertNotEquals(request1, request2)
    }

    @Test
    fun `two requests with different domains are not equal`() {
        val userId = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        val request1 = ListClientsRequest(
            qualifiedUsers = listOf(QualifiedId(id = userId, domain = "example.com"))
        )
        val request2 = ListClientsRequest(
            qualifiedUsers = listOf(QualifiedId(id = userId, domain = "staging.zinfra.io"))
        )

        assertNotEquals(request1, request2)
    }

    @Test
    fun `serialization round-trip preserves data`() {
        val userId = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab")
        val original = ListClientsRequest(
            qualifiedUsers = listOf(
                QualifiedId(id = userId, domain = "example.com")
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ListClientsRequest>(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `serialization round-trip preserves multiple users`() {
        val original = ListClientsRequest(
            qualifiedUsers = listOf(
                QualifiedId(
                    id = UUID.fromString("99db9768-04e3-4b5d-9268-831b6a25c4ab"),
                    domain = "example.com"
                ),
                QualifiedId(
                    id = UUID.fromString("1d51e2d6-9c70-605f-efc8-ff85c3dabdc7"),
                    domain = "staging.zinfra.io"
                )
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<ListClientsRequest>(encoded)

        assertEquals(original, decoded)
    }
}
