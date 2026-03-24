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

import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.persistence.AppStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import java.util.UUID

class ClientsApiClientTest {
    @Test
    fun `updateClientWithMlsPublicKey-puts-to-clients_deviceId`() =
        runTest {
            var capturedPath: String? = null
            var capturedMethod: HttpMethod? = null

            val client = HttpClient(MockEngine) {
                engine {
                    addHandler { request ->
                        capturedPath = request.url.fullPath
                        capturedMethod = request.method
                        respond(
                            content = "{}",
                            status = HttpStatusCode.OK,
                            headers = headersOf(
                                HttpHeaders.ContentType,
                                ContentType.Application.Json.toString()
                            )
                        )
                    }
                }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

            val deviceId = "device-123"
            val appStorage = mockk<AppStorage> {
                every { getDeviceId() } returns deviceId
            }

            val api = ClientsApiClient(client, appStorage)

            api.updateClientWithMlsPublicKey(
                CryptoClientId.create(
                    UUID.randomUUID(),
                    UUID.randomUUID().toString(),
                    "example.com"
                ),
                com.wire.sdk.model.http.MlsPublicKeys()
            )

            assertEquals("/clients/$deviceId", capturedPath)
            assertEquals(HttpMethod.Put, capturedMethod)
        }
}
