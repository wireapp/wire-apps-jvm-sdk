package com.wire.sdk.crypto

import com.wire.crypto.Ciphersuite
import com.wire.crypto.Ciphersuites
import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.DatabaseKey
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsTransport
import com.wire.crypto.Welcome
import com.wire.crypto.toClientId
import com.wire.crypto.toExternalSenderKey
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.client.PreKeyCrypto
import com.wire.sdk.model.http.client.toCryptography
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Base64

/**
 * Internal use only, use the factory function [create] to create a new instance.
 */
internal class CoreCryptoClient private constructor(
    private val ciphersuite: Ciphersuite,
    private var coreCrypto: CoreCrypto
) : CryptoClient {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var cryptoClientId: CryptoClientId? = null

    private fun setAppClientId(cryptoClientId: CryptoClientId) {
        this@CoreCryptoClient.cryptoClientId = cryptoClientId
    }

    override fun getAppClientId(): CryptoClientId? = cryptoClientId

    override suspend fun encryptMls(
        mlsGroupId: MLSGroupId,
        message: ByteArray
    ): ByteArray {
        val encryptedMessage =
            coreCrypto.transaction {
                it.encryptMessage(
                    mlsGroupId,
                    message
                )
            }
        return encryptedMessage
    }

    override suspend fun decryptMls(
        mlsGroupId: MLSGroupId,
        encryptedMessage: String
    ): ByteArray? {
        val encryptedMessageBytes: ByteArray = Base64.getDecoder().decode(encryptedMessage)
        val decryptedMessage =
            coreCrypto.transaction {
                it.decryptMessage(
                    id = mlsGroupId,
                    message = encryptedMessageBytes
                )
            }
        return decryptedMessage.message
    }

    override suspend fun initializeProteusClient() =
        coreCrypto.transaction {
            it.proteusInit()
        }

    override suspend fun generateProteusPreKeys(
        from: Int,
        count: Int
    ): ArrayList<PreKeyCrypto> =
        coreCrypto.transaction { crypto ->
            crypto.proteusNewPreKeys(from, count).map {
                it.toCryptography()
            } as ArrayList<PreKeyCrypto>
        }

    override suspend fun generateProteusLastPreKey(): PreKeyCrypto =
        coreCrypto.transaction { context ->
            context.proteusNewLastPreKey().toCryptography()
        }

    override suspend fun initializeMlsClient(
        cryptoClientId: CryptoClientId,
        mlsTransport: MlsTransport
    ) {
        coreCrypto.transaction {
            it.mlsInit(
                cryptoClientId.value.toClientId(),
                Ciphersuites(setOf(ciphersuite))
            )
        }

        coreCrypto.provideTransport(mlsTransport)

        setAppClientId(cryptoClientId = cryptoClientId)
    }

    override suspend fun mlsGetPublicKey(): MlsPublicKeys {
        val key =
            coreCrypto.transaction {
                it.getPublicKey(ciphersuite = ciphersuite)
            }
        val encodedKey = Base64.getEncoder().encodeToString(key)
        return when (ciphersuite) {
            Ciphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> {
                MlsPublicKeys(ecdsaSecp256r1Sha256 = encodedKey)
            }

            Ciphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> {
                MlsPublicKeys(ecdsaSecp384r1Sha384 = encodedKey)
            }

            Ciphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> {
                MlsPublicKeys(ecdsaSecp521r1Sha512 = encodedKey)
            }

            Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519,
            Ciphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 -> {
                MlsPublicKeys(ed25519 = encodedKey)
            }

            Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448,
            Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 -> {
                throw WireException.CryptographicSystemError("Unsupported ciphersuite")
            }
        }
    }

    override suspend fun mlsGenerateKeyPackages(packageCount: UInt): List<MLSKeyPackage> {
        return coreCrypto.transaction {
            it.generateKeyPackages(
                amount = packageCount,
                ciphersuite = ciphersuite
            )
        }
    }

    override suspend fun joinMlsConversationRequest(groupInfo: GroupInfo): MLSGroupId {
        return coreCrypto.transaction { it.joinByExternalCommit(groupInfo).id }
    }

    /**
     * Creates a conversation in CoreCrypto.
     *
     * @param MLSGroupId Group ID from creating the conversation on the backend
     * @param externalSenders Keys fetched from backend for validating external remove proposals
     */
    override suspend fun createConversation(
        groupId: MLSGroupId,
        externalSenders: ByteArray
    ) {
        return coreCrypto.transaction {
            it.createConversation(
                id = groupId,
                ciphersuite = ciphersuite,
                externalSenders = listOf(
                    externalSenders.toExternalSenderKey()
                )
            )
        }
    }

    override suspend fun updateKeyingMaterial(mlsGroupId: MLSGroupId) {
        coreCrypto.transaction {
            it.updateKeyingMaterial(mlsGroupId)
        }
    }

    override suspend fun addMemberToMlsConversation(
        mlsGroupId: MLSGroupId,
        keyPackages: List<MLSKeyPackage>
    ) {
        coreCrypto.transaction {
            it.addMember(mlsGroupId, keyPackages)
        }
    }

    override suspend fun removeMembersFromConversation(
        mlsGroupId: MLSGroupId,
        clientIds: List<CryptoClientId>
    ) {
        coreCrypto.transaction {
            it.removeMember(
                id = mlsGroupId,
                members = clientIds.map { client ->
                    ClientId(client.value.toByteArray())
                }
            )
        }
    }

    override suspend fun processWelcomeMessage(welcome: Welcome): MLSGroupId {
        val welcomeBundle = coreCrypto.transaction { it.processWelcomeMessage(welcome) }
        return welcomeBundle.id
    }

    override suspend fun hasTooFewKeyPackageCount(): Boolean {
        val packageCount = coreCrypto.transaction { it.validKeyPackageCount(ciphersuite) }
        return packageCount < CryptoClient.Companion.DEFAULT_KEYPACKAGE_COUNT / 2u
    }

    override suspend fun conversationExists(mlsGroupId: MLSGroupId): Boolean =
        coreCrypto.transaction {
            it.conversationExists(mlsGroupId)
        }

    override suspend fun conversationEpoch(mlsGroupId: MLSGroupId): ULong =
        coreCrypto.transaction {
            it.conversationEpoch(mlsGroupId)
        }

    override suspend fun wipeConversation(mlsGroupId: MLSGroupId) {
        logger.debug("Conversation will be deleted from CoreCrypto. mlsGroupId: {}", mlsGroupId)
        coreCrypto.transaction {
            it.wipeConversation(mlsGroupId)
            logger.debug("Conversation is deleted from CoreCrypto. mlsGroupId: {}", mlsGroupId)
        }
    }

    override fun close() {
        runBlocking { coreCrypto.close() }
    }

    companion object {
        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val KEYSTORE_NAME = "keystore"

        suspend fun create(
            userId: String,
            ciphersuiteCode: Int = DEFAULT_CIPHERSUITE_IDENTIFIER
        ): CoreCryptoClient {
            val clientDirectoryPath = "storage/cryptography/$userId"
            val keystorePath = "$clientDirectoryPath/$KEYSTORE_NAME"
            val ciphersuite = getMlsCipherSuiteName(ciphersuiteCode)

            File(clientDirectoryPath).mkdirs()

            val coreCrypto = CoreCrypto.invoke(
                keystore = keystorePath,
                databaseKey = IsolatedKoinContext.getCryptographyStoragePassword()
                    ?.let { DatabaseKey(it) }
                    ?: throw WireException.InvalidParameter("Cryptography password missing")
            )

            return CoreCryptoClient(
                ciphersuite = ciphersuite,
                coreCrypto = coreCrypto
            )
        }

        fun getMlsCipherSuiteName(code: Int): Ciphersuite =
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

        @Suppress("MagicNumber")
        fun Int.toHexString(minDigits: Int = 4): String {
            return "0x" + this.toString(16).padStart(minDigits, '0')
        }
    }
}
