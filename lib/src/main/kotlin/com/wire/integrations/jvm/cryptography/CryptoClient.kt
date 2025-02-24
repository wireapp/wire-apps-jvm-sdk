package com.wire.integrations.jvm.cryptography

import com.wire.crypto.Ciphersuite
import com.wire.crypto.Ciphersuites
import com.wire.crypto.ClientId
import com.wire.crypto.CoreCrypto
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.exception.WireException.InvalidParameter
import com.wire.integrations.jvm.model.ProteusInitKeys
import com.wire.integrations.jvm.model.ProteusPreKey
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.toProteusPreKey
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

internal class CryptoClient : AutoCloseable {
    val team: Team
    private val ciphersuite: Ciphersuite
    private var coreCrypto: CoreCrypto

    constructor(team: Team) : this(team, DEFAULT_CIPHERSUITE_IDENTIFIER)

    constructor(team: Team, ciphersuiteCode: Int) {
        this.team = team
        this.ciphersuite = getMlsCipherSuiteName(ciphersuiteCode)
        runBlocking {
            coreCrypto = coreCryptoInit(team.id)
            coreCrypto.transaction {
                it.proteusInit()
                it.mlsInit(
                    ClientId(getCoreCryptoId(team.userId, team.clientId)),
                    Ciphersuites(setOf(ciphersuite))
                )
            }
        }
    }

    companion object {
        private const val DEFAULT_CIPHERSUITE_IDENTIFIER = 1
        private const val DEFAULT_PREKEYS_COUNT = 100
        private const val DEFAULT_KEYPACKAGES_COUNT = 100u
        private const val KEYSTORE_NAME = "keystore"

        /**
         * Helper function to use core-crypto before having a fully functioning client.
         * To register a client on the backend and to obtain a clientId, Proteus keys are needed
         * so this function creates the minimal config to get prekeys and closes it
         */
        fun generateFirstPrekeys(teamId: UUID): ProteusInitKeys {
            return runBlocking {
                val tmpCoreCrypto = coreCryptoInit(teamId)
                val initKeys = tmpCoreCrypto.transaction { context ->
                    context.proteusInit()
                    val prekeys = context.proteusNewPreKeys(0, DEFAULT_PREKEYS_COUNT)
                    val lastPrekey = context.proteusNewLastPreKey()
                    ProteusInitKeys(
                        prekeys.map { it.toProteusPreKey() },
                        lastPrekey.toProteusPreKey()
                    )
                }
                tmpCoreCrypto.close()
                initKeys
            }
        }

        private suspend fun coreCryptoInit(teamId: UUID): CoreCrypto {
            val clientDirectoryPath = "cryptography/$teamId"
            val keystorePath = "$clientDirectoryPath/$KEYSTORE_NAME"

            File(clientDirectoryPath).mkdirs()

            return CoreCrypto.invoke(
                keystore = keystorePath,
                databaseKey = IsolatedKoinContext.getCryptographyStoragePassword()
                    ?: throw InvalidParameter("Cryptography password missing")
            )
        }
    }

    // Fully qualified id for the client, allowing to push key packages to the backend
    private fun getCoreCryptoId(
        userId: QualifiedId,
        clientId: String
    ): String = "${userId.id}:$clientId@${userId.domain}"

    fun proteusGeneratePrekeys(): List<ProteusPreKey> {
        return runBlocking {
            coreCrypto.transaction { context ->
                context.proteusNewPreKeys(0, DEFAULT_PREKEYS_COUNT)
                    .map { it.toProteusPreKey() }
            }
        }
    }

    fun proteusGenerateLastPrekey(): ProteusPreKey {
        return runBlocking {
            coreCrypto.transaction { it.proteusNewLastPreKey().toProteusPreKey() }
        }
    }

    fun mlsGetPublicKey(): ByteArray {
        return runBlocking {
            coreCrypto.transaction {
                it.getPublicKey(ciphersuite = ciphersuite).value
            }
        }
    }

    fun mlsGenerateKeyPackages(): List<ByteArray> {
        return runBlocking {
            coreCrypto.transaction {
                it.generateKeyPackages(
                    amount = DEFAULT_KEYPACKAGES_COUNT,
                    ciphersuite = ciphersuite
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
