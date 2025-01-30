package com.wire.integrations.jvm.cryptography

import com.wire.crypto.client.Ciphersuite
import com.wire.crypto.client.Ciphersuites
import com.wire.crypto.client.ClientId
import com.wire.crypto.client.CoreCryptoCentral
import com.wire.crypto.client.PreKey
import com.wire.crypto.client.ProteusClient
import com.wire.crypto.client.transaction
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import kotlinx.coroutines.runBlocking
import java.io.Closeable

class CryptoClient(private val team: Team, private val ciphersuite: Int) : Closeable {
    private var coreCryptoCentral: CoreCryptoCentral
    private var proteusClient: ProteusClient

    private companion object {
        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val DEFAULT_PREKEYS_COUNT = 100
        private const val DEFAULT_KEYPACKAGES_COUNT = 100u
    }

    internal constructor(team: Team) : this(team, DEFAULT_CIPHERSUITE_IDENTIFIER)

    init {
        runBlocking {
            val clientDirectoryPath = getDirectoryPath(team.clientId)

            coreCryptoCentral =
                CoreCryptoCentral.invoke(
                    rootDir = clientDirectoryPath,
                    databaseKey =
                        IsolatedKoinContext.koinApp.koin.getProperty("CRYPTOGRAPHY_STORAGE_PASSWORD")
                            ?: throw IllegalArgumentException("No cryptography storage password provided"),
                    ciphersuites = Ciphersuites(setOf(getMlsCipherSuiteName(ciphersuite)))
                )
            proteusClient = coreCryptoCentral.proteusClient()

            coreCryptoCentral.transaction {
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
        return runBlocking { proteusClient.newPreKeys(0, DEFAULT_PREKEYS_COUNT) }
    }

    fun proteusGenerateLastPrekey(): PreKey {
        return runBlocking { proteusClient.newLastPreKey() }
    }

    fun mlsGetPublicKey(): ByteArray {
        return runBlocking {
            val publicKey =
                coreCryptoCentral.transaction {
                    it.getPublicKey(getMlsCipherSuiteName(ciphersuite)).value
                }
            checkNotNull(publicKey) { "MLS client has not been initialized" }
        }
    }

    fun mlsGenerateKeyPackages(): List<ByteArray> {
        val keyPackages =
            runBlocking {
                val keyPackages =
                    coreCryptoCentral.transaction {
                        it.generateKeyPackages(
                            amount = DEFAULT_KEYPACKAGES_COUNT,
                            ciphersuite = getMlsCipherSuiteName(ciphersuite)
                        )
                    }
                checkNotNull(keyPackages) { "MLS client has not been initialized" }
            }
        return keyPackages.map { it.value }
    }

    override fun close() {
        runBlocking { coreCryptoCentral.close() }
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
