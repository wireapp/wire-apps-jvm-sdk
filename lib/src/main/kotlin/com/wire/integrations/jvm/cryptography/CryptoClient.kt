package com.wire.integrations.jvm.cryptography

import com.wire.crypto.Ciphersuite
import com.wire.crypto.Ciphersuites
import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.crypto.PreKey
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.exception.WireException.InvalidParameter
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import kotlinx.coroutines.runBlocking
import java.io.File

class CryptoClient(private val team: Team, private val ciphersuite: Int) : AutoCloseable {
    private var coreCrypto: CoreCrypto

    private companion object {
        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val DEFAULT_PREKEYS_COUNT = 100
        private const val DEFAULT_KEYPACKAGES_COUNT = 100u
        private const val KEYSTORE_NAME = "keystore"
    }

    internal constructor(team: Team) : this(team, DEFAULT_CIPHERSUITE_IDENTIFIER)

    init {
        runBlocking {
            val clientDirectoryPath = getDirectoryPath(team.clientId)
            val keystorePath = "$clientDirectoryPath/$KEYSTORE_NAME"

            File(clientDirectoryPath).mkdirs()

            coreCrypto = CoreCrypto.invoke(
                keystore = keystorePath,
                databaseKey = IsolatedKoinContext.getCryptographyStoragePassword()
                    ?: throw InvalidParameter("Cryptography password missing")
            )

            coreCrypto.transaction { it.proteusInit() }

            coreCrypto.transaction {
                it.mlsInit(
                    ClientId(getCoreCryptoId(team.userId, team.clientId)),
                    Ciphersuites(setOf(getMlsCipherSuiteName(ciphersuite)))
                )
            }
        }
    }

    fun getId(): String = team.clientId

    // Fully qualified id for the client, allowing to push key packages to the backend
    private fun getCoreCryptoId(
        userId: QualifiedId,
        clientId: String
    ): String = "${userId.id}:$clientId@${userId.domain}"

    private fun getDirectoryPath(clientId: String): String = "cryptography/$clientId"

    fun proteusGeneratePrekeys(): ArrayList<PreKey> {
        return runBlocking {
            coreCrypto.transaction { it.proteusNewPreKeys(0, DEFAULT_PREKEYS_COUNT) }
        }
    }

    fun proteusGenerateLastPrekey(): PreKey {
        return runBlocking {
            coreCrypto.transaction { it.proteusNewLastPreKey() }
        }
    }

    fun mlsGetPublicKey(): ByteArray {
        return runBlocking {
            coreCrypto.transaction {
                it.getPublicKey(getMlsCipherSuiteName(ciphersuite)).value
            }
        }
    }

    fun mlsGenerateKeyPackages(): List<ByteArray> {
        return runBlocking {
            coreCrypto.transaction {
                it.generateKeyPackages(
                    amount = DEFAULT_KEYPACKAGES_COUNT,
                    ciphersuite = getMlsCipherSuiteName(ciphersuite)
                )
            }
        }.map { it.value }
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
}
