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

import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.asset.AssetUploadData
import com.wire.integrations.jvm.model.asset.AssetUploadResponse
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.http.FeaturesResponse
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.model.http.client.RegisterClientRequest
import com.wire.integrations.jvm.model.http.client.RegisterClientResponse
import com.wire.integrations.jvm.model.http.conversation.ClaimedKeyPackageList
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import com.wire.integrations.jvm.model.http.conversation.CreateConversationRequest
import com.wire.integrations.jvm.model.http.conversation.MlsPublicKeysResponse
import com.wire.integrations.jvm.model.http.user.UserResponse
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

    companion object {
        const val API_VERSION = "v9"
    }
}
