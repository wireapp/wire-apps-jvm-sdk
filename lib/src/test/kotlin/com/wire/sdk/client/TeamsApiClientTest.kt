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
import com.wire.sdk.model.TeamId
import io.ktor.http.HttpMethod
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamsApiClientTest {
    private val teamId = TeamId(UUID.fromString("11111111-2222-3333-4444-555555555555"))
    private val conversationId = QualifiedId(
        id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
        domain = "example.com"
    )

    private val expectedPath = "/$API_VERSION/teams/${teamId.value}" +
        "/conversations/${conversationId.id}"

    @Test
    @DisplayName(
        "given teamId and conversationId, when deleteConversation is called, then correct URL is requested"
    )
    fun `requests-correct-url`() =
        runTest {
            var capturedPath: String? = null
            val client = createMockHttpClient(assertRequest = { capturedPath = it.url.fullPath })

            TeamsApiClient(client).deleteConversation(teamId, conversationId)

            assertEquals(expectedPath, capturedPath)
        }

    @Test
    fun `uses-delete-method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            val client = createMockHttpClient(assertRequest = { capturedMethod = it.method })

            TeamsApiClient(client).deleteConversation(teamId, conversationId)

            assertEquals(HttpMethod.Delete, capturedMethod)
        }
}
