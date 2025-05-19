/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.integrations.jvm.service

import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.AssetResource
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.ConversationMember
import com.wire.integrations.jvm.model.EncryptionKey
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.WireMessage.Asset.AssetMetadata
import com.wire.integrations.jvm.model.asset.AssetRetention
import com.wire.integrations.jvm.model.asset.AssetUploadData
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.protobuf.ProtobufSerializer
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.jvm.utils.AESDecrypt
import com.wire.integrations.jvm.utils.AESEncrypt
import com.wire.integrations.jvm.utils.MAX_DATA_SIZE
import kotlinx.coroutines.runBlocking
import java.util.UUID

/**
 * Allows fetching common data and interacting with each Team instance invited to the Application.
 * Some functions are provided as blocking methods for Java interoperability or
 * as suspending methods for Kotlin consumers.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val conversationStorage: ConversationStorage,
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient
) {
    fun getStoredTeams(): List<TeamId> = teamStorage.getAll()

    fun getStoredConversations(): List<ConversationData> = conversationStorage.getAll()

    fun getStoredConversationMembers(conversationId: QualifiedId): List<ConversationMember> =
        conversationStorage.getMembersByConversationId(conversationId)

    /**
     * Get API configuration from the connected Wire backend.
     * Blocking method for Java interoperability
     */
    @Throws(WireException::class)
    fun getBackendConfiguration(): ApiVersionResponse =
        runBlocking {
            getBackendConfigurationSuspending()
        }

    /**
     * Get API configuration from the connected Wire backend.
     * Suspending method for Kotlin consumers
     */
    @Throws(WireException::class)
    suspend fun getBackendConfigurationSuspending(): ApiVersionResponse =
        backendClient.getAvailableApiVersions()

    /**
     * Get the basic Wire Application data from the connected Wire backend.
     * Blocking method for Java interoperability
     */
    @Throws(WireException::class)
    fun getApplicationData(): AppDataResponse =
        runBlocking {
            getApplicationDataSuspending()
        }

    /**
     * Get the basic Wire Application data from the connected Wire backend.
     * Suspending method for Kotlin consumers
     */
    @Throws(WireException::class)
    suspend fun getApplicationDataSuspending(): AppDataResponse = backendClient.getApplicationData()

    /**
     * Sends a message to a conversation by getting the mlsGroupId and encrypting the message.
     *
     * Before calling this function, make sure that the conversation
     * has already been joined; otherwise, the message cannot be sent.
     *
     * The conversation ID can be retrieved from internal storage, but this is not required—if you
     * already have it from another source (from receiving events), you can use that instead.
     *
     * Blocking method for Java interoperability
     *
     * @param message The text of the message to be sent.
     * @throws WireException.EntityNotFound If the conversation cannot be found.
     * @return the id of the message sent, useful to edit/delete it later.
     */
    fun sendMessage(message: WireMessage): UUID {
        return runBlocking {
            sendMessageSuspending(message)
        }
    }

    /**
     * Sends a message to a conversation by getting the mlsGroupId and encrypting the message.
     *
     * Before calling this function, make sure that the conversation
     * has already been joined; otherwise, the message cannot be sent.
     *
     * The conversation ID can be retrieved from internal storage, but this is not required—if you
     * already have it from another source (from receiving events), you can use that instead.
     *
     * Suspending method for Kotlin consumers.
     *
     * @param message The text of the message to be sent.
     * @throws WireException.EntityNotFound If the conversation cannot be found.
     * @return the id of the message sent, useful to edit/delete it later.
     */
    suspend fun sendMessageSuspending(message: WireMessage): UUID {
        val conversation = conversationStorage.getById(conversationId = message.conversationId)
        conversation?.mlsGroupId?.let { mlsGroupId ->
            backendClient.sendMessage(
                mlsMessage = cryptoClient.encryptMls(
                    mlsGroupId = mlsGroupId,
                    message = ProtobufSerializer.toGenericMessageByteArray(wireMessage = message)
                )
            )
        } ?: throw WireException.EntityNotFound("Couldn't find Conversation MLS Group ID")
        return message.id
    }

    /**
     * Downloads an asset as a raw byte array.
     *
     * @param assetRemoteData the data required to identify and decrypt the asset.
     * You can use the AssetRemoteData directly inside WireMessage.Asset when an asset is received.
     * @return A [AssetResource] containing the raw binary content of the downloaded asset. You can
     * parse it to a file and store it.
     *
     * Blocking method for Java interoperability
     *
     * @throws [WireException] If the request fails or an error occurs while fetching the asset.
     */
    fun downloadAsset(assetRemoteData: AssetMetadata.RemoteData): AssetResource {
        return runBlocking {
            downloadAssetSuspending(assetRemoteData)
        }
    }

    /**
     * Downloads an asset as a raw byte array.
     *
     * @param assetRemoteData the data required to identify and decrypt the asset.
     * You can use the AssetRemoteData directly inside WireMessage.Asset when an asset is received.
     * @return A [AssetResource] containing the raw binary content of the downloaded asset. You can
     * parse it to a file and store it.
     *
     * Suspending method for Kotlin consumers.
     *
     * @throws [WireException] If the request fails or an error occurs while fetching the asset.
     */
    suspend fun downloadAssetSuspending(assetRemoteData: AssetMetadata.RemoteData): AssetResource {
        val encryptedAsset = backendClient.downloadAsset(
            assetId = assetRemoteData.assetId,
            assetDomain = assetRemoteData.assetDomain,
            assetToken = assetRemoteData.assetToken
        )

        val calculatedSha256 = AESEncrypt.calculateSha256Hash(encryptedAsset)
        if (!assetRemoteData.sha256.contentEquals(calculatedSha256)) {
            throw WireException.InvalidParameter("Asset checksums do not match")
        }

        val decryptedAsset = AESDecrypt.decryptData(encryptedAsset, assetRemoteData.otrKey)
        return AssetResource(decryptedAsset)
    }

    /**
     * Uploads an asset and sends it as a message to the specified conversation.
     *
     * Blocking method for Java interoperability
     *
     * @return The encryption key used to encrypt the asset. Might be useful if you want to
     * download the file later, other clients will receive the same key automatically.
     */
    fun uploadAndSendMessage(
        conversationId: QualifiedId,
        asset: AssetResource,
        mimeType: String,
        retention: AssetRetention
    ): EncryptionKey {
        return runBlocking {
            uploadAndSendMessageSuspending(
                conversationId = conversationId,
                asset = asset,
                mimeType = mimeType,
                retention = retention
            )
        }
    }

    /**
     * Uploads an asset and sends it as a message to the specified conversation.
     *
     * Suspending method for Kotlin consumers.
     *
     * @return The encryption key used to encrypt the asset. Might be useful if you want to
     * download the file later, other clients will receive the same key automatically.
     */
    suspend fun uploadAndSendMessageSuspending(
        conversationId: QualifiedId,
        asset: AssetResource,
        mimeType: String,
        retention: AssetRetention
    ): EncryptionKey {
        // Determine max size
        if (asset.value.size > MAX_DATA_SIZE) {
            throw WireException.InvalidParameter("Asset size exceeds the maximum limit")
        }

        // Encryption with AES
        val newAesKey = AESEncrypt.generateRandomAES256Key()
        val encryptedAsset = AESEncrypt.encryptData(asset.value, newAesKey)
        val assetUploadData = AssetUploadData(
            public = false,
            retention = retention,
            md5 = AESEncrypt.calculateSha256Hash(encryptedAsset)
        )

        // Upload the asset
        val assetUploadResponse = backendClient.uploadAsset(
            encryptedFile = encryptedAsset,
            encryptedFileLength = encryptedAsset.size.toLong(),
            assetUploadData = assetUploadData
        )

        // Build WireMessage.Asset
        val assetMessage = WireMessage.Asset(
            id = UUID.randomUUID(),
            conversationId = conversationId,
            sender = IsolatedKoinContext.getApplicationQualifiedId(),
            sizeInBytes = encryptedAsset.size.toLong(),
            mimeType = mimeType,
            remoteData = AssetMetadata.RemoteData(
                assetId = assetUploadResponse.key,
                assetDomain = assetUploadResponse.domain,
                assetToken = assetUploadResponse.token,
                otrKey = newAesKey,
                sha256 = assetUploadData.md5
            )
        )

        // Send the asset
        sendMessageSuspending(assetMessage)
        return EncryptionKey(newAesKey)
    }
}
