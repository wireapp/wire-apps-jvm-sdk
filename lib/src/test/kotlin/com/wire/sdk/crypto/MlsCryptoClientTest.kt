package com.wire.sdk.crypto

import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.KeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.toGroupInfo
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.protobuf.ProtobufDeserializer
import com.wire.sdk.model.protobuf.ProtobufSerializer
import com.wire.sdk.utils.MlsTransportLastWelcome
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileInputStream
import java.io.InputStream
import java.util.Base64
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class MlsCryptoClientTest {
    private val testMlsTransport = MlsTransportLastWelcome()

    @Test
    fun whenCryptoStoragePasswordIsSet_thenClientWorks() {
        runBlocking {
            val userId = UUID.randomUUID()
            val cryptoClient = MlsCryptoClient.create(
                appId = userId,
                ciphersuiteCode = 1
            )
            cryptoClient.initializeMlsClient(
                cryptoClientId = CryptoClientId.create(
                    userId = userId.toString(),
                    deviceId = "0001",
                    userDomain = "wire.test"
                ),
                mlsTransport = testMlsTransport
            )

            assertNotNull(cryptoClient.mlsGetPublicKey())
            val keyPackages = cryptoClient.mlsGenerateKeyPackages(10u)
            assertEquals(10, keyPackages.size)
        }
    }

    @Test
    fun deleteClientStorageRemovesKeystoreDirectory() {
        runBlocking {
            val userId = UUID.randomUUID()
            val cryptoClient = MlsCryptoClient.create(
                appId = userId,
                ciphersuiteCode = 1
            )
            cryptoClient.close()

            val directory = MlsCryptoClient.clientStorageDirectory(userId)
            assertTrue { directory.exists() }

            val deleted = MlsCryptoClient.deleteClientStorage(userId)

            assertTrue { deleted }
            assertFalse { directory.exists() }
        }
    }

    @Test
    fun deleteClientStorageReturnsTrueWhenDirectoryMissing() {
        val userId = UUID.randomUUID()
        assertFalse { MlsCryptoClient.clientStorageDirectory(userId).exists() }
        assertTrue { MlsCryptoClient.deleteClientStorage(userId) }
    }

    @Test
    fun testMlsClientFailOnDifferentPassword() {
        runBlocking {
            val userId = UUID.randomUUID()
            val ciphersuiteCode = 1

            val cryptoClient = MlsCryptoClient.create(
                appId = userId,
                ciphersuiteCode = ciphersuiteCode
            )
            cryptoClient.close()

            IsolatedKoinContext.setCryptographyStorageKey(
                "anotherPasswordOfRandom32BytesCH".toByteArray()
            )
            try {
                assertThrows<CoreCryptoException> {
                    MlsCryptoClient.create(
                        appId = userId,
                        ciphersuiteCode = ciphersuiteCode
                    )
                }
            } finally {
                IsolatedKoinContext.setCryptographyStorageKey(TEST_STORAGE_PASSWORD.toByteArray())
            }
        }
    }

    @Test
    fun whenMlsClientIsNotInitialized_thenCredentialDependentOperationsFailClearly() {
        runBlocking {
            val cryptoClient = MlsCryptoClient.create(
                appId = UUID.randomUUID(),
                ciphersuiteCode = 1
            )

            assertThrows<WireException.CryptographicSystemError> {
                cryptoClient.mlsGetPublicKey()
            }
            cryptoClient.close()
        }
    }

    @Test
    fun testMlsClientCreateConversationAndEncryptMls() {
        runBlocking {
            // GroupInfo of a real conversation, stored in a binary test file
            val inputStream: InputStream = FileInputStream("src/test/resources/groupInfo.bin")
            val groupInfo = inputStream.readAllBytes().toGroupInfo()

            // Create a new client and join the conversation
            val userId = UUID.randomUUID()
            val mlsClient = MlsCryptoClient.create(
                appId = userId,
                ciphersuiteCode = 1
            )
            mlsClient.initializeMlsClient(
                cryptoClientId = CryptoClientId.create(
                    userId = userId.toString(),
                    deviceId = "0001",
                    userDomain = "wire.test"
                ),
                mlsTransport = testMlsTransport
            )

            mlsClient.joinMlsConversationRequest(groupInfo)

            assertTrue { testMlsTransport.getLastCommitBundle().commit.isNotEmpty() }
            mlsClient.close()
        }
    }

    @Test
    fun testMlsClientsEncryptAndDecrypt() {
        runBlocking {
            // Create two clients, Bob and Alice
            val bobUserId = UUID.randomUUID()
            val bobClient = MlsCryptoClient.create(
                appId = bobUserId,
                ciphersuiteCode = 1
            )
            bobClient.initializeMlsClient(
                cryptoClientId = CryptoClientId.create(
                    userId = bobUserId.toString(),
                    deviceId = "b0b",
                    userDomain = "wire.test"
                ),
                mlsTransport = testMlsTransport
            )

            val aliceUserId = UUID.randomUUID()
            val aliceClient = MlsCryptoClient.create(
                appId = aliceUserId,
                ciphersuiteCode = 1
            )
            aliceClient.initializeMlsClient(
                cryptoClientId = CryptoClientId.create(
                    userId = aliceUserId.toString(),
                    deviceId = "a11ce",
                    userDomain = "wire.test"
                ),
                mlsTransport = testMlsTransport
            )

            // Create a new conversation with Bob, then add Alice to it
            val mlsGroupId =
                ConversationId("JfflcPtUivbg+1U3Iyrzsh5D2ui/OGS5Rvf52ipH5KY=".toByteArray())
            val externalSenders: ByteArray =
                Base64.getDecoder().decode("3AEFMpXsnJ28RcyA7CIRuaDL7L0vGmKaGjD206SANZw=")
            bobClient.createConversation(mlsGroupId, externalSenders)
            assertTrue { bobClient.conversationExists(mlsGroupId) }
            val keyPackages: List<KeyPackage> = aliceClient.mlsGenerateKeyPackages(1u)
            assertFalse { aliceClient.conversationExists(mlsGroupId) }

            assertNotEquals(bobClient.mlsGetPublicKey(), aliceClient.mlsGetPublicKey())
            bobClient.addClientsToMlsConversation(mlsGroupId, keyPackages)

            // Alice accepts joining the conversation
            val welcomeMessage = testMlsTransport.getLastWelcome()
            aliceClient.processWelcomeMessage(welcomeMessage)
            assert(aliceClient.conversationExists(mlsGroupId))

            // Alice encrypts a message for the joined conversation
            val plainMessage = "random_message"
            val wireTextMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = plainMessage
            )
            val encryptedMessage: ByteArray =
                aliceClient.encryptMls(
                    mlsGroupId,
                    ProtobufSerializer.toGenericMessageByteArray(wireMessage = wireTextMessage)
                )
            assert(encryptedMessage.size > 10)
            val encryptedBase64Message = Base64.getEncoder().encodeToString(encryptedMessage)

            // Bob decrypts the message
            val decrypted = requireNotNull(bobClient.decryptMls(mlsGroupId, encryptedBase64Message))
            assertEquals(QualifiedId(aliceUserId, "wire.test"), decrypted.senderClientId)

            val genericMessage = GenericMessage.parseFrom(decrypted.message)
            val wireMessage = ProtobufDeserializer.processGenericMessage(
                genericMessage = genericMessage,
                conversationId = QualifiedId(
                    id = UUID.randomUUID(),
                    domain = "random_domain"
                ),
                sender = QualifiedId(
                    id = UUID.randomUUID(),
                    domain = "random_domain"
                ),
                timestamp = Instant.DISTANT_PAST
            )

            assertEquals((wireMessage as WireMessage.Text).text, plainMessage)

            assertThrows<CoreCryptoException.Mls> {
                bobClient.decryptMls(mlsGroupId, encryptedBase64Message)
            }.also {
                // Message was already decrypted by Bob, trying again should fail
                assert(it.mlsError is MlsException.DuplicateMessage)
            }
            bobClient.close()
            aliceClient.close()
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() {
            // Testing that full UTF-8 is accepted on storage password
            IsolatedKoinContext.start()
            IsolatedKoinContext.setCryptographyStorageKey(TEST_STORAGE_PASSWORD.toByteArray())
        }

        val CONVERSATION_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = UUID.randomUUID().toString()
        )

        private const val TEST_STORAGE_PASSWORD = "myDummyPasswordOfRandom32BytesCH"

        @JvmStatic
        @AfterAll
        fun after() {
            IsolatedKoinContext.stop()
        }
    }
}
