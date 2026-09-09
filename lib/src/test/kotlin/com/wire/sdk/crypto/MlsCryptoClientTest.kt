package com.wire.sdk.crypto

import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.KeyPackage
import com.wire.crypto.MlsException
import com.wire.sdk.TestUtils
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.protobuf.ProtobufDeserializer
import com.wire.sdk.model.protobuf.ProtobufSerializer
import com.wire.sdk.utils.MlsTestFixtures
import com.wire.sdk.utils.MlsTransportLastWelcome
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    fun conferenceKeysFollowMembershipAndEpochs() =
        runBlocking {
            var latestGroupInfo = byteArrayOf()
            var latestCommit = byteArrayOf()
            val transport = object : com.wire.crypto.MlsTransport by testMlsTransport {
                override suspend fun sendCommitBundle(
                    commitBundle: com.wire.crypto.CommitBundle
                ): com.wire.crypto.MlsTransportResponse {
                    // The backend strips the MLS version/wire-format header before storing GroupInfo.
                    latestGroupInfo =
                        commitBundle.groupInfo.payload.copyBytes().drop(4).toByteArray()
                    latestCommit = commitBundle.commit.copyOf()
                    return com.wire.crypto.MlsTransportResponse.Success
                }
            }
            val alice = QualifiedId(UUID.randomUUID(), "wire.test")
            val bob = QualifiedId(UUID.randomUUID(), "wire.test")
            MlsCryptoClient.create(alice.id, 1).use { aliceClient ->
                MlsCryptoClient.create(bob.id, 1).use { bobClient ->
                    aliceClient.initializeMlsClient(
                        CryptoClientId.create(alice, "alice"),
                        transport
                    )
                    bobClient.initializeMlsClient(CryptoClientId.create(bob, "bob"), transport)
                    val parent = ConversationId(UUID.randomUUID().toString().toByteArray())
                    val child = ConversationId(UUID.randomUUID().toString().toByteArray())
                    aliceClient.createConversation(
                        parent,
                        Base64.getDecoder().decode("3AEFMpXsnJ28RcyA7CIRuaDL7L0vGmKaGjD206SANZw=")
                    )
                    // Alice represents the other client that has already initialized the call.
                    aliceClient.createConversation(
                        child,
                        Base64.getDecoder().decode("3AEFMpXsnJ28RcyA7CIRuaDL7L0vGmKaGjD206SANZw=")
                    )
                    aliceClient.updateKeyingMaterial(child)
                    assertTrue(
                        latestGroupInfo.isNotEmpty(),
                        "Establishment must upload a group info"
                    )
                    assertEquals(1uL, aliceClient.conversationEpoch(child))
                    assertEquals(
                        child,
                        bobClient.joinMlsConversationRequest(latestGroupInfo.toGroupInfo())
                    )
                    aliceClient.decryptMls(child, Base64.getEncoder().encodeToString(latestCommit))

                    aliceClient.getConferenceEpochInfo(CONVERSATION_ID, child).use { aliceInfo ->
                        bobClient.getConferenceEpochInfo(CONVERSATION_ID, child).use { bobInfo ->
                            kotlin.test.assertContentEquals(
                                aliceInfo.getSharedSecret(),
                                bobInfo.getSharedSecret()
                            )
                            assertEquals(32, aliceInfo.getSharedSecret().size)
                            assertEquals(
                                mapOf(alice to listOf("alice"), bob to listOf("bob")),
                                aliceInfo.members
                            )
                            assertEquals(aliceInfo.members, bobInfo.members)
                            assertEquals(2, aliceInfo.epoch)
                        }
                    }

                    aliceClient.removeClientsFromConversation(
                        child,
                        listOf(CryptoClientId.create(bob, "bob"))
                    )
                    val removal = bobClient.decryptMls(
                        child,
                        Base64.getEncoder().encodeToString(latestCommit)
                    )
                    assertFalse(removal.isActive)
                    aliceClient.getConferenceEpochInfo(CONVERSATION_ID, child).use {
                        assertEquals(mapOf(alice to listOf("alice")), it.members)
                        assertEquals(3, it.epoch)
                    }
                    assertTrue(aliceClient.conversationExists(parent))
                }
            }
        }

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
                    applicationQualifiedId = QualifiedId(userId, "wire.test"),
                    deviceId = "0001"
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
                IsolatedKoinContext.setCryptographyStorageKey(TestUtils.CRYPTOGRAPHY_STORAGE_KEY)
            }
        }
    }

    @Test
    fun whenMlsClientIsNotInitialized_thenCredentialDependentOperationsFailClearly() {
        runBlocking {
            MlsCryptoClient.create(
                appId = UUID.randomUUID(),
                ciphersuiteCode = 1
            ).use { cryptoClient ->
                assertThrows<WireException.CryptographicSystemError> {
                    cryptoClient.mlsGetPublicKey()
                }
                assertThrows<WireException.CryptographicSystemError> {
                    cryptoClient.mlsGenerateKeyPackages()
                }
                assertThrows<WireException.CryptographicSystemError> {
                    cryptoClient.createConversation(
                        mlsGroupId = ConversationId(ByteArray(32)),
                        externalSenders = ByteArray(32)
                    )
                }
                assertThrows<WireException.CryptographicSystemError> {
                    cryptoClient.joinMlsConversationRequest(MlsTestFixtures.groupInfo())
                }
            }
        }
    }

    @Test
    fun generateProteusPreKeysFailsWhenPreKeyIdsExceedUShortRange() {
        runBlocking {
            MlsCryptoClient.create(
                appId = UUID.randomUUID(),
                ciphersuiteCode = 1
            ).use { cryptoClient ->
                assertThrows<IllegalArgumentException> {
                    cryptoClient.generateProteusPreKeys(
                        from = UShort.MAX_VALUE.toInt(),
                        count = 2
                    )
                }
            }
        }
    }

    @Test
    fun testJoinMlsConversationRequestSendsCommitBundle() {
        runBlocking {
            val groupInfo = MlsTestFixtures.groupInfo()

            // Create a new client and join the conversation
            val userId = UUID.randomUUID()
            val mlsClient = MlsCryptoClient.create(
                appId = userId,
                ciphersuiteCode = 1
            )
            mlsClient.initializeMlsClient(
                cryptoClientId = CryptoClientId.create(
                    applicationQualifiedId = QualifiedId(userId, "wire.test"),
                    deviceId = "0001"
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
                    applicationQualifiedId = QualifiedId(bobUserId, "wire.test"),
                    deviceId = "b0b"
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
                    applicationQualifiedId = QualifiedId(aliceUserId, "wire.test"),
                    deviceId = "a11ce"
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
            assertEquals(QualifiedId(aliceUserId, "wire.test"), decrypted.sender)

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
            IsolatedKoinContext.setCryptographyStorageKey(TestUtils.CRYPTOGRAPHY_STORAGE_KEY)
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
