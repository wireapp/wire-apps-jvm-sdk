package com.wire.integrations.jvm.crypto

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.MlsTransport
import com.wire.crypto.Welcome
import com.wire.crypto.toGroupId
import com.wire.crypto.uniffi.CommitBundle
import com.wire.crypto.uniffi.MlsTransportResponse
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.ClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.TeamId
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.core.Koin
import org.koin.test.KoinTest
import java.io.FileInputStream
import java.io.InputStream
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CryptoClientTest : KoinTest {
    override fun getKoin(): Koin = IsolatedKoinContext.koinApp.koin

    @Test
    fun whenCryptoStoragePasswordIsSet_thenClientWorks() {
        val cryptoClient = CryptoClient(
            Team(
                id = TeamId(value = UUID.randomUUID()),
                userId = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString()),
                clientId = ClientId(UUID.randomUUID().toString())
            )
        )
        assertNotNull(cryptoClient.mlsGetPublicKey())
        val keyPackages = cryptoClient.mlsGenerateKeyPackages(10u)
        assertEquals(10, keyPackages.size)
    }

    @Test
    fun testMlsClientFailOnDifferentPassword() {
        val team = Team(
            id = TeamId(value = UUID.randomUUID()),
            userId = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString()),
            clientId = ClientId(UUID.randomUUID().toString())
        )

        val cryptoClient = CryptoClient(team)
        cryptoClient.close()

        IsolatedKoinContext.setCryptographyStoragePassword("apple🍎")
        assertThrows<com.wire.crypto.uniffi.CoreCryptoException.Mls> { CryptoClient(team) }
    }

    @Test
    fun testMlsClientCreateConversationAndEncryptMls() {
        val team = Team(
            id = TeamId(value = UUID.randomUUID()),
            userId = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString()),
            clientId = ClientId(UUID.randomUUID().toString())
        )

        // GroupInfo of a real conversation, stored in a binary test file
        val inputStream: InputStream = FileInputStream("src/test/resources/groupInfo.bin")
        val groupInfo = GroupInfo(inputStream.readAllBytes())

        // Create a new client and join the conversation
        val mlsClient = CryptoClient(team)
        val groupIdGenerated: MLSGroupId = mlsClient.createJoinMlsConversationRequest(groupInfo)
        assertTrue { mlsClient.mlsConversationExists(groupIdGenerated) }

        // Encrypt a message for the joined conversation
        val plainMessage = UUID.randomUUID().toString()
        val encryptedMessage: ByteArray =
            mlsClient.encryptMls(groupIdGenerated, plainMessage.toByteArray())
        assertTrue { encryptedMessage.size > 10 }
        val encryptedBase64Message = Base64.getEncoder().encodeToString(encryptedMessage)

        assertThrows<CoreCryptoException.Mls> {
            mlsClient.decryptMls(groupIdGenerated, encryptedBase64Message)
        }.also {
            // Unfortunately it's not possible for a client to decrypt a message it encrypted itself
            // By getting the duplicated message exception we know that the encryption works,
            // but we cannot attest that the decrypted message is the same as the original
            assert(it.exception is MlsException.DuplicateMessage)
        }
        mlsClient.close()
    }

    @Test
    fun testMlsClientsEncryptAndDecrypt() {
        val testMlsTransport = MlsTransportLastWelcome()

        // Create two clients, Bob and Alice
        val teamForBob = Team(
            id = TeamId(value = UUID.randomUUID()),
            userId = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString()),
            clientId = ClientId("bob_" + UUID.randomUUID())
        )
        val bobClient = CryptoClient(
            team = teamForBob,
            mlsTransport = testMlsTransport
        )

        val teamForAlice = Team(
            id = TeamId(value = UUID.randomUUID()),
            userId = QualifiedId(UUID.randomUUID(), UUID.randomUUID().toString()),
            clientId = ClientId("alice_" + UUID.randomUUID())
        )
        val aliceClient = CryptoClient(
            team = teamForAlice,
            mlsTransport = testMlsTransport
        )

        // Create a new conversation with Bob, then add Alice to it
        val groupId = "JfflcPtUivbg+1U3Iyrzsh5D2ui/OGS5Rvf52ipH5KY=".toGroupId()
        bobClient.createConversation(groupId)
        assertTrue { bobClient.mlsConversationExists(groupId) }
        val keyPackages: List<MLSKeyPackage> = aliceClient.mlsGenerateKeyPackages(1u)
        assertFalse { aliceClient.mlsConversationExists(groupId) }

        assertFalse { bobClient.mlsGetPublicKey().contentEquals(aliceClient.mlsGetPublicKey()) }
        bobClient.addMemberToMlsConversation(groupId, keyPackages)

        // Alice accepts joining the conversation
        val welcomeMessage = testMlsTransport.getLastWelcome()
        aliceClient.processWelcomeMessage(welcomeMessage)
        assert(aliceClient.mlsConversationExists(groupId))

        // Alice encrypts a message for the joined conversation
        val plainMessage = UUID.randomUUID().toString()
        val encryptedMessage: ByteArray =
            aliceClient.encryptMls(groupId, plainMessage.toByteArray())
        assert(encryptedMessage.size > 10)
        val encryptedBase64Message = Base64.getEncoder().encodeToString(encryptedMessage)

        // Bob decrypts the message
        val decrypted: ByteArray = bobClient.decryptMls(groupId, encryptedBase64Message)
        assert(String(decrypted) == plainMessage)

        assertThrows<CoreCryptoException.Mls> {
            bobClient.decryptMls(groupId, encryptedBase64Message)
        }.also {
            // Message was already decrypted by Bob, trying again should fail
            assert(it.exception is MlsException.DuplicateMessage)
        }
        bobClient.close()
        aliceClient.close()
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() {
            // Testing that full UTF-8 is accepted on storage password
            IsolatedKoinContext.setCryptographyStoragePassword("banana🍌")
        }
    }
}

/**
 * A simple implementation of [MlsTransport] that stores the last welcome message,
 * only for testing purposes. In a real scenario, the welcome message would come from the backend.
 */
class MlsTransportLastWelcome : MlsTransport {
    private var groupWelcomeMap: Welcome? = null

    override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse {
        commitBundle.welcome?.let {
            groupWelcomeMap = Welcome(it)
        }

        return MlsTransportResponse.Success
    }

    override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse {
        return MlsTransportResponse.Success
    }

    fun getLastWelcome(): Welcome =
        groupWelcomeMap ?: throw IllegalArgumentException("No welcome for group")
}
