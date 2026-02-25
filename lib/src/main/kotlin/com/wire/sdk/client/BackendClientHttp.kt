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
import com.wire.sdk.model.http.AppDataResponse
import com.wire.sdk.model.http.ClientUpdateRequest
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.FeaturesResponse
import com.wire.sdk.model.http.MlsKeyPackageRequest
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.NotificationsResponse
import com.wire.sdk.model.http.client.RegisterClientRequest
import com.wire.sdk.model.http.client.RegisterClientResponse
import com.wire.sdk.model.http.conversation.ClaimedKeyPackageList
import com.wire.sdk.model.http.conversation.ConversationIdsRequest
import com.wire.sdk.model.http.conversation.ConversationIdsResponse
import com.wire.sdk.model.http.conversation.ConversationListPaginationConfig
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.ConversationsResponse
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.MlsPublicKeysResponse
import com.wire.sdk.model.http.conversation.OneToOneConversationResponse
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import com.wire.sdk.model.http.user.SelfUserResponse
import com.wire.sdk.model.http.user.UserClientResponse
import com.wire.sdk.model.http.user.UserResponse
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.utils.Mls
import com.wire.sdk.utils.obfuscateId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.wss
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.setCookie
import io.ktor.util.encodeBase64
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID
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
    private var cachedAccessToken: String? = null

    // Active WebSocket session for graceful shutdown support
    private var activeWebSocketSession: DefaultClientWebSocketSession? = null

    override suspend fun connectWebSocket(
        handleFrames: suspend (DefaultClientWebSocketSession) -> Unit
    ) {
        logger.info("Connecting to the webSocket, waiting for events")
        val token = loginUser()

        val path = "/await" +
            "?$ACCESS_TOKEN_QUERY_KEY=$token" +
            (appStorage.getDeviceId()?.let { "&$CLIENT_QUERY_KEY=$it" } ?: "")

        httpClient.wss(
            host = IsolatedKoinContext.getApiHost()?.replace("https://", "")
                ?.replace("-https", "-ssl"),
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

    override suspend fun getApplicationData(): AppDataResponse {
        logger.info("Fetching application data")
        val applicationId = IsolatedKoinContext.getApplicationId()
        val applicationDomain = IsolatedKoinContext.getBackendDomain()
        return AppDataResponse(
            appClientId = "$applicationId:${appStorage.getDeviceId()}@$applicationDomain",
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

        val apiToken = appStorage.getBackendCookie()

        return apiToken?.let {
            getAccessToken(
                apiToken = apiToken,
                currentTime = currentTime
            )
        } ?: throw WireException.MissingParameter(
            message = "apiToken is empty or null."
        )
    }

    private suspend fun getAccessToken(
        apiToken: String,
        currentTime: Long
    ): String {
        val deviceId = appStorage.getDeviceId()
        val url = "/$API_VERSION/access".let {
            if (deviceId != null) "$it?client_id=$deviceId" else it
        }
        val accessResponse = try {
            httpClient.post(url) {
                headers {
                    append(HttpHeaders.Cookie, "zuid=$apiToken")
                }
                accept(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.error("Unable to retrieve access token, Error: ${ex.message}")
            if (isCookieExpired(ex)) {
                appStorage.deleteBackendCookie()
            }
            // Can't recover from this, need to restart the app with a valid api token
            throw Error("Current cookie/api-token is expired. Get a apiToken and restart the App")
        }

        // Chance of cookie renewal -> Store new cookie
        val responseCookies = accessResponse.setCookie()
        if (!responseCookies.isEmpty()) {
            val newCookie = responseCookies.firstOrNull { it.name == "zuid" }?.value
            if (!newCookie.isNullOrBlank()) {
                appStorage.saveBackendCookie(newCookie)
                logger.info("Received new api token from backend, updated stored cookie")
            }
        }

        val accessResponseData = accessResponse.body<LoginResponse>()

        // Store accessToken only if deviceId is available, because only in that case we have
        //  a token with full permissions.
        deviceId?.let {
            cachedAccessToken = accessResponseData.accessToken
            tokenTimestamp = currentTime
        }

        return accessResponseData.accessToken
    }

    private fun isCookieExpired(ex: WireException.ClientError): Boolean =
        ex.throwable is ClientRequestException &&
            (
                ex.throwable.response.status == HttpStatusCode.Unauthorized ||
                ex.throwable.response.status == HttpStatusCode.Forbidden
            )

    override suspend fun updateClientWithMlsPublicKey(
        cryptoClientId: CryptoClientId,
        mlsPublicKeys: MlsPublicKeys
    ) {
        val token = loginUser()
        try {
            httpClient.put("/$API_VERSION/clients/${appStorage.getDeviceId()}") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
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
        val token = loginUser()
        return httpClient.post("/$API_VERSION/clients") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(registerClientRequest)
            contentType(ContentType.Application.Json)
        }.body<RegisterClientResponse>()
    }

    override suspend fun uploadMlsKeyPackages(
        cryptoClientId: CryptoClientId,
        mlsKeyPackages: List<ByteArray>
    ) {
        val token = loginUser()
        val mlsKeyPackageRequest =
            MlsKeyPackageRequest(mlsKeyPackages.map { Base64.getEncoder().encodeToString(it) })
        try {
            httpClient.post("/$API_VERSION/mls/key-packages/self/${appStorage.getDeviceId()}") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(mlsKeyPackageRequest)
                contentType(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.info("MLS public key already set for DEMO user: $cryptoClientId", ex)
        }
        logger.info("Updated client with mls key packages for client: $cryptoClientId")
    }

    override suspend fun claimKeyPackages(
        user: QualifiedId,
        cipherSuite: String
    ): ClaimedKeyPackageList {
        val token = loginUser()
        val url = "$API_VERSION/mls/key-packages/claim/${user.domain}/${user.id}"
        return httpClient.post(url) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            parameter("ciphersuite", cipherSuite)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<ClaimedKeyPackageList>()
    }

    override suspend fun getPublicKeys(): MlsPublicKeysResponse {
        val token = loginUser()
        return httpClient.get("$API_VERSION/mls/public-keys") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<MlsPublicKeysResponse>()
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

    /**
     * Get User details
     *
     * @param [QualifiedId] The ID of the user to be requested
     * @return [UserResponse]
     */
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

    /**
     * Get Self User (SDK User) details
     *
     * @return [SelfUserResponse]
     */
    override suspend fun getSelfUser(): SelfUserResponse {
        val token = loginUser()
        return httpClient.get("/$API_VERSION/self") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            accept(ContentType.Application.Json)
        }.body<SelfUserResponse>()
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

    override suspend fun createGroupConversation(
        createConversationRequest: CreateConversationRequest
    ): ConversationResponse {
        val token = loginUser()
        return httpClient.post("/$API_VERSION/conversations") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(createConversationRequest)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<ConversationResponse>()
    }

    override suspend fun getOneToOneConversation(
        userId: QualifiedId
    ): OneToOneConversationResponse {
        val token = loginUser()
        return httpClient.get("/$API_VERSION/one2one-conversations/${userId.domain}/${userId.id}") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<OneToOneConversationResponse>()
    }

    override suspend fun updateConversationMemberRole(
        conversationId: QualifiedId,
        userId: QualifiedId,
        updateConversationMemberRoleRequest: UpdateConversationMemberRoleRequest
    ) {
        val token = loginUser()
        val conversationPath = "conversations/${conversationId.domain}/${conversationId.id}"
        val memberPath = "members/${userId.domain}/${userId.id}"

        httpClient.put("/$API_VERSION/$conversationPath/$memberPath") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(updateConversationMemberRoleRequest)
            contentType(ContentType.Application.Json)
        }
    }

    override suspend fun getConversationIds(): List<QualifiedId> {
        val token = loginUser()
        val conversationIds: MutableList<QualifiedId> = mutableListOf()

        var pagingConfig = ConversationListPaginationConfig(
            pagingState = null,
            size = CONVERSATION_LIST_IDS_PAGING_SIZE
        )

        var hasMorePages: Boolean
        do {
            val listIdsResponse = httpClient.post("/$API_VERSION/conversations/list-ids") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(pagingConfig)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }.body<ConversationIdsResponse>()

            hasMorePages = listIdsResponse.hasMore
            pagingConfig = pagingConfig.copy(pagingState = listIdsResponse.pagingState)
            conversationIds.addAll(listIdsResponse.qualifiedConversations)
        } while (hasMorePages)

        return conversationIds
    }

    override suspend fun getConversationsById(
        conversationIds: List<QualifiedId>
    ): List<ConversationResponse> {
        val token = loginUser()
        val conversations: MutableList<ConversationResponse> = mutableListOf()

        if (!conversationIds.isEmpty()) {
            var startIndex = FETCH_CONVERSATIONS_START_INDEX
            var endIndex = FETCH_CONVERSATIONS_END_INDEX

            do {
                if (endIndex > conversationIds.size) {
                    endIndex = conversationIds.size
                }

                val conversationIdsRequest = ConversationIdsRequest(
                    qualifiedIds = conversationIds.subList(startIndex, endIndex)
                )

                val conversationsListResponse =
                    httpClient.post("/$API_VERSION/conversations/list") {
                        headers {
                            append(HttpHeaders.Authorization, "Bearer $token")
                        }
                        setBody(conversationIdsRequest)
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                    }.body<ConversationsResponse>()

                conversations.addAll(conversationsListResponse.found)

                startIndex += FETCH_CONVERSATIONS_INCREASE_INDEX
                endIndex += FETCH_CONVERSATIONS_INCREASE_INDEX
            } while (endIndex < conversationIds.size + FETCH_CONVERSATIONS_INCREASE_INDEX)
        }

        return conversations
    }

    override suspend fun leaveConversation(
        userId: QualifiedId,
        conversationId: QualifiedId
    ) {
        logger.info(
            "App user will be removed from the conversation in the backend. " +
                "userId:{}, conversationId:{}",
            userId,
            conversationId
        )

        val token = loginUser()
        val path = "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}" +
            "/members/${userId.domain}/${userId.id}"

        httpClient.delete(path) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }

        logger.info(
            "App user is removed from the conversation in the backend. " +
                "userId:{}, conversationId:{}",
            userId,
            conversationId
        )
    }

    override suspend fun deleteConversation(
        teamId: TeamId,
        conversationId: QualifiedId
    ) {
        logger.info(
            "Conversation will be deleted in the backend. teamId:{}, conversationId:{}",
            teamId,
            conversationId
        )

        val token = loginUser()
        val path = "/$API_VERSION/teams/${teamId.value}/conversations/${conversationId.id}"

        httpClient.delete(path) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }

        logger.info(
            "Conversation is deleted in the backend. teamId:{}, conversationId:{}",
            teamId,
            conversationId
        )
    }

    override suspend fun getLastNotification(): EventResponse {
        val token = loginUser()
        val lastNotification = httpClient.get("notifications/last") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            appStorage.getDeviceId()?.let { parameter(CLIENT_QUERY_KEY, it) }
        }.body<EventResponse>()

        return lastNotification
    }

    override suspend fun getPaginatedNotifications(
        querySize: Int,
        querySince: String?
    ): NotificationsResponse {
        val token = loginUser()
        try {
            val notifications = httpClient.get("notifications") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
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

    override suspend fun getClientsByUserId(userId: QualifiedId): List<UserClientResponse> {
        val token = loginUser()
        val clients = httpClient.get("/users/${userId.domain}/${userId.id}/clients") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }.body<List<UserClientResponse>>()

        return clients
    }

    override suspend fun getClientsByUserIds(
        userIds: List<QualifiedId>
    ): Map<QualifiedId, List<UserClientResponse>> {
        val token = loginUser()
        val response = httpClient.post("/users/list-clients") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
            setBody(userIds)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<Map<String, Map<String, List<UserClientResponse>>>>()

        val usersClients = response.flatMap { (domain, users) ->
            users.map { (userId, clients) ->
                QualifiedId(UUID.fromString(userId), domain) to clients
            }
        }.toMap()

        return usersClients
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
        const val CONVERSATION_LIST_IDS_PAGING_SIZE = 100

        const val ACCESS_TOKEN_QUERY_KEY = "access_token"
        const val SIZE_QUERY_KEY = "size"
        const val CLIENT_QUERY_KEY = "client"
        const val SINCE_QUERY_KEY = "since"

        private const val FETCH_CONVERSATIONS_START_INDEX = 0
        private const val FETCH_CONVERSATIONS_END_INDEX = 1000
        private const val FETCH_CONVERSATIONS_INCREASE_INDEX = 1000
    }
}

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int
)
