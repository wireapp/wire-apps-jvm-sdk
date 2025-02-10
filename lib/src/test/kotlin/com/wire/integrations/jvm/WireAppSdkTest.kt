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
import com.github.tomakehurst.wiremock.client.WireMock.okForContentType
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.service.WireApplicationManager
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.core.Koin
import org.koin.test.KoinTest
import org.koin.test.junit5.mock.MockProviderExtension
import org.koin.test.mock.declareMock
import org.mockito.Mockito
import org.mockito.Mockito.atLeast
import java.util.UUID
import kotlin.test.assertEquals

class WireAppSdkTest : KoinTest {
    // Override the Koin instance as we use an isolated context
    override fun getKoin(): Koin = IsolatedKoinContext.koinApp.koin

    @JvmField
    @RegisterExtension
    val mockProvider = MockProviderExtension.create { clazz ->
        Mockito.mock(clazz.java)
    }

    // Mockito matcher, tweaked to work in Kotlin non-nullable types
    private fun <T> any(type: Class<T>): T = Mockito.any<T>(type)

    @Test
    fun koinModulesLoadCorrectly() {
        val wireAppSdk =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
                object : WireEventsHandler() {
                    override fun onEvent(event: String) {
                        println(event)
                    }
                }
            )
        assertNotNull(wireAppSdk.getTeamManager(), "Koin dependency injection failed")
    }

    @Test
    fun fetchingApplicationDataWithWireMockReturnsDummyData() {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/v7/app-data")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "app_type": "dummyAppType",
                        "app_command": "dummyAppCommand"
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/v7/api-version")).willReturn(
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
                object : WireEventsHandler() {
                    override fun onEvent(event: String) {
                        println(event)
                    }
                }
            )
        val result = wireAppSdk.getTeamManager().fetchApplicationData()
        assertEquals("dummyAppType", result.appType)
        val appMetadata = wireAppSdk.getTeamManager().getApplicationMetadata()
        assertEquals("host.com", appMetadata.domain)
    }

    @Test
    fun fetchingSseEvents() {
        // Override the WireApplicationManager from WireAppSdk with a mock
        val mock = declareMock<WireApplicationManager>()

        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/apps/teams/await")).willReturn(
                okForContentType("text/event-stream", getRandomEventStream())
            )
        )

        val wireAppSdk =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
                object : WireEventsHandler() {
                    override fun onEvent(event: String) {
                        println(event)
                    }
                }
            )
        wireAppSdk.start()
        Thread.sleep(5000) // Need this because with WireMock we are actually waiting
        Mockito.verify(mock, atLeast(2)).connectToTeam(any(Team::class.java))
        wireAppSdk.stop()
    }

    companion object {
        private val APPLICATION_ID = UUID.randomUUID()
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "localhost:8086"
        private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "dummyPassword"

        private fun getRandomEventStream() =
            """
            id:10
            event:random
            data:{"teamId":"${UUID.randomUUID()}"}
    
            id:11
            event:random
            data:{"teamId":"${UUID.randomUUID()}"}
    
            id:12
            event:random
            data:{"teamId":"${UUID.randomUUID()}"}
            """.trimIndent()

        private val wireMockServer = WireMockServer(8086)

        @JvmStatic
        @BeforeAll
        fun before() {
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
        }
    }
}
