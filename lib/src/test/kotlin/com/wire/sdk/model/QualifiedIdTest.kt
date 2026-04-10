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

package com.wire.sdk.model

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class QualifiedIdTest {

    private val uuid1: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
    private val uuid2: UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174999")

    private val domainA = "domainA"
    private val domainB = "domainB"

    // --- Equality & HashCode ---

    @Test
    fun `should be equal when id and domain are same`() {
        val a = QualifiedId(uuid1, domainA)
        val b = QualifiedId(uuid1, domainA)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `should not be equal when id is different`() {
        val a = QualifiedId(uuid1, domainA)
        val b = QualifiedId(uuid2, domainA)

        assertNotEquals(a, b)
    }

    @Test
    fun `should not be equal when domain is different`() {
        val a = QualifiedId(uuid1, domainA)
        val b = QualifiedId(uuid1, domainB)

        assertNotEquals(a, b)
    }

    @Test
    fun `should not be equal to null or other type`() {
        val a = QualifiedId(uuid1, domainA)

        assertNotEquals(a, null)
        assertNotEquals(a, "some-string")
    }

    // --- toString() (obfuscation) ---

    @Test
    fun `toString should return obfuscated id with domain`() {
        val qualifiedId = QualifiedId(uuid1, domainA)

        val result = qualifiedId.toString()

        assertTrue(result.contains("@$domainA"))
        assertFalse(result.contains(uuid1.toString())) // critical: must not leak raw UUID
    }

    // --- toFullString() ---

    @Test
    fun `toFullString should return full id and domain`() {
        val qualifiedId = QualifiedId(uuid1, domainA)

        val result = qualifiedId.toFullString()

        assertEquals("${uuid1}@$domainA", result)
    }

    // --- Serialization ---

    @Test
    fun `should serialize to expected json`() {
        val qualifiedId = QualifiedId(uuid1, domainA)

        val json = Json.encodeToString(QualifiedId.serializer(), qualifiedId)

        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"domain\""))
        assertTrue(json.contains(domainA))
    }

    @Test
    fun `should deserialize from json`() {
        val json = """
            {
                "id": "$uuid1",
                "domain": "$domainA"
            }
        """.trimIndent()

        val result = Json.decodeFromString(QualifiedId.serializer(), json)

        assertEquals(uuid1, result.id)
        assertEquals(domainA, result.domain)
    }

    @Test
    fun `serialization round trip should preserve object`() {
        val original = QualifiedId(uuid1, domainA)

        val json = Json.encodeToString(QualifiedId.serializer(), original)
        val deserialized = Json.decodeFromString(QualifiedId.serializer(), json)

        assertEquals(original, deserialized)
    }
}
