package com.wire.integrations.jvm.crypto

import com.wire.crypto.Ciphersuite
import com.wire.crypto.Ciphersuites
import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsMessage
import com.wire.crypto.MlsTransport
import com.wire.crypto.PlaintextMessage
import com.wire.crypto.Welcome
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.exception.WireException.InvalidParameter
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64

internal class CoreCryptoClient : CryptoClient {
    // App specific appClientId: app@domain:UUIDv4
    private val appClientId: AppClientId
    private val ciphersuite: Ciphersuite
    private var coreCrypto: CoreCrypto

    constructor(
        appClientId: AppClientId,
        ciphersuiteCode: Int = DEFAULT_CIPHERSUITE_IDENTIFIER,
        mlsTransport: MlsTransport
    ) {
        this.appClientId = appClientId
        this.ciphersuite = getMlsCipherSuiteName(ciphersuiteCode)
        val clientDirectoryPath = "cryptography/${appClientId.value}"
        val keystorePath = "$clientDirectoryPath/$KEYSTORE_NAME"

        File(clientDirectoryPath).mkdirs()
        runBlocking {
            coreCrypto = CoreCrypto.invoke(
                keystore = keystorePath,
                databaseKey = IsolatedKoinContext.getCryptographyStoragePassword()
                    ?: throw InvalidParameter("Cryptography password missing")
            )
            coreCrypto.transaction {
                it.mlsInit(
                    ClientId(appClientId.value),
                    Ciphersuites(setOf(ciphersuite))
                )
            }
            coreCrypto.provideTransport(mlsTransport)
        }
    }

    override fun encryptMls(
        mlsGroupId: MLSGroupId,
        plainMessage: ByteArray
    ): ByteArray {
        val encryptedMessage = runBlocking {
            coreCrypto.transaction { it.encryptMessage(mlsGroupId, PlaintextMessage(plainMessage)) }
        }
        return encryptedMessage.value
    }

    override fun decryptMls(
        mlsGroupId: MLSGroupId,
        encryptedMessage: String
    ): ByteArray {
        val encryptedMessageBytes: ByteArray = Base64.getDecoder().decode(encryptedMessage)
        val decryptedMessage = runBlocking {
            coreCrypto.transaction {
                it.decryptMessage(
                    id = mlsGroupId,
                    message = MlsMessage(encryptedMessageBytes)
                )
            }
        }
        return decryptedMessage.message
            ?: throw WireException.CryptographicSystemError("Decryption failed")
    }

    override fun mlsGetPublicKey(): MlsPublicKeys {
        val key = runBlocking {
            coreCrypto.transaction {
                it.getPublicKey(ciphersuite = ciphersuite).value
            }
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

    override fun mlsGenerateKeyPackages(packageCount: UInt): List<MLSKeyPackage> {
        return runBlocking {
            coreCrypto.transaction {
                it.generateKeyPackages(
                    amount = packageCount,
                    ciphersuite = ciphersuite
                )
            }
        }
    }

    override fun mlsConversationExists(mlsGroupId: MLSGroupId): Boolean {
        return runBlocking {
            coreCrypto.transaction { it.conversationExists(mlsGroupId) }
        }
    }

    override fun createJoinMlsConversationRequest(groupInfo: GroupInfo): MLSGroupId {
        return runBlocking {
            coreCrypto.transaction { it.joinByExternalCommit(groupInfo).id }
        }
    }

    override fun createConversation(groupId: MLSGroupId) {
        return runBlocking {
            coreCrypto.transaction {
                it.createConversation(
                    id = groupId,
                    ciphersuite = ciphersuite
                )
            }
        }
    }

    override fun addMemberToMlsConversation(
        mlsGroupId: MLSGroupId,
        keyPackages: List<MLSKeyPackage>
    ) {
        runBlocking {
            coreCrypto.transaction {
                it.addMember(mlsGroupId, keyPackages)
            }
        }
    }

    override fun processWelcomeMessage(welcome: Welcome): MLSGroupId {
        val welcomeBundle = runBlocking {
            coreCrypto.transaction { it.processWelcomeMessage(welcome) }
        }
        return welcomeBundle.id
    }

    override fun validKeyPackageCount(): Long {
        val packageCount = runBlocking {
            coreCrypto.transaction { it.validKeyPackageCount(ciphersuite) }
        }
        return packageCount.toLong()
    }

    override fun close() {
        runBlocking { coreCrypto.close() }
    }

    private fun getMlsCipherSuiteName(code: Int): Ciphersuite {
        return when (code) {
            DEFAULT_CIPHERSUITE_IDENTIFIER -> Ciphersuite.DEFAULT
            2 -> Ciphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            3 -> Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519
            4 -> Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448
            5 -> Ciphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521
            6 -> Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448
            7 -> Ciphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
            else -> Ciphersuite.DEFAULT
        }
    }

    companion object {
        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val KEYSTORE_NAME = "keystore"
    }
}
