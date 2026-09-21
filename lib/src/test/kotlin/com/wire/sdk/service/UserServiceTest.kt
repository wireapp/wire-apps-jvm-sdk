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
import com.wire.sdk.model.UserType
import com.wire.sdk.model.http.search.ContactDocument
import com.wire.sdk.model.http.search.SearchContactsResponse
import com.wire.sdk.model.http.user.ListUsersResponse
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
        deleted: Boolean? = false,
        type: UserType? = UserType.REGULAR
    ) = UserResponse(
        id = id,
        name = name,
        email = email,
        handle = handle,
        teamId = teamId,
        accentId = accentId,
        supportedProtocols = supportedProtocols,
        deleted = deleted,
        type = type
    )

    private fun buildContactDocument(
        id: String = userId.toString(),
        name: String = "Alice",
        handle: String? = "alice",
        qualifiedId: QualifiedId = this.qualifiedId,
        team: String? = null
    ) = ContactDocument(
        accentId = null,
        handle = handle,
        id = id,
        name = name,
        qualifiedId = qualifiedId,
        team = team,
        type = "regular"
    )

    // =========================================================================
    // getUsers
    // =========================================================================

    @Test
    fun `getUsers delegates to UsersApiClient with the given userIds`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUsers(listOf(qualifiedId)) } returns
                ListUsersResponse(found = listOf(buildResponse()))
            val service = UserService(usersApiClient, mockk(relaxed = true))

            service.getUsers(listOf(qualifiedId))

            coVerify(exactly = 1) { usersApiClient.getUsers(listOf(qualifiedId)) }
        }

    @Test
    fun `getUsers maps found users`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUsers(any()) } returns
                ListUsersResponse(
                    found = listOf(
                        buildResponse(
                            id = qualifiedId,
                            name = "Bob",
                            email = "bob@example.com",
                            handle = "bob",
                            teamId = teamId,
                            deleted = true,
                            type = UserType.BOT
                        )
                    )
                )
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUsers(listOf(qualifiedId)).single()

            assertEquals(qualifiedId, user.id)
            assertEquals("Bob", user.name)
            assertEquals("bob@example.com", user.email)
            assertEquals("bob", user.handle)
            assertEquals(teamId, user.teamId)
            assertEquals(true, user.deleted)
            assertEquals(UserType.BOT, user.type)
        }

    @Test
    fun `getUsers preserves nullable fields`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUsers(any()) } returns
                ListUsersResponse(
                    found = listOf(
                        buildResponse(
                            email = null,
                            handle = null,
                            teamId = null,
                            deleted = null,
                            type = null
                        )
                    )
                )
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val user = service.getUsers(listOf(qualifiedId)).single()

            assertNull(user.email)
            assertNull(user.handle)
            assertNull(user.teamId)
            assertNull(user.deleted)
            assertNull(user.type)
        }

    @Test
    fun `getUsers returns only found users`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            coEvery { usersApiClient.getUsers(any()) } returns
                ListUsersResponse(found = emptyList(), failed = listOf(qualifiedId))
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val users = service.getUsers(listOf(qualifiedId))

            assertTrue(users.isEmpty())
        }

    @Test
    fun `getUsers returns empty list without backend call for empty input`() =
        runTest {
            val usersApiClient = mockk<UsersApiClient>()
            val service = UserService(usersApiClient, mockk(relaxed = true))

            val users = service.getUsers(emptyList())

            assertTrue(users.isEmpty())
            coVerify(exactly = 0) { usersApiClient.getUsers(any()) }
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
