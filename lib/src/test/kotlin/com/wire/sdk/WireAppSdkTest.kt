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
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.WireMessage
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.service.WireTeamEventsListener
import com.wire.sdk.utils.obfuscateId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds

class WireAppSdkTest {
    @AfterEach
    fun tearDown() {
        unmockkObject(IsolatedKoinContext)
    }

    @Test
    fun koinModulesLoadCorrectly() {
        TestUtils.setupWireMockStubs(wireMockServer = wireMockServer)
        val wireAppSdk =
            WireAppSdk(
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStorageKey = TestUtils.CRYPTOGRAPHY_STORAGE_KEY,
                object : WireEventsHandlerDefault() {
                    override fun onTextMessageReceived(wireMessage: WireMessage.Text) {
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
            WireMock.get(
                WireMock.urlMatching("/${TestUtils.TEST_API_VERSION}/api-version")
            ).willReturn(
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
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStorageKey = TestUtils.CRYPTOGRAPHY_STORAGE_KEY,
                object : WireEventsHandlerDefault() {
                    override fun onTextMessageReceived(wireMessage: WireMessage.Text) {
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
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStorageKey = TestUtils.CRYPTOGRAPHY_STORAGE_KEY,
                wireEventsHandler = object : WireEventsHandlerDefault() {
                    override fun onTextMessageReceived(wireMessage: WireMessage.Text) {
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
                    1 -> delay(100.milliseconds)
                    2 -> delay(100.milliseconds)
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
            assert(completed) { "Timed out waiting for connection attempts" }

            // Verify connect was called the expected number of times
            coVerify(atLeast = 3) { mockEventsListener.connect() }

            wireAppSdk.stopListening()
        }

    @Test
    fun `given fresh storage, when sdk starts, then constructor token is stored for both`() {
        val appStorage = mockAppStorage(
            storedApiToken = null,
            storedBackendCookie = null
        )

        createWireAppSdk(apiToken = "")

        verify(exactly = 1) {
            appStorage.saveApiToken("")
            appStorage.saveBackendCookie("")
        }
    }

    @Test
    fun `given legacy cookie, when sdk starts, then token and cookie are replaced`() {
        val replacementToken = apiTokenForUser(TestUtils.APPLICATION_QUALIFIED_ID.id)
        val appStorage = mockAppStorage(
            storedApiToken = null,
            storedBackendCookie = "XYZ"
        )
        every {
            appStorage.getApplicationQualifiedId()
        } returns TestUtils.APPLICATION_QUALIFIED_ID

        createWireAppSdk(apiToken = replacementToken)

        verify(exactly = 1) {
            appStorage.saveApiToken(replacementToken)
            appStorage.saveBackendCookie(replacementToken)
        }
    }

    @Test
    fun `given matching startup token, when sdk starts, then nothing is saved`() {
        val appStorage = mockAppStorage(
            storedApiToken = "ABC",
            storedBackendCookie = "XYZ"
        )

        createWireAppSdk(apiToken = "ABC")

        verify(exactly = 0) {
            appStorage.saveApiToken(any())
            appStorage.saveBackendCookie(any())
        }
    }

    @Test
    fun `given changed token for same app, when sdk starts, then token and cookie are replaced`() {
        val replacementToken = apiTokenForUser(TestUtils.APPLICATION_QUALIFIED_ID.id)
        val appStorage = mockAppStorage(
            storedApiToken = "ABC",
            storedBackendCookie = "XYZ"
        )
        every {
            appStorage.getApplicationQualifiedId()
        } returns TestUtils.APPLICATION_QUALIFIED_ID

        createWireAppSdk(apiToken = replacementToken)

        verify(exactly = 1) {
            appStorage.saveApiToken(replacementToken)
            appStorage.saveBackendCookie(replacementToken)
        }
    }

    @Test
    fun `given startup token for another app, when sdk starts, then user must clear storage`() {
        val userId = UUID.fromString("b05e077d-7d5e-4f3f-b36c-30a0d18718a4")
        val replacementToken = apiTokenForUser(userId)
        val appStorage = mockAppStorage(
            storedApiToken = "ABC",
            storedBackendCookie = "XYZ"
        )
        every {
            appStorage.getApplicationQualifiedId()
        } returns TestUtils.APPLICATION_QUALIFIED_ID

        val exception = assertFailsWith<WireException.UnknownError> {
            createWireAppSdk(apiToken = replacementToken)
        }

        assertEquals(
            "Stored application QualifiedId ${TestUtils.APPLICATION_QUALIFIED_ID} " +
                "does not match App QualifiedId ${userId.obfuscateId()} " +
                "retrieved from the API token. Clear SDK storage before using a token for another app.",
            exception.message
        )
        verify(exactly = 0) {
            appStorage.saveApiToken(any())
            appStorage.saveBackendCookie(any())
        }
    }

    @Test
    fun `given changed token without user id, when sdk starts, then storage is not replaced`() {
        val appStorage = mockAppStorage(
            storedApiToken = "ABC",
            storedBackendCookie = "XYZ"
        )
        every {
            appStorage.getApplicationQualifiedId()
        } returns TestUtils.APPLICATION_QUALIFIED_ID

        assertFailsWith<WireException.UnknownError> {
            createWireAppSdk(apiToken = "DEF")
        }

        verify(exactly = 0) {
            appStorage.saveApiToken(any())
            appStorage.saveBackendCookie(any())
        }
    }

    private fun mockAppStorage(
        storedApiToken: String?,
        storedBackendCookie: String?
    ): AppStorage {
        val appStorage = mockk<AppStorage>()
        every { appStorage.getApiToken() } returns storedApiToken
        every { appStorage.getBackendCookie() } returns storedBackendCookie
        justRun { appStorage.saveApiToken(any()) }
        justRun { appStorage.saveBackendCookie(any()) }

        mockkObject(IsolatedKoinContext)
        every { IsolatedKoinContext.start() } answers {
            callOriginal()
            IsolatedKoinContext.koin.loadModules(
                listOf(
                    module {
                        single<AppStorage> { appStorage }
                    }
                )
            )
        }
        return appStorage
    }

    private fun createWireAppSdk(apiToken: String): WireAppSdk =
        WireAppSdk(
            apiToken = apiToken,
            apiHost = API_HOST,
            cryptographyStorageKey = TestUtils.CRYPTOGRAPHY_STORAGE_KEY,
            wireEventsHandler = object : WireEventsHandlerDefault() {}
        )

    // Real zauth tokens are signed dot-separated values, as documented in
    // https://github.com/wireapp/wire-server/blob/develop/libs/zauth/README.md.
    // These tests only need a token-shaped string with the u=<UUID> segment parsed by the SDK.
    private fun apiTokenForUser(userId: UUID): String = "zuid=token;u=$userId;wire_app=true"

    companion object {
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "http://localhost:8086"

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
