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

package com.wire.sdk.service

import com.wire.sdk.client.UsersApiClient
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireUser
import com.wire.sdk.model.http.user.UserResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserServiceTest {

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val teamId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val domain = "example.com"
    private val qualifiedId = QualifiedId(userId, domain)

    private fun buildResponse(
        id: QualifiedId = qualifiedId,
        name: String = "Alice",
        email: String? = "alice@example.com",
        handle: String? = "alice",
        teamId: UUID? = this.teamId,
        accentId: Long = 3L,
        supportedProtocols: List<CryptoProtocol> = listOf(CryptoProtocol.PROTEUS),
        deleted: Boolean? = false
    ) = UserResponse(
        id = id,
        name = name,
        email = email,
        handle = handle,
        teamId = teamId,
        accentId = accentId,
        supportedProtocols = supportedProtocols,
        deleted = deleted
    )

    private fun buildService(response: UserResponse): UserService {
        val usersApiClient = mockk<UsersApiClient>()
        coEvery { usersApiClient.getUserData(any()) } returns response
        return UserService(usersApiClient)
    }

    // --- Delegation to ApiClient ---

    @Test
    fun `getUser delegates to UsersApiClient with the given userId`() = runTest {
        val usersApiClient = mockk<UsersApiClient>()
        coEvery { usersApiClient.getUserData(qualifiedId) } returns buildResponse()
        val service = UserService(usersApiClient)

        service.getUser(qualifiedId)

        coVerify(exactly = 1) { usersApiClient.getUserData(qualifiedId) }
    }

    // --- Mapping: non-nullable fields ---

    @Test
    fun `getUser maps id correctly`() = runTest {
        val user = buildService(buildResponse(id = qualifiedId)).getUser(qualifiedId)

        assertEquals(qualifiedId, user.id)
    }

    @Test
    fun `getUser maps name correctly`() = runTest {
        val user = buildService(buildResponse(name = "Bob")).getUser(qualifiedId)

        assertEquals("Bob", user.name)
    }

    @Test
    fun `getUser maps supportedProtocols correctly`() = runTest {
        val protocols = listOf(CryptoProtocol.MLS, CryptoProtocol.PROTEUS)
        val user = buildService(buildResponse(supportedProtocols = protocols)).getUser(qualifiedId)

        assertEquals(protocols, user.supportedProtocols)
    }

    // --- Mapping: present nullable fields ---

    @Test
    fun `getUser maps non-null email correctly`() = runTest {
        val user = buildService(buildResponse(email = "alice@example.com")).getUser(qualifiedId)

        assertEquals("alice@example.com", user.email)
    }

    @Test
    fun `getUser maps non-null handle correctly`() = runTest {
        val user = buildService(buildResponse(handle = "alice")).getUser(qualifiedId)

        assertEquals("alice", user.handle)
    }

    @Test
    fun `getUser maps non-null teamId correctly`() = runTest {
        val user = buildService(buildResponse(teamId = teamId)).getUser(qualifiedId)

        assertEquals(teamId, user.teamId)
    }

    @Test
    fun `getUser maps non-null deleted true correctly`() = runTest {
        val user = buildService(buildResponse(deleted = true)).getUser(qualifiedId)

        assertEquals(true, user.deleted)
    }

    @Test
    fun `getUser maps non-null deleted false correctly`() = runTest {
        val user = buildService(buildResponse(deleted = false)).getUser(qualifiedId)

        assertEquals(false, user.deleted)
    }

    // --- Mapping: null fields are passed through as null ---

    @Test
    fun `getUser passes null email through as null`() = runTest {
        val user = buildService(buildResponse(email = null)).getUser(qualifiedId)

        assertNull(user.email)
    }

    @Test
    fun `getUser passes null handle through as null`() = runTest {
        val user = buildService(buildResponse(handle = null)).getUser(qualifiedId)

        assertNull(user.handle)
    }

    @Test
    fun `getUser passes null teamId through as null`() = runTest {
        val user = buildService(buildResponse(teamId = null)).getUser(qualifiedId)

        assertNull(user.teamId)
    }

    @Test
    fun `getUser passes null deleted through as null`() = runTest {
        val user = buildService(buildResponse(deleted = null)).getUser(qualifiedId)

        assertNull(user.deleted)
    }

    // --- Return type ---

    @Test
    fun `getUser returns a WireUser instance`() = runTest {
        val user = buildService(buildResponse()).getUser(qualifiedId)

        assertTrue(user is WireUser)
    }
}
