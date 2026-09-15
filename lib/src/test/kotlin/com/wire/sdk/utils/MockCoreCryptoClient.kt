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

import com.wire.crypto.CipherSuite
import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.Database
import com.wire.crypto.DatabaseKey
import com.wire.crypto.GroupInfo
import com.wire.crypto.KeyPackage
import com.wire.crypto.MlsTransport
import com.wire.crypto.Welcome
import com.wire.crypto.open
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.DecryptedMlsMessage
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.client.PreKeyCrypto
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.io.File
import java.util.Base64
import java.util.UUID

internal class MockCoreCryptoClient private constructor(
    private val cipherSuite: CipherSuite,
    private var coreCryptoClient: CoreCrypto
) : CryptoClient {
    val conversationExist = mutableSetOf<ConversationId>()
    private var cryptoClientId: CryptoClientId? = null

    fun setCryptoClientId(cryptoClientId: CryptoClientId) {
        this.cryptoClientId = cryptoClientId
    }

    override fun getCryptoClientId(): CryptoClientId? = cryptoClientId

    override suspend fun initializeProteusClient() {
        // Do nothing
    }

    override suspend fun generateProteusPreKeys(
        from: Int,
        count: Int
    ): List<PreKeyCrypto> {
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
        cryptoClientId: CryptoClientId,
        mlsTransport: MlsTransport
    ) {
        // Do nothing
    }

    override suspend fun decryptMls(
        mlsGroupId: ConversationId,
        encryptedMessage: String
    ): DecryptedMlsMessage =
        DecryptedMlsMessage(
            message = GENERIC_TEXT_MESSAGE.toByteArray(),
            sender = DEFAULT_SENDER
        )

    override suspend fun encryptMls(
        mlsGroupId: ConversationId,
        message: ByteArray
    ): ByteArray {
        TODO("Not yet implemented")
    }

    override suspend fun mlsGetPublicKey(): MlsPublicKeys {
        TODO("Not yet implemented")
    }

    override suspend fun mlsGenerateKeyPackages(packageCount: UInt): List<KeyPackage> {
        TODO("Not yet implemented")
    }

    override suspend fun joinMlsConversationRequest(groupInfo: GroupInfo) {
        TODO("Not yet implemented")
    }

    override suspend fun hasTooFewKeyPackageCount(): Boolean = false

    override fun close() {
        // Do nothing
    }

    override suspend fun createConversation(
        mlsGroupId: ConversationId,
        externalSenders: ByteArray
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun updateKeyingMaterial(mlsGroupId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun addClientsToMlsConversation(
        mlsGroupId: ConversationId,
        keyPackages: List<KeyPackage>
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun conversationExists(mlsGroupId: ConversationId): Boolean {
        val wasConversationAdded = conversationExist.add(mlsGroupId)
        return !wasConversationAdded
    }

    override suspend fun conversationEpoch(mlsGroupId: ConversationId): ULong = 0UL

    override suspend fun wipeConversation(mlsGroupId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun removeClientsFromConversation(
        mlsGroupId: ConversationId,
        clientIds: List<CryptoClientId>
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun processWelcomeMessage(welcome: Welcome) {
        // Do nothing
    }

    companion object {
        suspend fun create(
            userId: String,
            cipherSuiteCode: Int = DEFAULT_CIPHERSUITE_IDENTIFIER
        ): MockCoreCryptoClient {
            val clientDirectoryPath = "storage/cryptography/$userId"
            val keystorePath = "$clientDirectoryPath/$KEYSTORE_NAME"
            val ciphersuite = getMlsCipherSuiteName(cipherSuiteCode)

            File(clientDirectoryPath).mkdirs()

            val coreCryptoClient = CoreCrypto.invoke(
                database = Database.open(
                    location = keystorePath,
                    key = DatabaseKey(IsolatedKoinContext.getCryptographyStorageKey())
                )
            )

            return MockCoreCryptoClient(
                cipherSuite = ciphersuite,
                coreCryptoClient = coreCryptoClient
            )
        }

        fun getMlsCipherSuiteName(code: Int): CipherSuite =
            when (code) {
                DEFAULT_CIPHERSUITE_IDENTIFIER ->
                    CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519
                2 -> CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                3 -> CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519
                4 -> CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_ED448
                5 -> CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
                6 -> CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448
                7 -> CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                else -> CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519
            }

        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val KEYSTORE_NAME = "keystore"
        private val DEFAULT_SENDER = QualifiedId(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            domain = "wire.test"
        )
        val MLS_GROUP_ID = ConversationId(UUID.randomUUID().toString().toByteArray())
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
