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

package com.wire.integrations.jvm.client

import com.wire.integrations.jvm.client.BackendClient.Companion.API_VERSION
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.asset.AssetUploadData
import com.wire.integrations.jvm.model.asset.AssetUploadResponse
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.http.ClientUpdateRequest
import com.wire.integrations.jvm.model.http.FeaturesResponse
import com.wire.integrations.jvm.model.http.MlsKeyPackageRequest
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import com.wire.integrations.jvm.model.http.user.UserResponse
import com.wire.integrations.jvm.utils.Mls
import com.wire.integrations.jvm.utils.obfuscateId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cookies.get
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.setCookie
import io.ktor.util.encodeBase64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID

/**
 * Backend client implementation for test/demo purposes
 * Useful for testing the SDK without the Backend having new Application API implemented.
 *
 * Real http calls are made, they target 2 hosts:
 * - localhost:8086 - reaching the local Demo events producer
 * - some Wire Backend evn - emulate Apps API calls by using the Client API with a DEMO user
 */
internal class BackendClientDemo(private val httpClient: HttpClient) : BackendClient {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Simple cache of the Backend features, as the MLS values we care about
    // will be the same for all teams, so we can make the API call only once.
    private var cachedFeatures: FeaturesResponse? = null
    private var cachedAccessToken: String? = null

    override suspend fun connectWebSocket(
        handleFrames: suspend (DefaultClientWebSocketSession) -> Unit
    ) {
        logger.info("Connecting to the webSocket, waiting for events")

        val token = loginUser()
        httpClient.wss(
            host = IsolatedKoinContext.getApiHost()?.replace("https://", "")
                ?.replace("-https", "-ssl"),
            path = "/$API_VERSION/events?client=$DEMO_USER_CLIENT&access_token=$token"
        ) {
            handleFrames(this)
        }
    }

    override suspend fun getAvailableApiVersions(): ApiVersionResponse {
        logger.info("Fetching Wire backend version")
        return httpClient.get("/$API_VERSION/api-version").body()
    }

    override suspend fun getApplicationData(): AppDataResponse {
        logger.info("Fetching application data")
        return AppDataResponse(
            appClientId = "$DEMO_USER_ID:$DEMO_USER_CLIENT@$DEMO_ENVIRONMENT",
            appType = "FULL",
            appCommand = "demo"
        )
    }

