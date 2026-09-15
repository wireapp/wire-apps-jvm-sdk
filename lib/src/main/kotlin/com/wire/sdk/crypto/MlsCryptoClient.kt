package com.wire.sdk.crypto

import com.wire.crypto.CipherSuite
import com.wire.crypto.ClientId
import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.Credential
import com.wire.crypto.CredentialRef
import com.wire.crypto.Database
import com.wire.crypto.DatabaseKey
import com.wire.crypto.DecryptedMessage
import com.wire.crypto.DeviceId
import com.wire.crypto.ExternalSender
import com.wire.crypto.GroupInfo
import com.wire.crypto.KeyPackage
import com.wire.crypto.MlsTransport
import com.wire.crypto.Uuid
import com.wire.crypto.Welcome
import com.wire.crypto.open
import com.wire.crypto.proteusLastResortPrekeyIdFfi
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.http.MlsPublicKeys
import com.wire.sdk.model.http.client.PreKeyCrypto
import com.wire.sdk.utils.obfuscateId
import com.wire.sdk.utils.toQualifiedId
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import kotlin.io.encoding.Base64

/**
 * Wrapper client on top of the client provided by the Core-Crypto library,
 * used for any MLS cryptographic operations.
 * Internal use only, use the factory function [create] to create a new instance.
 */
