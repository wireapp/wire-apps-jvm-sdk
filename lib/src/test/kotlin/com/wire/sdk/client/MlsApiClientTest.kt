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

import com.wire.sdk.client.BackendClient.Companion.API_VERSION
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.persistence.AppStorage
import io.ktor.http.HttpMethod
import io.ktor.http.fullPath
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MlsApiClientTest {
    private val deviceId = "device-id-123"

    private val appStorage = mockk<AppStorage> {
        every { getDeviceId() } returns deviceId
    }

    private val userId = QualifiedId(
        id = UUID.fromString("3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a"),
        domain = "example.com"
    )

    private val cryptoClientId = CryptoClientId("client-id-123")

    private val publicKeysResponseJson = """
        {
            "removal": {
                "ed25519": "base64edkey==",
                "ecdsa_secp256r1_sha256": "base64p256key==",
                "ecdsa_secp384r1_sha384": null,
                "ecdsa_secp521r1_sha512": null
            }
        }
    """.trimIndent()

    private val claimedKeyPackagesResponseJson = """
        {
            "key_packages": [
                {
                    "client": "client-abc",
                    "domain": "example.com",
                    "key_package": "base64keypackage==",
                    "key_package_ref": "base64ref==",
                    "user": "3b5efd97-2f3e-4ab8-8525-bc3e8e7c4e1a"
                }
            ]
        }
    """.trimIndent()

    private fun mlsClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = MlsApiClient(
        createMockHttpClient(
            responseBody = responseBody,
            assertRequest = assertRequest
        ),
        appStorage
    )

    @Test
    fun `when getPublicKeys, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            mlsClient(publicKeysResponseJson) { capturedPath = it.url.fullPath }.getPublicKeys()
            assertEquals("/$API_VERSION/mls/public-keys", capturedPath)
        }

    @Test
    fun `when getPublicKeys, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            mlsClient(publicKeysResponseJson) { capturedMethod = it.method }.getPublicKeys()
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when claimKeyPackages, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            mlsClient(claimedKeyPackagesResponseJson) { capturedPath = it.url.encodedPath }
                .claimKeyPackages(userId, "1")
            assertEquals(
                "/$API_VERSION/mls/key-packages/claim/${userId.domain}/${userId.id}",
                capturedPath
            )
        }

    @Test
    fun `when claimKeyPackages, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            mlsClient(claimedKeyPackagesResponseJson) { capturedMethod = it.method }
                .claimKeyPackages(userId, "1")
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when claimKeyPackages, then ciphersuite query param set`() =
        runTest {
            var capturedParam: String? = null
            mlsClient(claimedKeyPackagesResponseJson) {
                capturedParam = it.url.parameters["ciphersuite"]
            }
                .claimKeyPackages(userId, "1")
            assertEquals("1", capturedParam)
        }

    @Test
    fun `when uploadMlsKeyPackages, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            mlsClient { capturedPath = it.url.fullPath }
                .uploadMlsKeyPackages(cryptoClientId, listOf(byteArrayOf(0x01)))
            assertEquals("/$API_VERSION/mls/key-packages/self/$deviceId", capturedPath)
        }

    @Test
    fun `when uploadMlsKeyPackages, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            mlsClient { capturedMethod = it.method }
                .uploadMlsKeyPackages(cryptoClientId, listOf(byteArrayOf(0x01)))
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when uploadCommitBundle, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            mlsClient { capturedPath = it.url.fullPath }
                .uploadCommitBundle(byteArrayOf(0x01))
            assertEquals("/$API_VERSION/mls/commit-bundles", capturedPath)
        }

    @Test
    fun `when uploadCommitBundle, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            mlsClient { capturedMethod = it.method }
                .uploadCommitBundle(byteArrayOf(0x01))
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when sendMessage, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            mlsClient { capturedPath = it.url.fullPath }
                .sendMessage(byteArrayOf(0x01))
            assertEquals("/$API_VERSION/mls/messages", capturedPath)
        }

    @Test
    fun `when sendMessage, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            mlsClient { capturedMethod = it.method }
                .sendMessage(byteArrayOf(0x01))
            assertEquals(HttpMethod.Post, capturedMethod)
        }
}
