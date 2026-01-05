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
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.wire.sdk.client.BackendClient
import java.util.UUID

object TestUtils {
    const val V = BackendClient.API_VERSION

    fun setupWireMockStubs(wireMockServer: WireMockServer) {
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/$V/api-version")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "development": [8],
                        "domain": "staging.zinfra.io",
                        "federation": true,
                        "supported": [1,2,3,4,5,6,7]
                    }
                    """.trimIndent()
                )
            )
        )
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
        wireMockServer.stubFor(
            WireMock.get(
                WireMock.urlPathTemplate(
                    "/$V/mls/public-keys"
                )
            ).willReturn(
                WireMock.okJson(MLS_PUBLIC_KEYS_RESPONSE)
            )
        )
        wireMockServer.stubFor(
            WireMock.post(
                WireMock.urlPathTemplate(
                    "/$V/mls/commit-bundles"
                )
            ).willReturn(
                ok()
            )
        )
        wireMockServer.stubFor(
            WireMock.post(
                WireMock.urlPathTemplate(
                    "/$V/conversations/list-ids"
                )
            ).willReturn(
                WireMock.okJson(
                    """
                        {
                            "has_more": false,
                            "paging_state": "123",
                            "qualified_conversations": []
                        }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.get(
                WireMock.urlPathTemplate("/$V/self")
            ).willReturn(
                WireMock.okJson(
                    """
                        {
                          "qualified_id": {
                            "domain": "staging.zinfra.io",
                            "id": "b82c3381-37b0-4545-b555-ca32a3a093d0"
                          },
                          "team": "86fdb92f-76b8-4548-8f21-6e3fd3f5f449",
                          "email": "sdk.user@wire.com",
                          "name": "SDK User"
                        }
                    """.trimIndent()
                )
            )
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
    private val MLS_PUBLIC_KEYS_RESPONSE =
        """
            {
                "removal": {
                    "ecdsa_secp256r1_sha256": "BGBbuHvwWYBrTru7sFzzcK/oT9XVzGkdNv/6iBHNtEo9QVDmYKbtW2FA+f+iNoOBgvhjp6mYQKmypa+z63u5/Qs=",
                    "ecdsa_secp384r1_sha384": "BMW56MVt4zR1oCHv40t/Q9VDqMBPsetBzESkCY3lXhyQmEMaJRO293D4v94qTrSwSFNHG9859anU03OtQo2CXz5Tsgr2HTL7cNBpGWrROPSmS+dx/mKx4sugHn2zakM9hA==",
                    "ecdsa_secp521r1_sha512": "BACrVVw3tK68GL8F7FP05mUp5y2zSV5eofS48BVoYNLdcNOBlKokO0f3mtGqLEiKPbgVncKeMskaZap2wL/kc1v/1wFCBdoSx5lS+efz1Fe3sx+lwjuhwkGW891lsjpbXzdkWGsM0yHY83DCgGT3XGaITURmL4I+EqEiMqtgi4VWo26+Nw==",
                    "ed25519": "3AEFMpXsnJ28RcyA7CIRuaDL7L0vGmKaGjD206SANZw="
                }
            }
        """.trimIndent()
}
