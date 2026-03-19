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
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.MlsKeyPackageRequest
import com.wire.sdk.model.http.conversation.ClaimedKeyPackageList
import com.wire.sdk.model.http.conversation.MlsPublicKeysResponse
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.utils.Mls
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.slf4j.LoggerFactory
import java.util.Base64

internal class MlsApiClient(
    private val httpClient: HttpClient,
    private val appStorage: AppStorage
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val basePath = "$API_VERSION/mls"

    suspend fun getPublicKeys(): MlsPublicKeysResponse {
        return httpClient.get("/$basePath/public-keys") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<MlsPublicKeysResponse>()
    }

    suspend fun claimKeyPackages(
        user: QualifiedId,
        cipherSuite: String
    ): ClaimedKeyPackageList {
        val url = "/$basePath/key-packages/claim/${user.domain}/${user.id}"
        return httpClient.post(url) {
            parameter("ciphersuite", cipherSuite)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<ClaimedKeyPackageList>()
    }

    suspend fun uploadMlsKeyPackages(
        cryptoClientId: CryptoClientId,
        mlsKeyPackages: List<ByteArray>
    ) {
        val mlsKeyPackageRequest =
            MlsKeyPackageRequest(mlsKeyPackages.map { Base64.getEncoder().encodeToString(it) })
        try {
            httpClient.post(
                "/$basePath/key-packages/self/${appStorage.getDeviceId()}"
            ) {
                setBody(mlsKeyPackageRequest)
                contentType(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.info("MLS public key already set for DEMO user: $cryptoClientId", ex)
        }
        logger.info("Updated client with mls key packages for client: $cryptoClientId")
    }

    suspend fun uploadCommitBundle(commitBundle: ByteArray) {
        httpClient.post("/$basePath/commit-bundles") {
            setBody(commitBundle)
            contentType(Mls)
        }
    }

    suspend fun sendMessage(mlsMessage: ByteArray) {
        httpClient.post("/$basePath/messages") {
            setBody(mlsMessage)
            contentType(Mls)
        }
    }
}
