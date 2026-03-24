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

import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamsApiClientTest {
    private fun apiClient(assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}) =
        TeamsApiClient(createMockHttpClient(assertRequest = assertRequest))

    @Test
    @DisplayName(
        "given teamId and conversationId, when deleteConversation is called, then correct URL is requested"
    )
    fun `requests-correct-url`() =
        runTest {
            var capturedPath: String? = null
            apiClient { capturedPath = it.url.encodedPath }
                .deleteConversation(TEAM_ID, CONVERSATION_ID)
            assertEquals(EXPECTED_PATH, capturedPath)
        }

    @Test
    fun `uses-delete-method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient { capturedMethod = it.method }
                .deleteConversation(TEAM_ID, CONVERSATION_ID)
            assertEquals(HttpMethod.Delete, capturedMethod)
        }

    companion object {
        private val TEAM_ID = TeamId(UUID.randomUUID())
        private val CONVERSATION_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = "example.com"
        )
        private val EXPECTED_PATH =
            "/teams/${TEAM_ID.value}/conversations/${CONVERSATION_ID.id}"
    }
}
