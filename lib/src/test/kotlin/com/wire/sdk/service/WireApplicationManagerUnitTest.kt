/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.sdk.service

import com.wire.crypto.ConversationId
import com.wire.sdk.client.BackendClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.protobuf.ProtobufSerializer
import com.wire.sdk.persistence.TeamStorage
import com.wire.sdk.service.conversation.ConversationService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.assertThrows

class WireApplicationManagerUnitTest {
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `sendMessageSuspending uses original message when conversation has no messageTimer`() =
        runTest {
            // Arrange
            val conversationId = QualifiedId(UUID.randomUUID(), "example.com")
            val mlsGroupId = ConversationId(UUID.randomUUID().toString().toByteArray())

            val conversationEntity = ConversationEntity(
                id = conversationId,
                name = "test",
                teamId = null,
                mlsGroupId = mlsGroupId,
                type = ConversationEntity.Type.GROUP,
                messageTimer = null
            )

            val conversationService = mockk<ConversationService>(relaxed = true)
            coEvery { conversationService.getConversationById(conversationId) } returns
                conversationEntity

            val backendClient = mockk<BackendClient>(relaxed = true)
            coEvery { backendClient.sendMessage(any()) } returns Unit

            val cryptoClient = mockk<CryptoClient>(relaxed = true)
            coEvery { cryptoClient.encryptMls(any(), any()) } returns byteArrayOf(1, 2, 3)

            val mlsFallbackStrategy = mockk<MlsFallbackStrategy>(relaxed = true)
            val teamStorage = mockk<TeamStorage>(relaxed = true)

            val manager = WireApplicationManager(
                teamStorage = teamStorage,
                backendClient = backendClient,
                cryptoClient = cryptoClient,
                mlsFallbackStrategy = mlsFallbackStrategy,
                conversationService = conversationService
            )

            val originalMessage = WireMessage.Text.create(
                conversationId = conversationId,
                text = "hello",
                expiresAfterMillis = null
            )

            // Capture the WireMessage passed to ProtobufSerializer via mocking the object
            mockkObject(ProtobufSerializer)
            val captured = slot<WireMessage>()
            every { ProtobufSerializer.toGenericMessageByteArray(capture(captured)) } answers {
                byteArrayOf(
                    9
                )
            }

            // Act
            val resultId = manager.sendMessageSuspending(originalMessage)

            // Assert
            assertEquals(originalMessage.id, resultId)
            // The captured message should be the original one (no override)
            assertEquals(originalMessage.id, (captured.captured as WireMessage).id)
            assertEquals(
                originalMessage.expiresAfterMillis,
                (captured.captured as WireMessage.Text).expiresAfterMillis
            )

            coVerify(exactly = 1) { cryptoClient.encryptMls(any(), any()) }
            coVerify(exactly = 1) { backendClient.sendMessage(any()) }
        }

    @Test
    fun `expiration is overridden when conversation has messageTimer and message is ephemeral`() =
        runTest {
            // Arrange
            val conversationId = QualifiedId(UUID.randomUUID(), "example.com")
            val mlsGroupId = ConversationId(UUID.randomUUID().toString().toByteArray())

            val messageTimerValue = 5_000L
            val conversationEntity = ConversationEntity(
                id = conversationId,
                name = "test",
                teamId = null,
                mlsGroupId = mlsGroupId,
                type = ConversationEntity.Type.GROUP,
                messageTimer = messageTimerValue
            )

            val conversationService = mockk<ConversationService>(relaxed = true)
            coEvery { conversationService.getConversationById(conversationId) } returns
                conversationEntity

            val backendClient = mockk<BackendClient>(relaxed = true)
            coEvery { backendClient.sendMessage(any()) } returns Unit

            val cryptoClient = mockk<CryptoClient>(relaxed = true)
            coEvery { cryptoClient.encryptMls(any(), any()) } returns byteArrayOf(2)

            val mlsFallbackStrategy = mockk<MlsFallbackStrategy>(relaxed = true)
            val teamStorage = mockk<TeamStorage>(relaxed = true)

            val manager = WireApplicationManager(
                teamStorage = teamStorage,
                backendClient = backendClient,
                cryptoClient = cryptoClient,
                mlsFallbackStrategy = mlsFallbackStrategy,
                conversationService = conversationService
            )

            val originalMessage = WireMessage.Text.create(
                conversationId = conversationId,
                text = "ephemeral",
                expiresAfterMillis = null
            )

            mockkObject(ProtobufSerializer)
            val captured = slot<WireMessage>()
            every { ProtobufSerializer.toGenericMessageByteArray(capture(captured)) } answers {
                byteArrayOf(
                    3
                )
            }

            // Act
            val resultId = manager.sendMessageSuspending(originalMessage)

            // Assert
            assertEquals(originalMessage.id, resultId)
            val capturedMessage = captured.captured
            // Should have overridden expiresAfterMillis to conversation.messageTimer
            val expires = when (capturedMessage) {
                is WireMessage.Text -> capturedMessage.expiresAfterMillis
                is WireMessage.Asset -> capturedMessage.expiresAfterMillis
                is WireMessage.Location -> capturedMessage.expiresAfterMillis
                is WireMessage.Ping -> capturedMessage.expiresAfterMillis
                else -> null
            }
            assertEquals(messageTimerValue, expires)

            coVerify(exactly = 1) { cryptoClient.encryptMls(any(), any()) }
            coVerify(exactly = 1) { backendClient.sendMessage(any()) }
        }

    @Test
    fun `throws when conversation has messageTimer and message is not ephemeral`() =
        runTest {
            // Arrange
            val conversationId = QualifiedId(UUID.randomUUID(), "example.com")
            val mlsGroupId = ConversationId(UUID.randomUUID().toString().toByteArray())

            val messageTimerValue = 10_000L
            val conversationEntity = ConversationEntity(
                id = conversationId,
                name = "test",
                teamId = null,
                mlsGroupId = mlsGroupId,
                type = ConversationEntity.Type.GROUP,
                messageTimer = messageTimerValue
            )

            val conversationService = mockk<ConversationService>(relaxed = true)
            coEvery { conversationService.getConversationById(conversationId) } returns
                conversationEntity

            val backendClient = mockk<BackendClient>(relaxed = true)
            val cryptoClient = mockk<CryptoClient>(relaxed = true)
            val mlsFallbackStrategy = mockk<MlsFallbackStrategy>(relaxed = true)
            val teamStorage = mockk<TeamStorage>(relaxed = true)

            val manager = WireApplicationManager(
                teamStorage = teamStorage,
                backendClient = backendClient,
                cryptoClient = cryptoClient,
                mlsFallbackStrategy = mlsFallbackStrategy,
                conversationService = conversationService
            )

            // Create a non-ephemeral message (Reaction is not Ephemeral)
            val reaction = WireMessage.Reaction.create(
                conversationId = conversationId,
                messageId = "msg-id",
                emojiSet = setOf("🙂")
            )

            // Act & Assert
            assertThrows<WireException.InvalidParameter> {
                manager.sendMessageSuspending(reaction)
            }
        }
}
