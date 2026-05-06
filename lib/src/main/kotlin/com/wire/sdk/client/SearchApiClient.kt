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
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import org.slf4j.LoggerFactory

internal class SearchApiClient(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val basePath = "search"

    private companion object {
        const val QUERY_KEY = "q"
        const val DOMAIN_KEY = "domain"
        const val SIZE_KEY = "size"
        const val TYPE_KEY = "type"

        const val DEFAULT_SIZE = 15
        const val MIN_SIZE = 1
        const val MAX_SIZE = 500
    }

    suspend fun searchContacts(
        query: String,
        domain: String? = null,
        size: Int = DEFAULT_SIZE,
        type: String? = null
    ): SearchContactsResponse {
        require(query.isNotBlank()) { "Search query must not be blank." }
        require(size in MIN_SIZE..MAX_SIZE) { "Size must be between $MIN_SIZE and $MAX_SIZE." }

        logger.debug(
            "Searching contacts with query='{}', domain='{}', size={}, type={}",
            query, domain, size, type
        )

        return httpClient.get("/$basePath/contacts") {
            parameter(QUERY_KEY, query)
            domain?.let { parameter(DOMAIN_KEY, it) }
            type?.let { parameter(TYPE_KEY, it) }
            parameter(SIZE_KEY, size)
        }.body<SearchContactsResponse>()
    }
}
