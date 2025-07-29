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

package com.wire.integrations.jvm

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.wire.integrations.jvm.client.BackendClient
import java.util.UUID

object TestUtils {
    const val V = BackendClient.API_VERSION

    fun setupWireMockStubs(wireMockServer: WireMockServer) {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/$V/apps")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "client_id": "dummyClientId",
                        "app_type": "dummyAppType",
                        "app_command": "dummyAppCommand"
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlMatching("/$V/clients")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "id": "dummyClientId"
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlMatching("/$V/login")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "access_token": "demoAccessToken",
                        "expires_in" : 3600
                    }
                    """.trimIndent()
                ).withHeaders(
                    HttpHeaders(
                        HttpHeader("set-cookie", "zuid=demoCookie"),
                        HttpHeader("Content-Type", "application/json")
                    )
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlPathEqualTo("/$V/access"))
                .withQueryParam("client_id", WireMock.matching(".*")).willReturn(
                    WireMock.okJson(
                        """
                        {
                            "access_token": "demoAccessToken",
                            "expires_in" : 3600
                        }
                        """.trimIndent()
                    )
                )
        )
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/$V/feature-configs")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "mls": {
                            "config": {
                                "allowedCipherSuites": [1],
                                "defaultCipherSuite": 1,
                                "defaultProtocol": "mls",
                                "supportedProtocols": ["mls", "proteus"]
                            },
                            "status": "enabled"
                        }
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.put(WireMock.urlPathTemplate("/$V/clients/{appClientId}")).willReturn(
                ok()
            )
        )
        wireMockServer.stubFor(
            WireMock.post(
                WireMock.urlPathTemplate("/$V/mls/key-packages/self/{appClientId}")
            ).willReturn(ok())
        )
    }

    fun setupSdk(eventsHandler: WireEventsHandler) {
        WireAppSdk(
            applicationId = APPLICATION_ID,
            apiToken = API_TOKEN,
            apiHost = API_HOST,
            cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
            eventsHandler
        )
    }

    private val APPLICATION_ID = UUID.randomUUID()
    private const val API_TOKEN = "dummyToken"
    private const val API_HOST = "http://localhost:8086"
    const val CRYPTOGRAPHY_STORAGE_PASSWORD = "myDummyPasswordOfRandom32BytesCH"
}
