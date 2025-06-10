package com.wire.integrations.jvm.crypto

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.MlsException
import com.wire.crypto.toGroupId
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.protobuf.ProtobufDeserializer
import com.wire.integrations.jvm.model.protobuf.ProtobufSerializer
import com.wire.integrations.jvm.utils.MlsTransportLastWelcome
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
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterAll

class CoreCryptoClientTest {
    private val testMlsTransport = MlsTransportLastWelcome()

    @Test
    fun whenCryptoStoragePasswordIsSet_thenClientWorks() {
        runBlocking {
            val cryptoClient = CoreCryptoClient.create(
                appClientId = AppClientId("app:${UUID.randomUUID()}"),
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
            val appClientId = AppClientId("app:${UUID.randomUUID()}")
            val cryptoClient = CoreCryptoClient.create(
                appClientId = appClientId,
                mlsTransport = testMlsTransport
            )
            cryptoClient.close()

            IsolatedKoinContext.setCryptographyStoragePassword("anotherPasswordOfRandom32BytesCH")
            assertThrows<com.wire.crypto.uniffi.CoreCryptoException.Mls> {
                CoreCryptoClient.create(
                    appClientId = appClientId,
                    mlsTransport = testMlsTransport
                )
            }
        }
    }

    @Test
    fun testMlsClientCreateConversationAndEncryptMls() {
        runBlocking {
            // GroupInfo of a real conversation, stored in a binary test file
            val inputStream: InputStream = FileInputStream("src/test/resources/groupInfo.bin")
            val groupInfo = GroupInfo(inputStream.readAllBytes())

            // Create a new client and join the conversation
            val mlsClient = CoreCryptoClient.create(
                appClientId = AppClientId(UUID.randomUUID().toString()),
                mlsTransport = testMlsTransport
            )
            val groupIdGenerated: MLSGroupId = mlsClient.joinMlsConversationRequest(groupInfo)
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
                assert(it.exception is MlsException.DuplicateMessage)
            }
            mlsClient.close()
        }
    }

    @Test
    fun testMlsClientsEncryptAndDecrypt() {
        runBlocking {
            // Create two clients, Bob and Alice
            val bobClient = CoreCryptoClient.create(
                appClientId = AppClientId("bob_" + UUID.randomUUID()),
                mlsTransport = testMlsTransport
            )

            val aliceClient = CoreCryptoClient.create(
                appClientId = AppClientId("alice_" + UUID.randomUUID()),
                mlsTransport = testMlsTransport
            )

            // Create a new conversation with Bob, then add Alice to it
            val groupId = "JfflcPtUivbg+1U3Iyrzsh5D2ui/OGS5Rvf52ipH5KY=".toGroupId()
            bobClient.createConversation(groupId)
            assertTrue { bobClient.conversationExists(groupId) }
            val keyPackages: List<MLSKeyPackage> = aliceClient.mlsGenerateKeyPackages(1u)
            assertFalse { aliceClient.conversationExists(groupId) }

            assertNotEquals(bobClient.mlsGetPublicKey(), aliceClient.mlsGetPublicKey())
            bobClient.addMemberToMlsConversation(groupId, keyPackages)

            // Alice accepts joining the conversation
            val welcomeMessage = testMlsTransport.getLastWelcome()
            aliceClient.processWelcomeMessage(welcomeMessage)
            assert(aliceClient.conversationExists(groupId))

            // Alice encrypts a message for the joined conversation
            val plainMessage = "random_message"
            val wireTextMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = plainMessage
            )
            val encryptedMessage: ByteArray =
                aliceClient.encryptMls(
                    groupId,
                    ProtobufSerializer.toGenericMessageByteArray(wireMessage = wireTextMessage)
                )
            assert(encryptedMessage.size > 10)
            val encryptedBase64Message = Base64.getEncoder().encodeToString(encryptedMessage)

            // Bob decrypts the message
            val decrypted: ByteArray? = bobClient.decryptMls(groupId, encryptedBase64Message)

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
                bobClient.decryptMls(groupId, encryptedBase64Message)
            }.also {
                // Message was already decrypted by Bob, trying again should fail
                assert(it.exception is MlsException.DuplicateMessage)
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
