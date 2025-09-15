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

package com.wire.sdk.utils

import com.wire.crypto.ClientId
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.CredentialType
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.DeviceStatus
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.MlsTransport
import com.wire.crypto.Welcome
import com.wire.crypto.WireIdentity
import com.wire.crypto.toGroupId
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.AppClientId
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.client.PreKeyCrypto
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.util.Base64
import java.util.UUID

internal class MockCoreCryptoClient : CryptoClient {
    val conversationExist = mutableSetOf<MLSGroupId>()
    private var appClientId: AppClientId? = null

    fun setAppClientId(appClientId: AppClientId) {
        this.appClientId = appClientId
    }

    override fun getAppClientId(): AppClientId? = appClientId

    override suspend fun initializeProteusClient() {
        // Do nothing
    }

    override suspend fun generateProteusPreKeys(
        from: Int,
        count: Int
    ): ArrayList<PreKeyCrypto> {
        val preKeys = arrayListOf<PreKeyCrypto>()
        for (i in from..count) {
            preKeys.add(
                PreKeyCrypto(
                    id = i,
                    encodedData = "encoded_data_$i"
                )
            )
        }

        return preKeys
    }

    override suspend fun generateProteusLastPreKey(): PreKeyCrypto =
        PreKeyCrypto(
            id = 0,
            encodedData = "encoded_data_last_key"
        )

    override suspend fun initializeMlsClient(
        appClientId: AppClientId,
        mlsTransport: MlsTransport
    ) {
        // Do nothing
    }

    override suspend fun decryptMls(
        mlsGroupId: MLSGroupId,
        encryptedMessage: String
    ): DecryptedMessage =
        DecryptedMessage(
            message = GENERIC_TEXT_MESSAGE.toByteArray(),
            isActive = true,
            commitDelay = null,
            hasEpochChanged = false,
            senderClientId = ClientId(UUID.randomUUID().toString().toByteArray()),
            identity = WireIdentity(
                clientId = UUID.randomUUID().toString(),
                status = DeviceStatus.Valid,
                thumbprint = UUID.randomUUID().toString(),
                credentialType = CredentialType.Companion.DEFAULT,
                x509Identity = null
            ),
            bufferedMessages = null,
            crlNewDistributionPoints = null
        )

    // Throw OrphanWelcome, testing the fallback to createJoinMlsConversationRequest
    override suspend fun processWelcomeMessage(welcome: Welcome): MLSGroupId =
        throw CoreCryptoException.Mls(MlsException.OrphanWelcome())

    // Mock joining the conversation, assume the backend accepts the invitation
    override suspend fun joinMlsConversationRequest(groupInfo: GroupInfo): MLSGroupId = MLS_GROUP_ID

    override suspend fun encryptMls(
        mlsGroupId: MLSGroupId,
        message: ByteArray
    ): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun mlsGetPublicKey(): MlsPublicKeys {
        TODO("Not yet implemented")
    }

    override suspend fun mlsGenerateKeyPackages(packageCount: UInt): List<MLSKeyPackage> {
        TODO("Not yet implemented")
    }

    override suspend fun hasTooFewKeyPackageCount(): Boolean = false

    override fun close() {
        // Do nothing
    }

    override suspend fun createConversation(
        groupId: MLSGroupId,
        externalSenders: ByteArray
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateKeyingMaterial(mlsGroupId: MLSGroupId) {
        TODO("Not yet implemented")
    }

    override suspend fun addMemberToMlsConversation(
        mlsGroupId: MLSGroupId,
        keyPackages: List<MLSKeyPackage>
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun conversationExists(mlsGroupId: MLSGroupId): Boolean {
        val wasConversationAdded = conversationExist.add(mlsGroupId)
        return !wasConversationAdded
    }

    override suspend fun conversationEpoch(mlsGroupId: MLSGroupId): ULong = 0UL

    companion object {
        val MLS_GROUP_ID = UUID.randomUUID().toString().toGroupId()
        val MLS_GROUP_ID_BASE64 = Base64.getEncoder().encodeToString(MLS_GROUP_ID.copyBytes())
        val GENERIC_TEXT_MESSAGE: GenericMessage = GenericMessage
            .newBuilder()
            .setMessageId(UUID.randomUUID().toString())
            .setText(
                Messages.Text.newBuilder()
                    .setContent("Decrypted message content")
                    .build()
            )
            .build()
    }
}
