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

import com.wire.sdk.model.asset.AssetRetention
import com.wire.sdk.model.asset.AssetUploadData
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssetsApiClientTest {
    private fun apiClient(
        responseBody: Any = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = AssetsApiClient(
        createMockHttpClient(
            responseBody = responseBody,
            assertRequest = assertRequest
        )
    )

    @Test
    fun `when downloadAsset, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(responseBody = FAKE_BYTES) { capturedPath = it.url.encodedPath }
                .downloadAsset(ASSET_ID, ASSET_DOMAIN, ASSET_TOKEN)
            assertEquals("/assets/$ASSET_DOMAIN/$ASSET_ID", capturedPath)
        }

    @Test
    fun `when downloadAsset, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(responseBody = FAKE_BYTES) { capturedMethod = it.method }
                .downloadAsset(ASSET_ID, ASSET_DOMAIN, ASSET_TOKEN)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when downloadAsset, then bytes returned`() =
        runTest {
            val result = apiClient(responseBody = FAKE_BYTES)
                .downloadAsset(ASSET_ID, ASSET_DOMAIN, ASSET_TOKEN)
            assertContentEquals(FAKE_BYTES, result)
        }

    @Test
    fun `when uploadAsset, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            apiClient(UPLOAD_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .uploadAsset(FAKE_BYTES, FAKE_BYTES.size.toLong(), ASSET_UPLOAD_DATA)
            assertEquals("/assets", capturedPath)
        }

    @Test
    fun `when uploadAsset, then POST method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            apiClient(UPLOAD_RESPONSE_JSON) { capturedMethod = it.method }
                .uploadAsset(FAKE_BYTES, FAKE_BYTES.size.toLong(), ASSET_UPLOAD_DATA)
            assertEquals(HttpMethod.Post, capturedMethod)
        }

    @Test
    fun `when uploadAsset, then response deserialized`() =
        runTest {
            val result = apiClient(UPLOAD_RESPONSE_JSON)
                .uploadAsset(FAKE_BYTES, FAKE_BYTES.size.toLong(), ASSET_UPLOAD_DATA)
            assertEquals("3-1-abc123", result.key)
            assertEquals("example.com", result.domain)
            assertEquals("upload-token-xyz", result.token)
        }

    @Test
    fun `when AssetBody bytes, then contains multipart boundary`() {
        val body = assetBody().bytes().toString(Charsets.UTF_8)
        assertTrue(body.contains("--frontier"))
    }

    @Test
    fun `when AssetBody bytes, then ends with closing boundary`() {
        val body = assetBody().bytes().toString(Charsets.UTF_8)
        assertTrue(body.trimEnd().endsWith("--frontier--"))
    }

    @Test
    fun `when AssetBody bytes, then contains JSON metadata part`() {
        val body = assetBody().bytes().toString(Charsets.UTF_8)
        assertTrue(body.contains("application/json;charset=utf-8"))
        assertTrue(body.contains("\"public\": ${ASSET_UPLOAD_DATA.public}"))
        assertTrue(body.contains("\"retention\": \"${ASSET_UPLOAD_DATA.retention.value}\""))
    }

    @Test
    fun `when AssetBody bytes, then contains octet-stream part`() {
        val body = assetBody().bytes().toString(Charsets.UTF_8)
        assertTrue(body.contains("application/octet-stream"))
        assertTrue(body.contains("Content-Length: ${FAKE_BYTES.size}"))
    }

    @Test
    fun `when AssetBody bytes, then contains Content-MD5 header`() {
        val body = assetBody().bytes().toString(Charsets.UTF_8)
        assertTrue(body.contains("Content-MD5:"))
    }

    @Test
    fun `when AssetBody bytes, then raw asset bytes embedded`() {
        val bodyBytes = assetBody().bytes()
        val bodyHex = bodyBytes.joinToString("") { "%02x".format(it) }
        val assetHex = FAKE_BYTES.joinToString("") { "%02x".format(it) }
        assertTrue(bodyHex.contains(assetHex))
    }

    companion object {
        private const val ASSET_ID = "asset-id-1234"
        private const val ASSET_DOMAIN = "example.com"
        private const val ASSET_TOKEN = "secret-token"
        private val FAKE_BYTES = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        private val ASSET_UPLOAD_DATA = AssetUploadData(
            public = false,
            retention = AssetRetention.PERSISTENT,
            md5 = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        )
        private val UPLOAD_RESPONSE_JSON = """
            {
                "key": "3-1-abc123",
                "domain": "example.com",
                "expires": null,
                "token": "upload-token-xyz"
            }
        """.trimIndent()

        private fun assetBody() =
            AssetsApiClient.AssetBody(
                assetContent = FAKE_BYTES,
                assetSize = FAKE_BYTES.size.toLong(),
                metadata = ASSET_UPLOAD_DATA
            )
    }
}
