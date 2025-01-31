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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class WireAppSdkTest {
    @Test
    fun koinModulesLoadCorrectly() {
        val wireAppSdk =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
                object : WireEventsHandler {
                    override fun onEvent(event: String) {
                        println(event)
                    }
                }
            )

        assertNotNull(wireAppSdk.getTeamManager(), "Koin dependency injection failed")
    }

    @Test
    fun fetchingApplicationDataWithWireMockReturnsDummyData() {
        val wireAppSdk =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
                object : WireEventsHandler {
                    override fun onEvent(event: String) {
                        println(event)
                    }
                }
            )

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

        val result = wireAppSdk.getTeamManager().fetchApplicationData()
        assertEquals("dummyAppType", result.appType)
    }

    companion object {
        private val APPLICATION_ID = UUID.randomUUID()
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "localhost:8080"
        private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "dummyPassword"

        private val wireMockServer = WireMockServer(8080)

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
