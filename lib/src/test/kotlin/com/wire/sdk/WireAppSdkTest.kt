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

package com.wire.sdk

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.wire.crypto.ClientId
import com.wire.sdk.calling.CallManager
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.service.WireTeamEventsListener
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WireAppSdkTest {
    @Test
    fun koinModulesLoadCorrectly() {
        TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
        val wireAppSdk =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = TestUtils.CRYPTOGRAPHY_STORAGE_PASSWORD,
                object : WireEventsHandlerDefault() {
                    override fun onMessage(wireMessage: WireMessage.Text) {
                        println(wireMessage)
                    }
                }
            )
        assertNotNull(wireAppSdk.getApplicationManager(), "Koin dependency injection failed")
    }

    @Test
    fun fetchingApiVersionWithWireMockReturnsDummyData() {
        TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/${TestUtils.V}/api-version")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "development": [8],
                        "domain": "host.com",
                        "federation": true,
                        "supported": [1,2,3,4,5,6,7]
                    }
                    """.trimIndent()
                )
            )
        )

        val wireAppSdk =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = TestUtils.CRYPTOGRAPHY_STORAGE_PASSWORD,
                object : WireEventsHandlerDefault() {
                    override fun onMessage(wireMessage: WireMessage.Text) {
                        println(wireMessage)
                    }
                }
            )
        val appMetadata = wireAppSdk.getApplicationManager().getBackendConfiguration()
        assertEquals("host.com", appMetadata.domain)
    }

    @Test
    fun `connect is called multiple times after exceptions`() =
        runTest {
            TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)

            // Create the SDK instance
            val wireAppSdk = WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = TestUtils.CRYPTOGRAPHY_STORAGE_PASSWORD,
                wireEventsHandler = object : WireEventsHandlerDefault() {
                    override fun onMessage(wireMessage: WireMessage.Text) {
                        println(wireMessage)
                    }
                }
            )

            val mockEventsListener = mockk<WireTeamEventsListener>()
            val listenerModule = module { single { mockEventsListener } }
            // Load our mock into Koin
            IsolatedKoinContext.koin.loadModules(listOf(modules, listenerModule))
            var callCount = 0
            val latch = CountDownLatch(1)

            // Update the mock to count connections and signal the latch
            coEvery { mockEventsListener.connect() } coAnswers {
                callCount++
                when (callCount) {
                    1 -> delay(100)
                    2 -> delay(100)
                    else -> {
                        latch.countDown()
                        throw InterruptedException("Simulated network error")
                    }
                }
            }

            // Start listening
            wireAppSdk.startListening()

            // Wait for the last reconnect attempt to complete (with exception)
            val completed = latch.await(5, TimeUnit.SECONDS)
            assertTrue(completed, "Timed out waiting for connection attempts")

            // Verify connect was called the expected number of times
            coVerify(atLeast = 3) { mockEventsListener.connect() }

            wireAppSdk.stopListening()
        }

    companion object {
        private val APPLICATION_ID = UUID.randomUUID()
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "http://localhost:8086"

        private val wireMockServer = WireMockServer(8086)
        private val modules =
            module {
                single<CallManager> {
                    object : CallManager {
                        override suspend fun endCall(conversationId: QualifiedId) {}

                        override suspend fun reportProcessNotifications(isStarted: Boolean) {}

                        override fun cancelJobs() {}

                        override suspend fun onCallingMessageReceived(
                            message: WireMessage.Calling,
                            senderClient: ClientId
                        ) {
                        }
                    }
                }
            }

        @JvmStatic
        @BeforeAll
        fun before() {
            wireMockServer.start()

            IsolatedKoinContext.start()
            IsolatedKoinContext.koin.loadModules(listOf(modules))
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
            IsolatedKoinContext.stop()
        }
    }
}
