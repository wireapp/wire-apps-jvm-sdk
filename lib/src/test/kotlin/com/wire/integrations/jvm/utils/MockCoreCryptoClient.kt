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

package com.wire.integrations.jvm.utils

import com.wire.crypto.Ciphersuite
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.DatabaseKey
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.MlsTransport
import com.wire.crypto.Welcome
import com.wire.crypto.toGroupId
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.exception.WireException.InvalidParameter
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.model.http.client.PreKeyCrypto
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.io.File
import java.util.Base64
import java.util.UUID

internal class MockCoreCryptoClient private constructor(
    private val ciphersuite: Ciphersuite,
    private var coreCrypto: CoreCrypto
) : CryptoClient {
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
    ): ByteArray = GENERIC_TEXT_MESSAGE.toByteArray()

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
        suspend fun create(
            userId: String,
            ciphersuiteCode: Int = DEFAULT_CIPHERSUITE_IDENTIFIER
        ): MockCoreCryptoClient {
            val clientDirectoryPath = "storage/cryptography/$userId"
            val keystorePath = "$clientDirectoryPath/$KEYSTORE_NAME"
            val ciphersuite = getMlsCipherSuiteName(ciphersuiteCode)

            File(clientDirectoryPath).mkdirs()

            val coreCrypto = CoreCrypto.invoke(
                keystore = keystorePath,
                databaseKey = IsolatedKoinContext.getCryptographyStoragePassword()
                    ?.let { DatabaseKey(it) }
                    ?: throw InvalidParameter("Cryptography password missing")
            )

            return MockCoreCryptoClient(
                ciphersuite = ciphersuite,
                coreCrypto = coreCrypto
            )
        }

        private fun getMlsCipherSuiteName(code: Int): Ciphersuite =
            when (code) {
                DEFAULT_CIPHERSUITE_IDENTIFIER -> Ciphersuite.DEFAULT
                2 -> Ciphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                3 -> Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
                4 -> Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
                5 -> Ciphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
                6 -> Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
                7 -> Ciphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                else -> Ciphersuite.DEFAULT
            }

        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val KEYSTORE_NAME = "keystore"
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
