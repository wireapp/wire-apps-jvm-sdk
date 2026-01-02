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

import com.wire.sdk.model.AppClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.asset.AssetUploadData
import com.wire.sdk.model.asset.AssetUploadResponse
import com.wire.sdk.model.http.ApiVersionResponse
import com.wire.sdk.model.http.AppDataResponse
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.FeaturesResponse
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.NotificationsResponse
import com.wire.sdk.model.http.client.RegisterClientRequest
import com.wire.sdk.model.http.client.RegisterClientResponse
import com.wire.sdk.model.http.conversation.ClaimedKeyPackageList
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.MlsPublicKeysResponse
import com.wire.sdk.model.http.conversation.OneToOneConversationResponse
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import com.wire.sdk.model.http.user.SelfUserResponse
import com.wire.sdk.model.http.user.UserResponse
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession

interface BackendClient {
    suspend fun connectWebSocket(handleFrames: suspend (DefaultClientWebSocketSession) -> Unit)

    suspend fun getAvailableApiVersions(): ApiVersionResponse

    suspend fun getApplicationData(): AppDataResponse

    suspend fun getApplicationFeatures(): FeaturesResponse

    suspend fun confirmTeam(teamId: TeamId)

    suspend fun updateClientWithMlsPublicKey(
        appClientId: AppClientId,
        mlsPublicKeys: MlsPublicKeys
    )

    suspend fun registerClient(registerClientRequest: RegisterClientRequest): RegisterClientResponse

    suspend fun uploadMlsKeyPackages(
        appClientId: AppClientId,
        mlsKeyPackages: List<ByteArray>
    )

    suspend fun claimKeyPackages(
        user: QualifiedId,
        cipherSuite: String
    ): ClaimedKeyPackageList

    suspend fun getPublicKeys(): MlsPublicKeysResponse

    suspend fun uploadCommitBundle(commitBundle: ByteArray)

    suspend fun sendMessage(mlsMessage: ByteArray)

    suspend fun getConversation(conversationId: QualifiedId): ConversationResponse

    suspend fun getUserData(userId: QualifiedId): UserResponse

    suspend fun getSelfUser(): SelfUserResponse

    suspend fun getConversationGroupInfo(conversationId: QualifiedId): ByteArray

    suspend fun downloadAsset(
        assetId: String,
        assetDomain: String,
        assetToken: String?
    ): ByteArray

    suspend fun uploadAsset(
        encryptedFile: ByteArray,
        encryptedFileLength: Long,
        assetUploadData: AssetUploadData
    ): AssetUploadResponse

    suspend fun createGroupConversation(
        createConversationRequest: CreateConversationRequest
    ): ConversationResponse

    suspend fun getOneToOneConversation(userId: QualifiedId): OneToOneConversationResponse

    suspend fun updateConversationMemberRole(
        conversationId: QualifiedId,
        userId: QualifiedId,
        updateConversationMemberRoleRequest: UpdateConversationMemberRoleRequest
    )

    suspend fun getConversationIds(): List<QualifiedId>

    suspend fun getConversationsById(conversationIds: List<QualifiedId>): List<ConversationResponse>

    suspend fun deleteConversation(
        teamId: TeamId,
        conversationId: QualifiedId
    )

    suspend fun getLastNotification(): EventResponse

    suspend fun getPaginatedNotifications(
        querySize: Int = NOTIFICATION_MINIMUM_QUERY_SIZE,
        querySince: String?
    ): NotificationsResponse

    companion object {
        const val API_VERSION = "v13"

        /**
         * The backend doesn't allow queries smaller than a minimum value.
         */
        const val NOTIFICATION_MINIMUM_QUERY_SIZE = 100
    }
}
