package com.wire.sdk.crypto

import com.wire.crypto.CUSTOM_CONFIGURATION_DEFAULT
import com.wire.crypto.Ciphersuite
import com.wire.crypto.ClientId
import com.wire.crypto.ConversationConfiguration
import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.CoreCryptoClient
import com.wire.crypto.CoreCryptoContext
import com.wire.crypto.CredentialType
import com.wire.crypto.DatabaseKey
import com.wire.crypto.GroupInfo
import com.wire.crypto.KeyPackage
import com.wire.crypto.MlsTransport
import com.wire.crypto.Welcome
import com.wire.crypto.invoke
import com.wire.crypto.toClientId
import com.wire.crypto.toExternalSenderKey
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.MlsCryptoClient.Companion.create
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.client.PreKeyCrypto
import io.ktor.util.encodeBase64
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.Base64

/**
 * Wrapper client on top of the client provided by the Core-Crypto library,
 * used for any MLS cryptographic operations.
 * Internal use only, use the factory function [create] to create a new instance.
 */
internal class MlsCryptoClient private constructor(
    private val ciphersuite: Ciphersuite,
    private var coreCryptoClient: CoreCryptoClient
) : CryptoClient {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var cryptoClientId: CryptoClientId? = null

    private fun setCryptoClientId(cryptoClientId: CryptoClientId) {
        this@MlsCryptoClient.cryptoClientId = cryptoClientId
    }

    override fun getCryptoClientId(): CryptoClientId? = cryptoClientId

    override suspend fun encryptMls(
        mlsGroupId: ConversationId,
        message: ByteArray
    ): ByteArray {
        val encryptedMessage =
            coreCryptoClient.transaction {
                it.encryptMessage(
                    mlsGroupId,
                    message
                )
            }
        return encryptedMessage
    }

    override suspend fun decryptMls(
        mlsGroupId: ConversationId,
        encryptedMessage: String
    ): ByteArray? {
        val encryptedMessageBytes: ByteArray = Base64.getDecoder().decode(encryptedMessage)
        val decryptedMessage =
            coreCryptoClient.transaction {
                it.decryptMessage(
                    conversationId = mlsGroupId,
                    payload = encryptedMessageBytes
                )
            }
        return decryptedMessage.message
    }

    override suspend fun initializeProteusClient() =
        coreCryptoClient.transaction {
            it.proteusInit()
        }

    override suspend fun generateProteusPreKeys(
        from: Int,
        count: Int
    ): List<PreKeyCrypto> =
        coreCryptoClient.transaction { crypto ->
            from.until(from + count).map {
                val pkb = crypto.proteusNewPrekey(it.toUShort())
                PreKeyCrypto(it, pkb.encodeBase64())
            }
        }

    override suspend fun generateProteusLastPreKey(): PreKeyCrypto =
        coreCryptoClient.transaction { context ->
            val id = context.proteusLastResortPrekeyId()
            val pkb = context.proteusLastResortPrekey()
            PreKeyCrypto(id.toInt(), pkb.encodeBase64())
        }

    override suspend fun initializeMlsClient(
        cryptoClientId: CryptoClientId,
        mlsTransport: MlsTransport
    ) {
        coreCryptoClient.transaction {
            it.mlsInit(
                clientId = cryptoClientId.value.toClientId(),
                ciphersuites = listOf(ciphersuite),
                nbKeyPackage = null
            )
        }

        coreCryptoClient.provideTransport(mlsTransport)

        setCryptoClientId(cryptoClientId = cryptoClientId)
    }

    override suspend fun mlsGetPublicKey(): MlsPublicKeys {
        val key =
            coreCryptoClient.transaction {
                it.clientPublicKey(
                    ciphersuite = ciphersuite,
                    credentialType = getCredentialType(it)
                )
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

            Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519,
            Ciphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519 -> {
                MlsPublicKeys(ed25519 = encodedKey)
            }

            Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_ED448,
            Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448 -> {
                throw WireException.CryptographicSystemError("Unsupported ciphersuite")
            }
        }
    }

    override suspend fun mlsGenerateKeyPackages(packageCount: UInt): List<KeyPackage> {
        return coreCryptoClient.transaction {
            val mlsCredentialType = getCredentialType(it)
            it.clientKeypackages(
                amountRequested = packageCount,
                ciphersuite = ciphersuite,
                credentialType = mlsCredentialType
            )
        }
    }

    override suspend fun joinMlsConversationRequest(groupInfo: GroupInfo): ConversationId {
        return coreCryptoClient.transaction {
            val mlsCredentialType = getCredentialType(it)
            it.joinByExternalCommit(
                groupInfo = groupInfo,
                credentialType = mlsCredentialType,
                customConfiguration = CUSTOM_CONFIGURATION_DEFAULT
            ).id
        }
    }

    /**
     * Creates a conversation in CoreCrypto.
     *
     * @param ConversationId Group ID from creating the conversation on the backend
     * @param externalSenders Keys fetched from backend for validating external remove proposals
     */
    override suspend fun createConversation(
        mlsGroupId: ConversationId,
        externalSenders: ByteArray
    ) {
        return coreCryptoClient.transaction {
            val mlsCredentialType = getCredentialType(it)
            it.createConversation(
                conversationId = mlsGroupId,
                creatorCredentialType = mlsCredentialType,
                config = ConversationConfiguration(
                    ciphersuite = ciphersuite,
                    externalSenders = listOf(
                        externalSenders.toExternalSenderKey()
                    ),
                    custom = CUSTOM_CONFIGURATION_DEFAULT
                )
            )
        }
    }

    override suspend fun updateKeyingMaterial(mlsGroupId: ConversationId) {
        coreCryptoClient.transaction {
            it.updateKeyingMaterial(mlsGroupId)
        }
    }

    override suspend fun addMemberToMlsConversation(
        mlsGroupId: ConversationId,
        keyPackages: List<KeyPackage>
    ) {
        coreCryptoClient.transaction {
            it.addClientsToConversation(mlsGroupId, keyPackages)
        }
    }

    override suspend fun removeMembersFromConversation(
        mlsGroupId: ConversationId,
        clientIds: List<CryptoClientId>
    ) {
        coreCryptoClient.transaction {
            it.removeClientsFromConversation(
                conversationId = mlsGroupId,
                clients = clientIds.map { client ->
                    ClientId(client.value.toByteArray())
                }
            )
        }
    }

    override suspend fun processWelcomeMessage(welcome: Welcome): ConversationId {
        val welcomeBundle = coreCryptoClient.transaction {
            it.processWelcomeMessage(
                welcomeMessage = welcome,
                customConfiguration = CUSTOM_CONFIGURATION_DEFAULT
            )
        }
        return welcomeBundle.id
    }

    override suspend fun hasTooFewKeyPackageCount(): Boolean {
        val packageCount = coreCryptoClient.transaction {
            val mlsCredentialType = getCredentialType(it)
            it.clientValidKeypackagesCount(
                ciphersuite = ciphersuite,
                credentialType = mlsCredentialType
            )
        }
        return packageCount < CryptoClient.DEFAULT_KEYPACKAGE_COUNT / 2u
    }

    override suspend fun conversationExists(mlsGroupId: ConversationId): Boolean =
        coreCryptoClient.transaction {
            it.conversationExists(mlsGroupId)
        }

    override suspend fun conversationEpoch(mlsGroupId: ConversationId): ULong =
        coreCryptoClient.transaction {
            it.conversationEpoch(mlsGroupId)
        }

    override suspend fun wipeConversation(mlsGroupId: ConversationId) {
        logger.debug("Conversation will be deleted from CoreCrypto. mlsGroupId: {}", mlsGroupId)
        coreCryptoClient.transaction {
            it.wipeConversation(mlsGroupId)
            logger.debug("Conversation is deleted from CoreCrypto. mlsGroupId: {}", mlsGroupId)
        }
    }

    private suspend fun getCredentialType(context: CoreCryptoContext): CredentialType =
        if (context.e2eiIsEnabled(ciphersuite)) CredentialType.X509 else CredentialType.BASIC

    override fun close() {
        runBlocking { coreCryptoClient.close() }
    }

    companion object {
        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val KEYSTORE_NAME = "keystore"

        suspend fun create(
            userId: String,
            ciphersuiteCode: Int = DEFAULT_CIPHERSUITE_IDENTIFIER
        ): MlsCryptoClient {
            val clientDirectoryPath = "storage/cryptography/$userId"
            val keystorePath = "$clientDirectoryPath/$KEYSTORE_NAME"
            val ciphersuite = getMlsCipherSuiteName(ciphersuiteCode)

            File(clientDirectoryPath).mkdirs()

            val coreCryptoClient = CoreCrypto.invoke(
                keystore = keystorePath,
                databaseKey = IsolatedKoinContext.getCryptographyStorageKey()
                    ?.let { DatabaseKey(it) }
                    ?: throw WireException.InvalidParameter("Cryptography password missing")
            )

            return MlsCryptoClient(
                ciphersuite = ciphersuite,
                coreCryptoClient = coreCryptoClient
            )
        }

        fun getMlsCipherSuiteName(code: Int): Ciphersuite =
            when (code) {
                DEFAULT_CIPHERSUITE_IDENTIFIER ->
                    Ciphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519
                2 -> Ciphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                3 -> Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519
                4 -> Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_ED448
                5 -> Ciphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
                6 -> Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448
                7 -> Ciphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                else -> Ciphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519
            }

        @Suppress("MagicNumber")
        fun Int.toHexString(minDigits: Int = 4): String {
            return "0x" + this.toString(16).padStart(minDigits, '0')
        }
    }
}
