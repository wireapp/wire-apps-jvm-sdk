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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.wire.sdk.TestUtils.TEST_API_VERSION
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.config.createHttpClient
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.get
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import kotlin.test.Test

class HttpClientTest {
    val authTokenManager = mockk<AuthTokenManager>()
    private val httpClient = createHttpClient(
        "${wireMockServer.baseUrl()}/$TEST_API_VERSION",
        authTokenManager
    )

    companion object {
        private val wireMockServer = WireMockServer(8086)

        @JvmStatic
        @BeforeAll
        fun before() {
            IsolatedKoinContext.start()
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
            IsolatedKoinContext.stop()
        }
    }

    @Test
    fun `when concurrent unauthorized request, then should refresh access token only once`() =
        runTest {
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/$TEST_API_VERSION/authorized-endpoint"))
                    .willReturn(
                        WireMock.aResponse().withStatus(401)
                    )
            )
            wireMockServer.stubFor(
                WireMock.get(WireMock.urlPathEqualTo("/$TEST_API_VERSION/authorized-endpoint"))
                    .withHeader("Authorization", WireMock.equalTo("Bearer refreshed-token"))
                    .willReturn(
                        WireMock.aResponse().withStatus(200)
                    )
            )

            coEvery {
                authTokenManager.refreshAccessToken(any())
            } returns BearerTokens("refreshed-token", null)

            awaitAll(
                async { httpClient.get("/authorized-endpoint") },
                async { httpClient.get("/authorized-endpoint") }
            )

            coVerify(exactly = 1) { authTokenManager.refreshAccessToken(any()) }
        }
}