internal class MlsCryptoClient private constructor(
    private val cipherSuite: CipherSuite,
    private var coreCryptoClient: CoreCrypto
) : CryptoClient {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var cryptoClientId: CryptoClientId? = null
    private var credential: CredentialRef? = null

    private fun setCryptoClientId(cryptoClientId: CryptoClientId) {
        this@MlsCryptoClient.cryptoClientId = cryptoClientId
    }

    private fun credentialOrThrow(): CredentialRef =
        credential ?: throw WireException.CryptographicSystemError(
            "MLS client has not been initialized."
        )

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
    ): DecryptedMlsMessage? {
        val encryptedMessageBytes: ByteArray = Base64.decode(encryptedMessage)
        val decryptedMessage =
            coreCryptoClient.transaction {
                it.decryptMessage(
                    conversationId = mlsGroupId,
                    payload = encryptedMessageBytes
                )
            }

        return when (decryptedMessage) {
            is DecryptedMessage.Text -> DecryptedMlsMessage(
                message = decryptedMessage.plaintext,
                sender = decryptedMessage.senderClientId.toQualifiedId()
            )

            is DecryptedMessage.Commit,
            is DecryptedMessage.Proposal -> {
                logger.debug(
                    "Decryption successful but no application message. decryptedMessageType: {}",
                    decryptedMessage::class.simpleName
                )
                null
            }
        }
    }

    override suspend fun initializeProteusClient() =
        coreCryptoClient.transaction {
            it.proteusInit()
        }

    override suspend fun generateProteusPreKeys(
        from: Int,
        count: Int
    ): List<PreKeyCrypto> {
        require(from >= 0) { "PreKey start index must be non-negative." }
        require(count >= 0) { "PreKey count must be non-negative." }
        require(from.toLong() + count.toLong() <= UShort.MAX_VALUE.toLong() + 1) {
            "PreKey range must fit within UShort."
        }

        return coreCryptoClient.transaction { crypto ->
            from.until(from + count).map {
                val preKeyId = it.toUShort()
                val preKeyValue = crypto.proteusNewPrekey(preKeyId)
                PreKeyCrypto(it, Base64.encode(preKeyValue))
            }
        }
    }

    override suspend fun generateProteusLastPreKey(): PreKeyCrypto =
        coreCryptoClient.transaction { context ->
            val proteusLastPreKeyId = proteusLastResortPrekeyIdFfi()
            val proteusLastPreKeyValue = context.proteusLastResortPrekey()

            PreKeyCrypto(proteusLastPreKeyId.toInt(), Base64.encode(proteusLastPreKeyValue))
        }

    override suspend fun initializeMlsClient(
        cryptoClientId: CryptoClientId,
        mlsTransport: MlsTransport
    ) {
        val clientId = ClientId(
            userId = Uuid(cryptoClientId.userId),
            deviceId = DeviceId.fromHexString(cryptoClientId.deviceId),
            domain = cryptoClientId.userDomain
        )

        coreCryptoClient.transaction {
            it.mlsInit(
                clientId = clientId,
                transport = mlsTransport
            )

            val credentials = coreCryptoClient.getCredentials()
            if (credentials.isEmpty()) {
                logger.info("Creating CoreCrypto Credential")
                val credential = Credential.basic(cipherSuite, clientId)
                this.credential = it.addCredential(credential)
            } else {
                this.logger.info("Loading CoreCrypto Credential")
                this.credential = credentials[0]
            }
        }

        setCryptoClientId(cryptoClientId = cryptoClientId)
    }

    override suspend fun mlsGetPublicKey(): MlsPublicKeys {
        val key = coreCryptoClient.publicKey(credentialOrThrow())
        val encodedKey = Base64.encode(key)

        return when (cipherSuite) {
            CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 -> {
                MlsPublicKeys(ecdsaSecp256r1Sha256 = encodedKey)
            }

            CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 -> {
                MlsPublicKeys(ecdsaSecp384r1Sha384 = encodedKey)
            }

            CipherSuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 -> {
                MlsPublicKeys(ecdsaSecp521r1Sha512 = encodedKey)
            }

            CipherSuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519,
            CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519 -> {
                MlsPublicKeys(ed25519 = encodedKey)
            }

            CipherSuite.MLS_256_DHKEMX448_AES256GCM_SHA512_ED448,
            CipherSuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448 -> {
                throw WireException.CryptographicSystemError("Unsupported ciphersuite")
            }
        }
    }

    override suspend fun mlsGenerateKeyPackages(packageCount: UInt): List<KeyPackage> {
        return coreCryptoClient.transaction { context ->
            val credential = credentialOrThrow()
            val keyPackages = mutableListOf<KeyPackage>()
            repeat(packageCount.toInt()) {
                val keyPackage = context.generateKeyPackage(credential)
                keyPackages.add(keyPackage)
            }
            keyPackages
        }
    }

    override suspend fun joinMlsConversationRequest(groupInfo: GroupInfo) {
        return coreCryptoClient.transaction {
            it.joinByExternalCommit(
                groupInfo = groupInfo,
                credentialRef = credentialOrThrow()
            )
        }
    }

    /**
     * Creates a conversation in CoreCrypto.
     *
     * @param mlsGroupId Group ID from creating the conversation in core-crypto storage
     * @param externalSenders Keys fetched from backend for validating external remove proposals
     */
    override suspend fun createConversation(
        mlsGroupId: ConversationId,
        externalSenders: ByteArray
    ) {
        return coreCryptoClient.transaction {
            val credential = credentialOrThrow()
            it.createConversation(
                conversationId = mlsGroupId,
                credentialRef = credential,
                externalSender = ExternalSender.parse(
                    key = externalSenders,
                    signatureScheme = credential.signatureScheme()
                )
            )
        }
    }

    override suspend fun updateKeyingMaterial(mlsGroupId: ConversationId) {
        coreCryptoClient.transaction {
            it.updateKeyingMaterial(mlsGroupId)
        }
    }

    override suspend fun addClientsToMlsConversation(
        mlsGroupId: ConversationId,
        keyPackages: List<KeyPackage>
    ) {
        coreCryptoClient.transaction {
            logger.debug("Clients will be added to conversation. mlsGroupId: {}", mlsGroupId)
            it.addClientsToConversation(mlsGroupId, keyPackages)
            logger.debug("Clients are added to conversation. mlsGroupId: {}", mlsGroupId)
        }
    }

    override suspend fun removeClientsFromConversation(
        mlsGroupId: ConversationId,
        clientIds: List<CryptoClientId>
    ) {
        coreCryptoClient.transaction {
            logger.debug("Clients will be removed from conversation. mlsGroupId: {}", mlsGroupId)

            it.removeClientsFromConversation(
                conversationId = mlsGroupId,
                clients = clientIds.map { client ->
                    ClientId(
                        userId = Uuid(client.userId),
                        deviceId = DeviceId.fromHexString(client.deviceId),
                        domain = client.userDomain
                    )
                }
            )

            logger.debug("Clients are removed from conversation. mlsGroupId: {}", mlsGroupId)
        }
    }

    override suspend fun processWelcomeMessage(welcome: Welcome) {
        coreCryptoClient.transaction {
            it.processWelcomeMessage(welcomeMessage = welcome)
        }
    }

    override suspend fun hasTooFewKeyPackageCount(): Boolean {
        val packageCount = coreCryptoClient.transaction {
            it.getKeyPackages().size
        }

        return packageCount < (CryptoClient.DEFAULT_KEYPACKAGE_COUNT / 2u).toInt()
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
        logger.debug(
            "Conversation will be deleted from CoreCrypto. mlsGroupId: {}",
            mlsGroupId.toString().obfuscateId()
        )
        coreCryptoClient.transaction {
            it.wipeConversation(mlsGroupId)
            logger.debug(
                "Conversation is deleted from CoreCrypto. mlsGroupId: {}",
                mlsGroupId.toString().obfuscateId()
            )
        }
    }

    override fun close() {
        runBlocking { coreCryptoClient.close() }
    }

    companion object {
        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val KEYSTORE_NAME = "keystore"
        private const val CLIENT_STORAGE_ROOT = "storage/cryptography"

        /**
         * Local directory holding the CoreCrypto keystore (Proteus + MLS state) for [appId].
         */
        fun clientStorageDirectory(appId: UUID): File = File("$CLIENT_STORAGE_ROOT/$appId")

        /**
         * Deletes the local CoreCrypto keystore directory for [appId], wiping all Proteus and
         * MLS state so a new client can be registered from a clean slate.
         *
         * Must only be called while no CoreCrypto client holds the keystore open (e.g. at
         * startup before [create]); deleting an open keystore leads to undefined behaviour.
         *
         * @return true if the directory was absent or fully deleted, false if deletion failed.
         */
        fun deleteClientStorage(appId: UUID): Boolean {
            val directory = clientStorageDirectory(appId)
            return if (directory.exists()) directory.deleteRecursively() else true
        }

        suspend fun create(
            appId: UUID,
            ciphersuiteCode: Int = DEFAULT_CIPHERSUITE_IDENTIFIER
        ): MlsCryptoClient {
            val clientDirectory = clientStorageDirectory(appId)
            val keystorePath = "${clientDirectory.path}/$KEYSTORE_NAME"
            val ciphersuite = getMlsCipherSuiteName(ciphersuiteCode)

            clientDirectory.mkdirs()

            val coreCryptoClient = CoreCrypto.invoke(
                database = Database.open(
                    location = keystorePath,
                    key = DatabaseKey(IsolatedKoinContext.getCryptographyStorageKey())
                )
            )

            return MlsCryptoClient(
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

        @Suppress("MagicNumber")
        fun Int.toHexString(minDigits: Int = 4): String {
            return "0x" + this.toString(16).padStart(minDigits, '0')
        }
    }
}
