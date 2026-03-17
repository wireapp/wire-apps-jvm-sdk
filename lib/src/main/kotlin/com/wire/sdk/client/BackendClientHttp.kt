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

package com.wire.sdk.client

import com.wire.sdk.client.BackendClient.Companion.API_VERSION
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.asset.AssetUploadData
import com.wire.sdk.model.asset.AssetUploadResponse
import com.wire.sdk.model.http.ApiVersionResponse
import com.wire.sdk.model.http.ClientUpdateRequest
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.FeaturesResponse
import com.wire.sdk.model.http.MlsKeyPackageRequest
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.NotificationsResponse
import com.wire.sdk.model.http.client.RegisterClientRequest
import com.wire.sdk.model.http.client.RegisterClientResponse
import com.wire.sdk.model.http.conversation.MlsPublicKeysResponse
import com.wire.sdk.model.http.conversation.OneToOneConversationResponse
import com.wire.sdk.model.http.user.SelfUserResponse
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.utils.Mls
import com.wire.sdk.utils.obfuscateId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.encodeBase64
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.time.Clock

/**
 * Backend client connecting via HTTP (Rest and WebSocket) to the Wire backend.
 * This can be either the production backend on cloud, a test environment or a on-premise server.
 */
internal class BackendClientHttp(
    private val httpClient: HttpClient,
    private val appStorage: AppStorage
) : BackendClient {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Simple cache of the Backend features, as the MLS values we care about
    // will be the same for all teams, so we can make the API call only once.
    private var cachedFeatures: FeaturesResponse? = null

    // Active WebSocket session for graceful shutdown support
    private var activeWebSocketSession: DefaultClientWebSocketSession? = null

    override suspend fun connectWebSocket(
        handleFrames: suspend (DefaultClientWebSocketSession) -> Unit
    ) {
        logger.info("Connecting to the webSocket, waiting for events")

        val path = "/await" +
            (appStorage.getDeviceId()?.let { "?$CLIENT_QUERY_KEY=$it" } ?: "")

        httpClient.wss(
            host = IsolatedKoinContext.getApiHost().replace("https://", "")
                .replace("-https", "-ssl"),
            path = path
        ) {
            activeWebSocketSession = this
            handleFrames(this)
        }
    }

    override suspend fun closeWebSocket() {
        logger.info("Requesting graceful WebSocket close")
        activeWebSocketSession?.close(
            CloseReason(CloseReason.Codes.GOING_AWAY, "Graceful shutdown")
        )
    }

    override suspend fun getAvailableApiVersions(): ApiVersionResponse {
        logger.info("Fetching Wire backend version")
        return httpClient.get("/$API_VERSION/api-version").body()
    }

    override suspend fun getApplicationFeatures(): FeaturesResponse {
        logger.info("Fetching application enabled features")
        return cachedFeatures ?: run {
            httpClient
                .get("/$API_VERSION/feature-configs")
                .body<FeaturesResponse>()
                .also { cachedFeatures = it }
        }
    }

    override suspend fun confirmTeam(teamId: TeamId) {
        logger.info("Confirming team invite")
    }

    override suspend fun updateClientWithMlsPublicKey(
        cryptoClientId: CryptoClientId,
        mlsPublicKeys: MlsPublicKeys
    ) {
        try {
            httpClient.put("/$API_VERSION/clients/${appStorage.getDeviceId()}") {
                setBody(ClientUpdateRequest(mlsPublicKeys = mlsPublicKeys))
                contentType(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.info("MLS public key already set for DEMO user: $cryptoClientId", ex)
        }
        logger.info("Updated client with mls info for client: $cryptoClientId")
    }

    override suspend fun registerClient(
        registerClientRequest: RegisterClientRequest
    ): RegisterClientResponse {
        val clientCreatedResponse = httpClient.post("/$API_VERSION/clients") {
            setBody(registerClientRequest)
            contentType(ContentType.Application.Json)
        }.body<RegisterClientResponse>()

        // Register client is performed with an access_token having limited scope.
        //  clear the token to force a refresh with the full-scope token for next requests.
        httpClient.authProviders
            .filterIsInstance<BearerAuthProvider>()
            .first()
            .clearToken()

        return clientCreatedResponse
    }

    override suspend fun uploadMlsKeyPackages(
        cryptoClientId: CryptoClientId,
        mlsKeyPackages: List<ByteArray>
    ) {
        val mlsKeyPackageRequest =
            MlsKeyPackageRequest(mlsKeyPackages.map { Base64.getEncoder().encodeToString(it) })
        try {
            httpClient.post("/$API_VERSION/mls/key-packages/self/${appStorage.getDeviceId()}") {
                setBody(mlsKeyPackageRequest)
                contentType(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.info("MLS public key already set for DEMO user: $cryptoClientId", ex)
        }
        logger.info("Updated client with mls key packages for client: $cryptoClientId")
    }

    override suspend fun getPublicKeys(): MlsPublicKeysResponse {
        return httpClient.get("$API_VERSION/mls/public-keys") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<MlsPublicKeysResponse>()
    }

    override suspend fun uploadCommitBundle(commitBundle: ByteArray) {
        httpClient.post("/$API_VERSION/mls/commit-bundles") {
            setBody(commitBundle)
            contentType(Mls)
        }
    }

    override suspend fun sendMessage(mlsMessage: ByteArray) {
        httpClient.post("/$API_VERSION/mls/messages") {
            setBody(mlsMessage)
            contentType(Mls)
        }
    }

    /**
     * Get Self User (SDK User) details
     *
     * @return [SelfUserResponse]
     */
    override suspend fun getSelfUser(): SelfUserResponse {
        return httpClient.get("/$API_VERSION/self") {
            accept(ContentType.Application.Json)
        }.body<SelfUserResponse>()
    }

    override suspend fun downloadAsset(
        assetId: String,
        assetDomain: String,
        assetToken: String?
    ): ByteArray {
        logger.info("Downloading asset ${assetId.obfuscateId()}")

        return httpClient.prepareGet("$PATH_PUBLIC_ASSETS_V4/$assetDomain/$assetId") {
            headers {
                if (!assetToken.isNullOrBlank()) {
                    append(HEADER_ASSET_TOKEN, assetToken)
                }
            }
        }.execute { httpResponse ->
            httpResponse.readRawBytes()
        }
    }

    override suspend fun uploadAsset(
        encryptedFile: ByteArray,
        encryptedFileLength: Long,
        assetUploadData: AssetUploadData
    ): AssetUploadResponse {
        logger.info("Uploading new asset")

        return httpClient.post(PATH_PUBLIC_ASSETS_V3) {
            setBody(
                AssetBody(
                    assetContent = encryptedFile,
                    assetSize = encryptedFileLength,
                    metadata = assetUploadData
                )
            )
            contentType(ContentType.MultiPart.Mixed)
        }.body<AssetUploadResponse>()
    }

    override suspend fun getOneToOneConversation(
        userId: QualifiedId
    ): OneToOneConversationResponse {
        return httpClient.get("/$API_VERSION/one2one-conversations/${userId.domain}/${userId.id}") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<OneToOneConversationResponse>()
    }

    override suspend fun getLastNotification(): EventResponse {
        val lastNotification = httpClient.get("notifications/last") {
            appStorage.getDeviceId()?.let { parameter(CLIENT_QUERY_KEY, it) }
        }.body<EventResponse>()

        return lastNotification
    }

    override suspend fun getPaginatedNotifications(
        querySize: Int,
        querySince: String?
    ): NotificationsResponse {
        try {
            val notifications = httpClient.get("notifications") {
                parameter(SIZE_QUERY_KEY, querySize)
                appStorage.getDeviceId()?.let { parameter(CLIENT_QUERY_KEY, it) }
                querySince?.let { parameter(SINCE_QUERY_KEY, it) }
            }.body<NotificationsResponse>()
            return notifications
        } catch (exception: WireException.ClientError) {
            logger.warn("Notifications not found.", exception)
            return NotificationsResponse(
                hasMore = false,
                events = emptyList(),
                time = Clock.System.now()
            )
        }
    }

    internal class AssetBody internal constructor(
        private val assetContent: ByteArray,
        assetSize: Long,
        metadata: AssetUploadData
    ) : OutgoingContent.ByteArrayContent() {
        private val openingData: String by lazy {
            val body = StringBuilder()

            // Part 1
            val strMetadata = "{\"public\": ${metadata.public}, " +
                "\"retention\": \"${metadata.retention.value}\"}"

            body.append("--frontier\r\n")
            body.append("Content-Type: application/json;charset=utf-8\r\n")
            body.append("Content-Length: ")
                .append(strMetadata.length)
                .append("\r\n\r\n")
            body.append(strMetadata)
                .append("\r\n")

            // Part 2
            body.append("--frontier\r\n")
            body.append("Content-Type: application/octet-stream")
                .append("\r\n")
            body.append("Content-Length: ")
                .append(assetSize)
                .append("\r\n")
            body.append("Content-MD5: ")
                .append(metadata.md5.encodeBase64())
                .append("\r\n\r\n")

            body.toString()
        }
        private val closingData = "\r\n--frontier--\r\n"

        override fun bytes(): ByteArray =
            openingData.toByteArray() + assetContent + closingData.toByteArray()
    }

    private companion object {
        const val PATH_PUBLIC_ASSETS_V3 = "assets/v3"
        const val PATH_PUBLIC_ASSETS_V4 = "assets/v4"
        const val HEADER_ASSET_TOKEN = "Asset-Token"

        const val SIZE_QUERY_KEY = "size"
        const val CLIENT_QUERY_KEY = "client"
        const val SINCE_QUERY_KEY = "since"
    }
}
