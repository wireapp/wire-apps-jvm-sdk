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
import com.wire.sdk.persistence.AppStorage
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertFailsWith

class AuthTokenManagerTest {
    private val appStorage = mockk<AppStorage>()
    private val httpClient = createHttpClient(wireMockServer.baseUrl(), mockk())

    @BeforeEach
    fun resetStubs() {
        wireMockServer.resetAll()
        every { appStorage.getBackendCookie() } returns "cookie"
        every { appStorage.getDeviceId() } returns "device"
        justRun { appStorage.deleteBackendCookie() }
        justRun { appStorage.deleteDeviceId() }
    }

    @Test
    fun `when access returns invalid-credentials, then cookie and deviceId are deleted`() =
        runTest {
            stubAccess(label = "invalid-credentials")
            val authTokenManager = AuthTokenManager(appStorage)

            assertFailsWith<IllegalStateException> {
                authTokenManager.refreshAccessToken(refreshParams())
            }

            verify(exactly = 1) { appStorage.deleteBackendCookie() }
            verify(exactly = 1) { appStorage.deleteDeviceId() }
        }

    @Test
    fun `when access returns a non credentials 403, then nothing is deleted`() =
        runTest {
            stubAccess(label = "operation-denied")
            val authTokenManager = AuthTokenManager(appStorage)

            assertFailsWith<IllegalStateException> {
                authTokenManager.refreshAccessToken(refreshParams())
            }

            verify(exactly = 0) { appStorage.deleteBackendCookie() }
            verify(exactly = 0) { appStorage.deleteDeviceId() }
        }

    private fun stubAccess(label: String) {
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/$TEST_API_VERSION/access"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"code":403,"label":"$label","message":"forbidden"}""")
                )
        )
    }

    // relaxed = true so the member-extension markAsRefreshTokenRequest() is a no-op in the test
    private fun refreshParams(): RefreshTokensParams =
        mockk<RefreshTokensParams>(relaxed = true).also {
            every { it.client } returns httpClient
        }

    companion object {
        private val wireMockServer = WireMockServer(8087)

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
}
