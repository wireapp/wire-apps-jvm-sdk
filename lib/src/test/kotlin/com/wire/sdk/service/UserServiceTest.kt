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

import com.wire.sdk.client.SearchApiClient
import com.wire.sdk.client.UsersApiClient
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireUser
import com.wire.sdk.model.http.search.ContactDocument
import com.wire.sdk.model.http.search.SearchContactsResponse
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

    // --- Helpers ---

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

    private fun buildContactDocument(
        id: String = userId.toString(),
        name: String = "Alice",
        handle: String? = "alice",
        qualifiedId: QualifiedId? = this.qualifiedId,
        team: String? = null
    ) = ContactDocument(
        accentId = null,
        handle = handle,
        id = id,
        name = name,
        qualifiedId = qualifiedId,
        team = team,
        type = null
    )

    // =========================================================================
    // getUser
    // =========================================================================

    @Test
    fun `getUser delegates to UsersApiClient with the given userId`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(qualifiedId) } returns buildResponse()
            val service = UserService(usersApiClient, mockk(relaxed = true))

            service.getUser(qualifiedId)

            coVerify(exactly = 1) { usersApiClient.getUserData(qualifiedId) }
        }

    @Test
    fun `getUser maps id correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(id = qualifiedId)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals(qualifiedId, user.id)
        }

    @Test
    fun `getUser maps name correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(name = "Bob")
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals("Bob", user.name)
        }

    @Test
    fun `getUser maps supportedProtocols correctly`() =
        runTest {
            val protocols = listOf(CryptoProtocol.MLS, CryptoProtocol.PROTEUS)
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns
                buildResponse(supportedProtocols = protocols)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals(protocols, user.supportedProtocols)
        }

    @Test
    fun `getUser maps non-null email correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns
                buildResponse(email = "alice@example.com")
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals("alice@example.com", user.email)
        }

    @Test
    fun `getUser maps non-null handle correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(handle = "alice")
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals("alice", user.handle)
        }

    @Test
    fun `getUser maps non-null teamId correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(teamId = teamId)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals(teamId, user.teamId)
        }

    @Test
    fun `getUser maps non-null deleted true correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(deleted = true)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals(true, user.deleted)
        }

    @Test
    fun `getUser maps non-null deleted false correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(deleted = false)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertEquals(false, user.deleted)
        }

    @Test
    fun `getUser passes null email through as null`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(email = null)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertNull(user.email)
        }

    @Test
    fun `getUser passes null handle through as null`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(handle = null)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertNull(user.handle)
        }

    @Test
    fun `getUser passes null teamId through as null`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(teamId = null)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertNull(user.teamId)
        }

    @Test
    fun `getUser passes null deleted through as null`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse(deleted = null)
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertNull(user.deleted)
        }

    @Test
    fun `getUser returns a WireUser instance`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUserData(any()) } returns buildResponse()
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUser(qualifiedId)

            assertTrue(user is WireUser)
        }

    // =========================================================================
    // searchUsers
    // =========================================================================

    @Test
    fun `searchUsers delegates to SearchApiClient with correct parameters`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery {
                searchApiClient.searchUsers(
                    query = "Alice",
                    domain = domain,
                    numberOfResults = 10
                )
            } returns SearchContactsResponse(documents = listOf(buildContactDocument()))
            val service = UserService(usersApiClient, searchApiClient)

            service.searchUsers(query = "Alice", domain = domain, numberOfResults = 10)

            coVerify(exactly = 1) {
                searchApiClient.searchUsers(query = "Alice", domain = domain, numberOfResults = 10)
            }
        }

    @Test
    fun `searchUsers maps qualifiedId to WireUser id`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery { searchApiClient.searchUsers(any(), any(), any()) } returns
                SearchContactsResponse(
                    documents = listOf(buildContactDocument(qualifiedId = qualifiedId))
                )
            val service = UserService(usersApiClient, searchApiClient)

            val result = service.searchUsers(
                query = "Alice",
                domain = domain,
                numberOfResults = null
            )

            assertEquals(qualifiedId, result.first().id)
        }

    @Test
    fun `searchUsers falls back to bare id with empty domain when qualifiedId is null`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery { searchApiClient.searchUsers(any(), any(), any()) } returns
                SearchContactsResponse(
                    documents = listOf(buildContactDocument(qualifiedId = null))
                )
            val service = UserService(usersApiClient, searchApiClient)

            val result = service.searchUsers(
                query = "Alice",
                domain = domain,
                numberOfResults = null
            )

            assertEquals(userId, result.first().id.id)
            assertEquals("", result.first().id.domain)
        }

    @Test
    fun `searchUsers maps name and handle correctly`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery { searchApiClient.searchUsers(any(), any(), any()) } returns
                SearchContactsResponse(
                    documents = listOf(buildContactDocument(name = "Bob", handle = "bob"))
                )
            val service = UserService(usersApiClient, searchApiClient)

            val result = service.searchUsers(query = "Bob", domain = domain, numberOfResults = null)

            assertEquals("Bob", result.first().name)
            assertEquals("bob", result.first().handle)
        }

    @Test
    fun `searchUsers parses team as UUID for teamId`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery { searchApiClient.searchUsers(any(), any(), any()) } returns
                SearchContactsResponse(
                    documents = listOf(buildContactDocument(team = teamId.toString()))
                )
            val service = UserService(usersApiClient, searchApiClient)

            val result = service.searchUsers(
                query = "Alice",
                domain = domain,
                numberOfResults = null
            )

            assertEquals(teamId, result.first().teamId)
        }

    @Test
    fun `searchUsers sets teamId to null when team is null`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery { searchApiClient.searchUsers(any(), any(), any()) } returns
                SearchContactsResponse(
                    documents = listOf(buildContactDocument(team = null))
                )
            val service = UserService(usersApiClient, searchApiClient)

            val result = service.searchUsers(
                query = "Alice",
                domain = domain,
                numberOfResults = null
            )

            assertNull(result.first().teamId)
        }

    @Test
    fun `searchUsers sets email deleted and supportedProtocols to null or empty`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery { searchApiClient.searchUsers(any(), any(), any()) } returns
                SearchContactsResponse(
                    documents = listOf(buildContactDocument())
                )
            val service = UserService(usersApiClient, searchApiClient)

            val result = service.searchUsers(
                query = "Alice",
                domain = domain,
                numberOfResults = null
            )

            assertNull(result.first().email)
            assertNull(result.first().deleted)
            assertTrue(result.first().supportedProtocols.isEmpty())
        }

    @Test
    fun `searchUsers returns empty list when documents are empty`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>(relaxed = true)
            val searchApiClient = mockk<SearchApiClient>()
            coEvery { searchApiClient.searchUsers(any(), any(), any()) } returns
                SearchContactsResponse(
                    documents = emptyList()
                )
            val service = UserService(usersApiClient, searchApiClient)

            val result = service.searchUsers(
                query = "Alice",
                domain = domain,
                numberOfResults = null
            )

            assertTrue(result.isEmpty())
        }
}
