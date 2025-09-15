/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.sdk.calling

import com.wire.sdk.utils.KtxSerializer
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

/**
 * HTTP client not connecting to Wire Backend but to the AVS servers directly.
 */
class CallingHttpClient(private val httpClient: HttpClient) {
    suspend fun getCallConfig(limit: Int?): String =
        httpClient.get("$PATH_CALLS/$PATH_CONFIG") {
            limit?.let { parameter(QUERY_KEY_LIMIT, it) }
        }.body<String>()

    suspend fun connectToSFT(
        url: String,
        data: String
    ): ByteArray =
        url.let {
            URLBuilder(it).apply {
                protocol = URLProtocol.HTTPS
            }
        }.build().let { parsedUrl ->
            httpClient.post(url = parsedUrl) {
                // We are parsing the data string to json due to Ktor serialization escaping
                // the string
                // and thus backend not recognizing and returning a 400 - Bad Request
                val json = KtxSerializer.json.parseToJsonElement(data)
                setBody(json)
            }
        }.body<ByteArray>()

    private companion object {
        const val PATH_CALLS = "calls"
        const val PATH_CONFIG = "config/v2"
        const val QUERY_KEY_LIMIT = "limit"
    }
}
