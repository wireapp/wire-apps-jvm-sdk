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
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelfApiClientTest {
    private fun apiClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = SelfApiClient(createMockHttpClient(responseBody, assertRequest = assertRequest))

    @Test
    fun `when getSelfUser, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(SELF_RESPONSE_WITH_TEAM) { capturedPath = it.url.encodedPath }.getSelfUser()
            assertEquals("/$API_VERSION/self", capturedPath)
        }

    @Test
    fun `when getSelfUser, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(SELF_RESPONSE_WITH_TEAM) { capturedMethod = it.method }.getSelfUser()
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `given teamId in response, when getSelfUser, then teamId deserialized`() =
        runTest {
            val result = apiClient(SELF_RESPONSE_WITH_TEAM).getSelfUser()
            assertEquals(TEAM_ID, result.teamId)
        }

    @Test
    fun `given null teamId in response, when getSelfUser, then teamId is null`() =
        runTest {
            val result = apiClient(SELF_RESPONSE_NO_TEAM).getSelfUser()
            assertNull(result.teamId)
        }

    companion object {
        private val TEAM_ID = UUID.randomUUID()
        private val SELF_RESPONSE_WITH_TEAM = """{ "team": "$TEAM_ID" }"""
        private val SELF_RESPONSE_NO_TEAM = """{ "team": null }"""
    }
}
