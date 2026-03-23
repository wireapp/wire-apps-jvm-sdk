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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.request.HttpRequestData
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

internal fun createMockHttpClient(
    responseBody: Any = "{}",
    statusCode: HttpStatusCode = HttpStatusCode.OK,
    assertRequest: (HttpRequestData) -> Unit = {}
): HttpClient =
    HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                assertRequest(request)

                when (responseBody) {
                    is String -> respond(
                        content = responseBody,
                        status = statusCode,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                    )

                    is ByteArray -> respond(
                        content = responseBody,
                        status = statusCode,
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                    )

                    else -> error("Unsupported response body type")
                }
            }
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
