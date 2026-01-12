package com.wire.sdk.crypto

import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.KeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.toGroupInfo
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.model.AppClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.protobuf.ProtobufDeserializer
import com.wire.sdk.model.protobuf.ProtobufSerializer
import com.wire.sdk.utils.MlsTransportLastWelcome
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import kotlinx.coroutines.runBlocking
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
import org.junit.jupiter.api.AfterAll

class MlsCryptoClientTest {
    private val testMlsTransport = MlsTransportLastWelcome()

    @Test
    fun whenCryptoStoragePasswordIsSet_thenClientWorks() {
        runBlocking {
            val userId = UUID.randomUUID().toString()
            val cryptoClient = MlsCryptoClient.create(
                userId = userId,
                ciphersuiteCode = 1
            )
            cryptoClient.initializeMlsClient(
                appClientId = AppClientId("user_$userId"),
                mlsTransport = testMlsTransport
            )

            assertNotNull(cryptoClient.mlsGetPublicKey())
            val keyPackages = cryptoClient.mlsGenerateKeyPackages(10u)
            assertEquals(10, keyPackages.size)
        }
    }

    @Test
    fun testMlsClientFailOnDifferentPassword() {
        runBlocking {
            val userId = UUID.randomUUID().toString()
            val ciphersuiteCode = 1

            val cryptoClient = MlsCryptoClient.create(
                userId = userId,
                ciphersuiteCode = ciphersuiteCode
            )
            cryptoClient.close()

            IsolatedKoinContext.setCryptographyStoragePassword("anotherPasswordOfRandom32BytesCH")
            assertThrows<CoreCryptoException.Mls> {
                MlsCryptoClient.create(
                    userId = userId,
                    ciphersuiteCode = ciphersuiteCode
                )
            }
        }
    }

    @Test
    fun testMlsClientCreateConversationAndEncryptMls() {
        runBlocking {
            // GroupInfo of a real conversation, stored in a binary test file
            val inputStream: InputStream = FileInputStream("src/test/resources/groupInfo.bin")
            val groupInfo = inputStream.readAllBytes().toGroupInfo()

            // Create a new client and join the conversation
            val userId = UUID.randomUUID().toString()
            val mlsClient = MlsCryptoClient.create(
                userId = userId,
                ciphersuiteCode = 1
            )
            mlsClient.initializeMlsClient(
                appClientId = AppClientId("user_$userId"),
                mlsTransport = testMlsTransport
            )

            val groupIdGenerated: ConversationId = mlsClient.joinMlsConversationRequest(groupInfo)
            assertTrue { mlsClient.conversationExists(groupIdGenerated) }

            // Encrypt a message for the joined conversation
            val plainMessage = UUID.randomUUID().toString()
            val wireTextMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = plainMessage
            )
            val encryptedMessage: ByteArray =
                mlsClient.encryptMls(
                    groupIdGenerated,
                    ProtobufSerializer.toGenericMessageByteArray(wireMessage = wireTextMessage)
                )
            assertTrue { encryptedMessage.size > 10 }
            val encryptedBase64Message = Base64.getEncoder().encodeToString(encryptedMessage)

            assertThrows<CoreCryptoException.Mls> {
                mlsClient.decryptMls(groupIdGenerated, encryptedBase64Message)
            }.also {
                // Unfortunately it's not possible for a client to decrypt a message it encrypted itself
                // By getting the duplicated message exception we know that the encryption works,
                // but we cannot attest that the decrypted message is the same as the original
                assert(it.mlsError is MlsException.DuplicateMessage)
            }
            mlsClient.close()
        }
    }

    @Test
    fun testMlsClientsEncryptAndDecrypt() {
        runBlocking {
            // Create two clients, Bob and Alice
            val bobUserId = UUID.randomUUID().toString()
            val bobClient = MlsCryptoClient.create(
                userId = bobUserId,
                ciphersuiteCode = 1
            )
            bobClient.initializeMlsClient(
                appClientId = AppClientId(
                    value = "bob_$bobUserId"
                ),
                mlsTransport = testMlsTransport
            )

            val aliceUserId = UUID.randomUUID().toString()
            val aliceClient = MlsCryptoClient.create(
                userId = aliceUserId,
                ciphersuiteCode = 1
            )
            aliceClient.initializeMlsClient(
                appClientId = AppClientId(
                    value = "alice_$aliceUserId"
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
            bobClient.addMemberToMlsConversation(mlsGroupId, keyPackages)

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
            val decrypted: ByteArray? = bobClient.decryptMls(mlsGroupId, encryptedBase64Message)

            val genericMessage = GenericMessage.parseFrom(decrypted)
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
            IsolatedKoinContext.setCryptographyStoragePassword("myDummyPasswordOfRandom32BytesCH")
        }

        val CONVERSATION_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = UUID.randomUUID().toString()
        )

        @JvmStatic
        @AfterAll
        fun after() {
            IsolatedKoinContext.stop()
        }
    }
}
