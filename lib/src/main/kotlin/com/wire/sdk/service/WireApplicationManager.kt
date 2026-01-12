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

package com.wire.sdk.service

import com.wire.sdk.client.BackendClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.AssetResource
import com.wire.sdk.model.Conversation
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.EncryptionKey
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.asset.AssetRetention
import com.wire.sdk.model.asset.AssetUploadData
import com.wire.sdk.model.conversation.AddMembersToConversationResult
import com.wire.sdk.model.http.ApiVersionResponse
import com.wire.sdk.model.http.AppDataResponse
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.model.http.user.UserResponse
import com.wire.sdk.model.protobuf.ProtobufSerializer
import com.wire.sdk.persistence.TeamStorage
import com.wire.sdk.service.conversation.ConversationService
import com.wire.sdk.utils.AESDecrypt
import com.wire.sdk.utils.AESEncrypt
import com.wire.sdk.utils.MAX_DATA_SIZE
import java.io.ByteArrayInputStream
import java.util.UUID
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Allows fetching common data and interacting with each Team instance invited to the Application.
 * Some functions are provided as blocking methods for Java interoperability or
 * as suspending methods for Kotlin consumers.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient,
    private val mlsFallbackStrategy: MlsFallbackStrategy,
    private val conversationService: ConversationService
) {
    fun getStoredTeams(): List<TeamId> = teamStorage.getAll()

    fun getStoredConversations(): List<Conversation> =
        conversationService
            .getAll()
            .filter { it.type != ConversationEntity.Type.SELF }
            .map { Conversation.fromEntity(it) }

    fun getStoredConversationMembers(conversationId: QualifiedId): List<ConversationMember> =
        conversationService.getStoredConversationMembers(conversationId = conversationId)

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
        val conversation = conversationService.getConversationById(
            conversationId = message.conversationId
        )

        conversation.mlsGroupId.let { mlsGroupId ->
            val encryptedMessage = cryptoClient.encryptMls(
                mlsGroupId = mlsGroupId,
                message = ProtobufSerializer
                    .toGenericMessageByteArray(
                        wireMessage = message
                    )
            )

            try {
                backendClient.sendMessage(mlsMessage = encryptedMessage)
            } catch (exception: WireException.ClientError) {
                if (exception.response.isMlsStaleMessage()) {
                    mlsFallbackStrategy.verifyConversationOutOfSync(
                        mlsGroupId = mlsGroupId,
                        conversationId = message.conversationId
                    )
                    backendClient.sendMessage(mlsMessage = encryptedMessage)
                }
            }
        }
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
    @JvmName("downloadAsset")
    fun downloadAsset(assetRemoteData: WireMessage.Asset.RemoteData): AssetResource {
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
    suspend fun downloadAssetSuspending(
        assetRemoteData: WireMessage.Asset.RemoteData
    ): AssetResource {
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
    @Suppress("LongParameterList")
    fun sendAsset(
        conversationId: QualifiedId,
        asset: AssetResource,
        metadata: WireMessage.Asset.AssetMetadata? = null,
        name: String,
        mimeType: String,
        retention: AssetRetention
    ): EncryptionKey {
        return runBlocking {
            sendAssetSuspending(
                conversationId = conversationId,
                asset = asset,
                metadata = metadata,
                name = name,
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
     * This method currently only handles the retrieval of an Image Asset, so for now we are also
     * receiving a metadata [AssetMetadata] object for other types (Audio | Video).
     *
     * @param conversationId The qualified ID of the conversation
     * @param asset [AssetResource] containing the ByteArray of the asset File
     * @param metadata [AssetMetadata] Metadata to display the asset in the UI
     * @param name Name of the asset
     * @param mimeType MimeType of the asset
     * @param retention [AssetRetention] What should be the retantion of the asset remotely
     *
     * @return The encryption key used to encrypt the asset. Might be useful if you want to
     * download the file later, other clients will receive the same key automatically.
     */
    @Suppress("LongParameterList")
    suspend fun sendAssetSuspending(
        conversationId: QualifiedId,
        asset: AssetResource,
        metadata: WireMessage.Asset.AssetMetadata? = null,
        name: String,
        mimeType: String,
        retention: AssetRetention
    ): EncryptionKey {
        // TODO: In a future implementation this method will be removed in order for the metadata
        //  to be retrieved internally and not received from external parameters.

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

        // Verify if Asset is an Image and get metadata
        val assetMetadata = if (WireMessage.Asset.isImageMimeType(mimeType = mimeType)) {
            withContext(Dispatchers.IO) {
                val image = ImageIO.read(ByteArrayInputStream(asset.value))
                WireMessage.Asset.AssetMetadata.Image(
                    width = image.width,
                    height = image.height
                )
            }
        } else {
            metadata
        }

        // Build WireMessage.Asset
        val assetMessage = WireMessage.Asset(
            id = UUID.randomUUID(),
            conversationId = conversationId,
            sender = QualifiedId(
                id = UUID.randomUUID(),
                domain = UUID.randomUUID().toString()
            ),
            name = name,
            sizeInBytes = encryptedAsset.size.toLong(),
            mimeType = mimeType,
            metadata = assetMetadata,
            remoteData = WireMessage.Asset.RemoteData(
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

    /**
     * Get a Wire user's data
     * Blocking method for Java interoperability
     */
    @Throws(WireException::class)
    fun getUser(userId: QualifiedId): UserResponse =
        runBlocking {
            getUserSuspending(userId)
        }

    /**
     * Get a Wire user's data
     * Suspending method for Kotlin consumers
     */
    @Throws(WireException::class)
    suspend fun getUserSuspending(userId: QualifiedId): UserResponse =
        backendClient.getUserData(userId)

    /**
     * Creates a Group Conversation where currently the only admin is the App
     *
     * @param name Name of the created conversation
     * @param userIds List of QualifiedId of all the users to be added to the conversation
     * (excluding the App user)
     *
     * @return QualifiedId The ID of the created conversation
     */
    fun createGroupConversation(
        name: String,
        userIds: List<QualifiedId>
    ): QualifiedId =
        runBlocking {
            createGroupConversationSuspending(
                name = name,
                userIds = userIds
            )
        }

    /**
     * See [createGroupConversation]
     */
    suspend fun createGroupConversationSuspending(
        name: String,
        userIds: List<QualifiedId>
    ): QualifiedId =
        conversationService.createGroup(
            name = name,
            userIds = userIds
        )

    /**
     * Creates a One To One Conversation with a user starting from the App
     *
     * @param userId QualifiedId of the user the App will create the conversation with
     *
     * @return QualifiedId The ID of the created conversation
     */
    fun createOneToOneConversation(userId: QualifiedId): QualifiedId =
        runBlocking {
            createOneToOneConversationSuspending(userId = userId)
        }

    /**
     * See [createOneToOneConversation]
     */
    suspend fun createOneToOneConversationSuspending(userId: QualifiedId): QualifiedId =
        conversationService.createOneToOne(userId = userId)

    /**
     * Creates a Channel Conversation where currently the only admin is the App and only the Admin
     * can invite others to join.
     *
     * @param name Name of the created conversation
     * @param userIds List of QualifiedId of all the users to be added to the conversation
     * (excluding the App user)
     *
     * @return QualifiedId The ID of the created conversation
     */
    fun createChannelConversation(
        name: String,
        userIds: List<QualifiedId>
    ): QualifiedId =
        runBlocking {
            createChannelConversationSuspending(
                name = name,
                userIds = userIds
            )
        }

    /**
     * See [createChannelConversation]
     */
    suspend fun createChannelConversationSuspending(
        name: String,
        userIds: List<QualifiedId>
    ): QualifiedId =
        conversationService.createChannel(
            name = name,
            userIds = userIds
        )

    /**
     * Updates the member role in a conversation, to be called only after a conversation is created
     * or established.
     *
     * @param conversationId ID of the conversation where the member is present
     * @param userId ID of the user where the role will be changed
     * @param newRole ConversationROle to be changed to
     */
    fun updateConversationMemberRole(
        conversationId: QualifiedId,
        userId: QualifiedId,
        newRole: ConversationRole
    ) {
        runBlocking {
            updateConversationMemberRoleSuspending(
                conversationId = conversationId,
                userId = userId,
                newRole = newRole
            )
        }
    }

    /**
     * See [updateConversationMemberRole]
     */
    suspend fun updateConversationMemberRoleSuspending(
        conversationId: QualifiedId,
        userId: QualifiedId,
        newRole: ConversationRole
    ) {
        conversationService.updateConversationMemberRole(
            conversationId = conversationId,
            userId = userId,
            newRole = newRole
        )
    }

    /**
     * Deletes the conversation that belongs to a team.
     * After successful completion, backend informs all participants in the channel.
     *
     * @param conversationId ID of the conversation where the member is present
     */
    fun deleteConversation(conversationId: QualifiedId) {
        runBlocking {
            deleteConversationSuspending(
                conversationId = conversationId
            )
        }
    }

    /**
     * See [deleteConversation]
     */
    suspend fun deleteConversationSuspending(conversationId: QualifiedId) {
        conversationService.deleteConversation(
            conversationId = conversationId
        )
    }

    /**
     * Leaves the conversation.
     * After successful completion, backend informs all participants in the channel.
     *
     * @param conversationId ID of the conversation where the member is present
     */
    fun leaveConversation(conversationId: QualifiedId) {
        runBlocking {
            leaveConversationSuspending(
                conversationId = conversationId
            )
        }
    }

    /**
     * See [leaveConversation]
     */
    suspend fun leaveConversationSuspending(conversationId: QualifiedId) {
        conversationService.leaveConversation(
            conversationId = conversationId
        )
    }

    /**
     * Adds members to a Group or Channel conversation.
     *
     * If invoked for a One To One conversation it will throw [WireException.Forbidden]
     *
     * @param conversationId ID of the conversation where the members will be added
     * @param members List of ID of the members to be added to the conversation
     *
     * @return AddMembersToConversationResult containing success and failed users
     *
     * @throws WireException.Forbidden
     */
    fun addMembersToConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ): AddMembersToConversationResult =
        runBlocking {
            addMembersToConversationSuspending(
                conversationId = conversationId,
                members = members
            )
        }

    /**
     * See [addMembersToConversation]
     */
    suspend fun addMembersToConversationSuspending(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ): AddMembersToConversationResult =
        conversationService.addMembersToConversation(
            conversationId = conversationId,
            members = members
        )

    /**
     * Remove members from a Group or Channel conversation
     *
     * If invoked in for a One To One conversation it will throw [WireException.Forbidden]
     *
     * @param conversationId ID of the conversation where the members will be removed
     * @param members List of ID of the members to be removed from the conversation
     *
     * @throws WireException.Forbidden
     */
    fun removeMembersFromConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        runBlocking {
            removeMembersFromConversationSuspending(
                conversationId = conversationId,
                members = members
            )
        }
    }

    /**
     * See [removeMembersFromConversation]
     */
    suspend fun removeMembersFromConversationSuspending(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        conversationService.removeMembersFromConversation(
            conversationId = conversationId,
            members = members
        )
    }
}
