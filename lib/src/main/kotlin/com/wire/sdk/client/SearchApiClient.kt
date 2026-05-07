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

import com.wire.sdk.model.http.search.SearchContactsResponse
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.slf4j.LoggerFactory

internal class SearchApiClient(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val basePath = "search"

    private companion object {
        const val DEFAULT_RESULT_SIZE = 15
        const val MIN_RESULT_SIZE = 1
        const val MAX_RESULT_SIZE = 500
    }

    suspend fun searchUsers(
        query: String,
        domain: String? = null,
        numberOfResults: Int? = DEFAULT_RESULT_SIZE
    ): SearchContactsResponse {
        require(query.isNotBlank()) { "Search query must not be blank." }
        require(numberOfResults in MIN_RESULT_SIZE..MAX_RESULT_SIZE) {
            "Size must be between $MIN_RESULT_SIZE and $MAX_RESULT_SIZE."
        }

        logger.debug(
            "Searching users with query='{}', domain='{}', numberOfResults={}",
            query,
            domain,
            numberOfResults
        )

        return httpClient.get("/$basePath/contacts") {
            parameter("q", query)
            domain?.let { parameter("domain", it) }
            parameter("type", "regular") // Search users only, not apps.
            parameter("size", numberOfResults)
        }.body<SearchContactsResponse>()
    }
}