    override suspend fun getApplicationFeatures(): FeaturesResponse {
        logger.info("Fetching application enabled features")
        return cachedFeatures ?: run {
            val token = loginUser()
            httpClient.get("/$API_VERSION/feature-configs") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }.body<FeaturesResponse>().also { cachedFeatures = it }
        }
    }

    override suspend fun confirmTeam(teamId: TeamId) {
        logger.info("Confirming team invite")
    }

    private var tokenTimestamp: Long? = null

    /**
     * Login DEMO user in the backend, get access_token for further requests.
     * After the login, the token is immediately refreshed by calling /access,
     * because the new one is tied to client and has more permissions.
     * Not needed in the actual implementation, as the SDK is authenticated with the API_TOKEN
     */
    private suspend fun loginUser(): String {
        val currentTime = System.currentTimeMillis()

        // Check if token is valid (not null and not expired)
        if (cachedAccessToken != null && tokenTimestamp != null) {
            val timeSinceTokenIssued = currentTime - tokenTimestamp!!
            if (timeSinceTokenIssued < TOKEN_EXPIRATION_MS) {
                return cachedAccessToken as String
            }
            // Token has expired, will get a new one
            logger.info("Access token expired, getting a new one")
        }

        val loginResponse = httpClient.post("/$API_VERSION/login") {
            setBody(LoginRequest(DEMO_USER_EMAIL, DEMO_USER_PASSWORD))
            contentType(ContentType.Application.Json)
        }
        val zuidCookie = loginResponse.setCookie()["zuid"]

        val accessResponse =
            httpClient.post("/$API_VERSION/access?client_id=$DEMO_USER_CLIENT") {
                headers {
                    append(HttpHeaders.Cookie, "zuid=${zuidCookie!!.value}")
                }
                accept(ContentType.Application.Json)
            }.body<LoginResponse>()

        cachedAccessToken = accessResponse.accessToken
        tokenTimestamp = currentTime

        return accessResponse.accessToken
    }

    override suspend fun updateClientWithMlsPublicKey(
        appClientId: AppClientId,
        mlsPublicKeys: MlsPublicKeys
    ) {
        val token = loginUser()
        try {
            httpClient.put("/$API_VERSION/clients/$DEMO_USER_CLIENT") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(ClientUpdateRequest(mlsPublicKeys = mlsPublicKeys))
                contentType(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.info("MLS public key already set for DEMO user: $appClientId", ex)
        }
        logger.info("Updated client with mls info for client: $appClientId")
    }

    override suspend fun uploadMlsKeyPackages(
        appClientId: AppClientId,
        mlsKeyPackages: List<ByteArray>
    ) {
        val token = loginUser()
        val mlsKeyPackageRequest =
            MlsKeyPackageRequest(mlsKeyPackages.map { Base64.getEncoder().encodeToString(it) })
        try {
            httpClient.post("/$API_VERSION/mls/key-packages/self/$DEMO_USER_CLIENT") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(mlsKeyPackageRequest)
                contentType(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.info("MLS public key already set for DEMO user: $appClientId", ex)
        }
        logger.info("Updated client with mls key packages for client: $appClientId")
    }

    override suspend fun uploadCommitBundle(commitBundle: ByteArray) {
        val token = loginUser()
        httpClient.post("/$API_VERSION/mls/commit-bundles") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(commitBundle)
            contentType(Mls)
        }
    }

    override suspend fun sendMessage(mlsMessage: ByteArray) {
        val token = loginUser()
        httpClient.post("/$API_VERSION/mls/messages") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(mlsMessage)
            contentType(Mls)
        }
    }

    override suspend fun getConversation(conversationId: QualifiedId): ConversationResponse {
        logger.info("Fetching conversation: $conversationId")
        val token = loginUser()
        return httpClient.get(
            "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}"
        ) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }.body<ConversationResponse>()
    }

    override suspend fun getUserData(userId: QualifiedId): UserResponse {
        logger.info("Fetching user: $userId")
        val token = loginUser()
        return httpClient.get(
            "/$API_VERSION/users/${userId.domain}/${userId.id}"
        ) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }.body<UserResponse>()
    }

    override suspend fun getConversationGroupInfo(conversationId: QualifiedId): ByteArray {
        logger.info("Fetching conversation groupInfo: $conversationId")
        val token = loginUser()
        return httpClient.get(
            "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}" +
                "/groupinfo"
        ) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            accept(Mls)
        }.body<ByteArray>()
    }

    override suspend fun downloadAsset(
        assetId: String,
        assetDomain: String,
        assetToken: String?
    ): ByteArray {
        logger.info("Downloading asset ${assetId.obfuscateId()}")

        val token = loginUser()
        return httpClient.prepareGet("$PATH_PUBLIC_ASSETS_V4/$assetDomain/$assetId") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
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

        val token = loginUser()
        return httpClient.post(PATH_PUBLIC_ASSETS_V3) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
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
        const val TOKEN_EXPIRATION_MS = 14 * 60 * 1000 // 14 minutes in milliseconds

        val DEMO_USER_ID: UUID =
            UUID.fromString(
                System.getenv("WIRE_SDK_USER_ID")
                    ?: "ee159b66-fd70-4739-9bae-23c96a02cb09"
            )

        val DEMO_USER_EMAIL: String =
            System.getenv("WIRE_SDK_EMAIL") ?: "integrations-admin@wire.com"

        val DEMO_USER_PASSWORD: String =
            System.getenv("WIRE_SDK_PASSWORD") ?: "Aqa123456!"

        val DEMO_USER_CLIENT: String =
            System.getenv("WIRE_SDK_CLIENT") ?: "fc088e7f958fb833"

        val DEMO_ENVIRONMENT: String =
            System.getenv("WIRE_SDK_ENVIRONMENT") ?: "chala.wire.link"
    }
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int
)
