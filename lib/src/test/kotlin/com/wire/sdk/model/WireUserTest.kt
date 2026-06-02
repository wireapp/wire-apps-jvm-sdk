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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

class WireUserTest {
    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val teamId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val domain = "example.com"

    private fun buildUser(
        id: QualifiedId = QualifiedId(userId, domain),
        name: String = "Alice",
        email: String? = "alice@example.com",
        handle: String? = "alice",
        teamId: UUID? = this.teamId,
        deleted: Boolean? = false
    ) = WireUser(
        id = id,
        name = name,
        email = email,
        handle = handle,
        teamId = teamId,
        deleted = deleted
    )

    // --- Construction ---

    @Test
    fun `should store all fields correctly`() {
        val qualifiedId = QualifiedId(userId, domain)
        val user = buildUser(id = qualifiedId)

        assertEquals(qualifiedId, user.id)
        assertEquals("Alice", user.name)
        assertEquals("alice@example.com", user.email)
        assertEquals("alice", user.handle)
        assertEquals(teamId, user.teamId)
        assertEquals(false, user.deleted)
    }

    @Test
    fun `should allow null email`() {
        val user = buildUser(email = null)

        assertNull(user.email)
    }

    @Test
    fun `should allow null handle`() {
        val user = buildUser(handle = null)

        assertNull(user.handle)
    }

    @Test
    fun `should allow null teamId`() {
        val user = buildUser(teamId = null)

        assertNull(user.teamId)
    }

    @Test
    fun `should allow null deleted`() {
        val user = buildUser(deleted = null)

        assertNull(user.deleted)
    }

    @Test
    fun `should allow deleted to be true`() {
        val user = buildUser(deleted = true)

        assertEquals(true, user.deleted)
    }

    // --- Equality ---

    @Test
    fun `two users with same fields should be equal`() {
        val userA = buildUser()
        val userB = buildUser()

        assertEquals(userA, userB)
    }

    @Test
    fun `two users with same fields should have same hashCode`() {
        val userA = buildUser()
        val userB = buildUser()

        assertEquals(userA.hashCode(), userB.hashCode())
    }

    @Test
    fun `two users with different ids should not be equal`() {
        val userA = buildUser(id = QualifiedId(userId, domain))
        val userB = buildUser(id = QualifiedId(UUID.randomUUID(), domain))

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with different names should not be equal`() {
        val userA = buildUser(name = "Alice")
        val userB = buildUser(name = "Bob")

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with different emails should not be equal`() {
        val userA = buildUser(email = "alice@example.com")
        val userB = buildUser(email = "bob@example.com")

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with null and non-null email should not be equal`() {
        val userA = buildUser(email = "alice@example.com")
        val userB = buildUser(email = null)

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with different handles should not be equal`() {
        val userA = buildUser(handle = "alice")
        val userB = buildUser(handle = "bob")

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with different teamIds should not be equal`() {
        val userA = buildUser(teamId = UUID.randomUUID())
        val userB = buildUser(teamId = UUID.randomUUID())

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with null and non-null teamId should not be equal`() {
        val userA = buildUser(teamId = UUID.randomUUID())
        val userB = buildUser(teamId = null)

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with different deleted flags should not be equal`() {
        val userA = buildUser(deleted = false)
        val userB = buildUser(deleted = true)

        assertNotEquals(userA, userB)
    }

    @Test
    fun `two users with null and non-null deleted should not be equal`() {
        val userA = buildUser(deleted = false)
        val userB = buildUser(deleted = null)

        assertNotEquals(userA, userB)
    }

    // --- Copy ---

    @Test
    fun `copy should produce equal object when no fields changed`() {
        val user = buildUser()
        val copy = user.copy()

        assertEquals(user, copy)
    }

    @Test
    fun `copy with changed name should differ from original`() {
        val user = buildUser(name = "Alice")
        val updated = user.copy(name = "Bob")

        assertNotEquals(user, updated)
        assertEquals("Bob", updated.name)
        assertEquals(user.id, updated.id)
    }

    @Test
    fun `copy with deleted true should reflect updated flag`() {
        val user = buildUser(deleted = false)
        val deletedUser = user.copy(deleted = true)

        assertEquals(false, user.deleted)
        assertEquals(true, deletedUser.deleted)
    }

    @Test
    fun `copy with null email should reflect null`() {
        val user = buildUser(email = "alice@example.com")
        val updated = user.copy(email = null)

        assertNull(updated.email)
        assertEquals("alice@example.com", user.email)
    }
}
