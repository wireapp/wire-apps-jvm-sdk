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

import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchApiClientTest {
    private fun apiClient(
        responseBody: String = SEARCH_RESPONSE_EMPTY,
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = SearchApiClient(createMockHttpClient(responseBody, assertRequest = assertRequest))

    @Test
    fun `when searchUsers, then correct URL path`() =
        runTest {
            var capturedPath: String? = null
            apiClient { capturedPath = it.url.encodedPath }.searchUsers(query = "Alice")
            assertEquals("/search/contacts", capturedPath)
        }

    @Test
    fun `when searchUsers, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient { capturedMethod = it.method }.searchUsers(query = "Alice")
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when searchUsers, then query parameter is set`() =
        runTest {
            var capturedParams: io.ktor.http.Parameters? = null
            apiClient { capturedParams = it.url.parameters }.searchUsers(query = "Alice")
            assertEquals("Alice", capturedParams?.get("q"))
        }

    @Test
    fun `when searchUsers with null numberOfResults, then size parameter is 15`() =
        runTest {
            var capturedParams: io.ktor.http.Parameters? = null
            apiClient { capturedParams = it.url.parameters }.searchUsers(
                query = "Alice",
                numberOfResults = null
            )
            assertEquals("15", capturedParams?.get("size"))
        }

    @Test
    fun `when searchUsers with domain, then domain parameter is set`() =
        runTest {
            var capturedParams: io.ktor.http.Parameters? = null
            apiClient { capturedParams = it.url.parameters }.searchUsers(
                query = "Alice",
                domain = "wire.com"
            )
            assertEquals("wire.com", capturedParams?.get("domain"))
        }

    @Test
    fun `when searchUsers without domain, then domain parameter is absent`() =
        runTest {
            var capturedParams: io.ktor.http.Parameters? = null
            apiClient {
                capturedParams = it.url.parameters
            }.searchUsers(query = "Alice", domain = null)
            assertEquals(false, capturedParams?.contains("domain"))
        }

    @Test
    fun `when searchUsers, then type parameter is always regular`() =
        runTest {
            var capturedParams: io.ktor.http.Parameters? = null
            apiClient { capturedParams = it.url.parameters }.searchUsers(query = "Alice")
            assertEquals("regular", capturedParams?.get("type"))
        }

    @Test
    fun `when searchUsers with custom numberOfResults, then size parameter is set`() =
        runTest {
            var capturedParams: io.ktor.http.Parameters? = null
            apiClient { capturedParams = it.url.parameters }.searchUsers(
                query = "Alice",
                numberOfResults = 42
            )
            assertEquals("42", capturedParams?.get("size"))
        }

    @Test
    fun `when searchUsers with default numberOfResults, then size parameter is 15`() =
        runTest {
            var capturedParams: io.ktor.http.Parameters? = null
            apiClient { capturedParams = it.url.parameters }.searchUsers(query = "Alice")
            assertEquals("15", capturedParams?.get("size"))
        }

    @Test
    fun `given blank query, when searchUsers, then throws IAException`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                apiClient().searchUsers(query = "   ")
            }
        }

    @Test
    fun `given empty query, when searchUsers, then throws IAException`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                apiClient().searchUsers(query = "")
            }
        }

    @Test
    fun `given numberOfResults below minimum, when searchUsers, then throws IAException`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                apiClient().searchUsers(query = "Alice", numberOfResults = 0)
            }
        }

    @Test
    fun `given numberOfResults above maximum, when searchUsers, then throws IAException`() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                apiClient().searchUsers(query = "Alice", numberOfResults = 501)
            }
        }

    @Test
    fun `given numberOfResults at minimum boundary, when searchUsers, then succeeds`() =
        runTest {
            apiClient().searchUsers(query = "Alice", numberOfResults = 1)
        }

    @Test
    fun `given numberOfResults at maximum boundary, when searchUsers, then succeeds`() =
        runTest {
            apiClient().searchUsers(query = "Alice", numberOfResults = 500)
        }

    @Test
    fun `given documents in response, when searchUsers, then documents deserialized`() =
        runTest {
            val result = apiClient(SEARCH_RESPONSE_WITH_RESULTS).searchUsers(query = "Alice")
            assertEquals(1, result.documents.size)
            assertEquals("Alice", result.documents.first().name)
            assertEquals("alice", result.documents.first().handle)
        }

    @Test
    fun `given document with qualifiedId, when searchUsers, then qualifiedId deserialized`() =
        runTest {
            val result = apiClient(SEARCH_RESPONSE_WITH_RESULTS).searchUsers(query = "Alice")
            assertEquals("wire.com", result.documents.first().qualifiedId?.domain)
            assertEquals(UUID.fromString(USER_ID), result.documents.first().qualifiedId?.id)
        }

    @Test
    fun `given hasMore true in response, when searchUsers, then hasMore is true`() =
        runTest {
            val result = apiClient(SEARCH_RESPONSE_WITH_RESULTS).searchUsers(query = "Alice")
            assertEquals(true, result.hasMore)
        }

    @Test
    fun `given empty documents in response, when searchUsers, then documents list is empty`() =
        runTest {
            val result = apiClient(SEARCH_RESPONSE_EMPTY).searchUsers(query = "Alice")
            assertEquals(0, result.documents.size)
        }

    @Test
    fun `given pagingState in response, when searchUsers, then pagingState deserialized`() =
        runTest {
            val result = apiClient(SEARCH_RESPONSE_WITH_RESULTS).searchUsers(query = "Alice")
            assertEquals("next-page-token", result.pagingState)
        }

    @Test
    fun `given null pagingState in response, when searchUsers, then pagingState is null`() =
        runTest {
            val result = apiClient(SEARCH_RESPONSE_EMPTY).searchUsers(query = "Alice")
            assertEquals(null, result.pagingState)
        }

    companion object {
        private const val USER_ID = "99db9768-04e3-4b5d-9268-831b6a25c4ab"

        private val SEARCH_RESPONSE_EMPTY = """
            {
              "documents": [],
              "found": 0,
              "has_more": false,
              "paging_state": null,
              "returned": 0,
              "search_policy": "full_search",
              "took": 5
            }
        """.trimIndent()

        private val SEARCH_RESPONSE_WITH_RESULTS = """
            {
              "documents": [
                {
                  "accent_id": 1,
                  "handle": "alice",
                  "id": "$USER_ID",
                  "name": "Alice",
                  "qualified_id": {
                    "domain": "wire.com",
                    "id": "$USER_ID"
                  },
                  "team": null,
                  "type": "regular"
                }
              ],
              "found": 1,
              "has_more": true,
              "paging_state": "next-page-token",
              "returned": 1,
              "search_policy": "full_search",
              "took": 12
            }
        """.trimIndent()
    }
}
