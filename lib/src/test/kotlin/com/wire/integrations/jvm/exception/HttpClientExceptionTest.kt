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

package com.wire.integrations.jvm.exception

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.wire.integrations.jvm.TestUtils.V
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.utils.Mls
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.contentType
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class HttpClientExceptionTest {
    @Test
    fun whenEpochIsTooOldThenThrowMlsStaleMessageException() {
        val stubMlsMessagePath = "/$V/mls/messages"

        wireMockServer.stubFor(
            WireMock.post(WireMock.urlPathEqualTo(stubMlsMessagePath))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(
                            """
                            {
                              "code": 409,
                              "label": "mls-stale-message",
                              "message": "The conversation epoch in a message is too old"
                            }
                            """.trimIndent()
                        )
                )
        )
        val httpClient = IsolatedKoinContext.koinApp.koin.get<HttpClient>()

        runBlocking {
            val exception = assertThrows<WireException.ClientError> {
                httpClient.post(stubMlsMessagePath) {
                    setBody("test message".toByteArray())
                    contentType(Mls)
                }
            }
            assertTrue(exception.isMlsStaleMessage())
        }
    }

    @Test
    fun whenResponseIsSuccessfulThenNoExceptionIsThrown() {
        val stubMlsKeyPackagePath = "/$V/mls/key-packages/self/{appClientId}"
        wireMockServer.stubFor(
            WireMock.post(
                WireMock.urlPathEqualTo(stubMlsKeyPackagePath)
            ).willReturn(ok())
        )
        val httpClient = IsolatedKoinContext.koinApp.koin.get<HttpClient>()

        runBlocking {
            assertDoesNotThrow {
                httpClient.post(stubMlsKeyPackagePath)
            }
        }
    }

    @Test
    fun whenErrorResponseDoesNotContainLabelThenThrowUnknownException() {
        val stubMlsMessagePath = "/$V/mls/messages"

        wireMockServer.stubFor(
            WireMock.post(WireMock.urlPathEqualTo(stubMlsMessagePath))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(409)
                        .withHeader("Content-Type", "application/json;charset=utf-8")
                        .withBody(
                            """
                            {
                              "non_federating_backends": [
                                "example.com"
                              ]
                            }
                            """.trimIndent()
                        )
                )
        )
        val httpClient = IsolatedKoinContext.koinApp.koin.get<HttpClient>()

        runBlocking {
            assertThrows<WireException.UnknownError> {
                httpClient.post(stubMlsMessagePath) {
                    setBody("test message".toByteArray())
                    contentType(Mls)
                }
            }
        }
    }

    companion object {
        private val wireMockServer = WireMockServer(8086)

        @BeforeAll
        @JvmStatic
        fun before() {
            IsolatedKoinContext.start()
            IsolatedKoinContext.koinApp.koin.setProperty("API_HOST", "http://localhost:8086")
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
            IsolatedKoinContext.stop()
        }
    }
}
