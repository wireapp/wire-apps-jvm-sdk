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
import com.wire.sdk.client.MlsApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.StandardError
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.protobuf.ProtobufDeserializer
import com.wire.sdk.service.conversation.ConversationService
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CallingMessageSendingTest {
    private val id = QualifiedId(UUID.randomUUID(), "wire.test")
    private val group = ConversationId(byteArrayOf(1))
    private val crypto = mockk<CryptoClient>()
    private val api = mockk<MlsApiClient>()
    private val fallback = mockk<MlsFallbackStrategy>(relaxed = true)
    private val conversation = ConversationEntity(
        id,
        null,
        null,
        group,
        ConversationEntity.Type.GROUP,
        messageTimer = 1000
    )
    private val conversations = mockk<ConversationService> {
        coEvery { getConversationById(id) } returns conversation
    }
    private val manager = WireApplicationManager(
        mockk(),
        mockk(),
        mockk(),
        api,
        mockk(),
        crypto,
        fallback,
        conversations,
        mockk(),
        mockk()
    )

    @Test
    fun `calling messages use encrypted protobuf without chat expiration`() =
        runTest {
            val payload = slot<ByteArray>()
            val message = WireMessage.Calling.create(id, """{"type":"SETUP"}""")
            coEvery { crypto.encryptMls(group, capture(payload)) } returns byteArrayOf(7)
            coEvery { api.sendMessage(any()) } returns Unit
            assertEquals(message.id, manager.sendMessageSuspending(message))
            val decoded = ProtobufDeserializer.processGenericMessage(
                GenericMessage.parseFrom(payload.captured),
                id,
                id,
                message.timestamp
            )
            assertIs<WireMessage.Calling>(decoded)
            assertEquals(message.content, decoded.content)
            coVerify { api.sendMessage(match { it.contentEquals(byteArrayOf(7)) }) }
        }

    @Test
    fun `backend rejection reaches the app`() =
        runTest {
            coEvery { crypto.encryptMls(any(), any()) } returns byteArrayOf(1)
            coEvery { api.sendMessage(any()) } throws WireException.ClientError(
                StandardError(403, "operation-denied", "denied"),
                null
            )
            assertFailsWith<WireException.ClientError> {
                manager.sendMessageSuspending(WireMessage.Calling.create(id, "{}"))
            }
        }

    @Test
    fun `stale signaling is reencrypted after recovery`() =
        runTest {
            coEvery { crypto.encryptMls(any(), any()) } returnsMany
                listOf(byteArrayOf(1), byteArrayOf(2))
            coEvery { api.sendMessage(match { it.contentEquals(byteArrayOf(1)) }) } throws
                WireException.ClientError(StandardError(409, "mls-stale-message", "stale"), null)
            coEvery { api.sendMessage(match { it.contentEquals(byteArrayOf(2)) }) } returns Unit
            manager.sendMessageSuspending(WireMessage.Calling.create(id, "{}"))
            coVerify(exactly = 1) { fallback.verifyConversationOutOfSync(group, id) }
            coVerify(exactly = 1) { api.sendMessage(match { it.contentEquals(byteArrayOf(2)) }) }
        }
}
