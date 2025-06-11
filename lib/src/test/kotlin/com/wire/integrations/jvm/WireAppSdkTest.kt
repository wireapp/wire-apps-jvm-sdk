/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.integrations.jvm

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.wire.integrations.jvm.TestUtils.V
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.service.WireTeamEventsListener
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

class WireAppSdkTest {
    @Test
    fun koinModulesLoadCorrectly() {
        TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
        val wireAppSdk =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
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
            WireMock.get(WireMock.urlMatching("/$V/api-version")).willReturn(
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
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
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
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
                wireEventsHandler = object : WireEventsHandlerDefault() {
                    override fun onMessage(wireMessage: WireMessage.Text) {
                        println(wireMessage)
                    }
                }
            )

            val mockEventsListener = mockk<WireTeamEventsListener>()
            // Load our mock into Koin
            IsolatedKoinContext.koinApp.koin.loadModules(
                listOf(
                    module {
                        single { mockEventsListener }
                    }
                )
            )
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

            // Wait for the the last reconnect attempt to complete (with exception)
            val completed = latch.await(5, TimeUnit.SECONDS)
            assert(completed) { "Timed out waiting for connection attempts" }

            // Verify connect was called the expected number of times
            coVerify(atLeast = 3) { mockEventsListener.connect() }

            wireAppSdk.stopListening()
        }

    companion object {
        private val APPLICATION_ID = UUID.randomUUID()
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "http://localhost:8086"
        private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "dummyPassword"

        private val wireMockServer = WireMockServer(8086)

        @JvmStatic
        @BeforeAll
        fun before() {
            wireMockServer.start()
            IsolatedKoinContext.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
            IsolatedKoinContext.stop()
        }
    }
}
