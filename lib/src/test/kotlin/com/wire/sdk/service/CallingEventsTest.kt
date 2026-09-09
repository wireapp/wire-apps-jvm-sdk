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
import com.wire.sdk.WireEventsHandler
import com.wire.sdk.WireEventsHandlerDefault
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.DecryptedMlsMessage
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.http.EventContentDTO
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.protobuf.ProtobufDeserializer
import com.wire.sdk.model.protobuf.ProtobufSerializer
import com.wire.sdk.service.conversation.ConversationService
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallingEventsTest {
    private val id = QualifiedId(UUID.randomUUID(), "wire.test")
    private val sender = QualifiedId(UUID.randomUUID(), "sender.test")
    private val group = ConversationId(byteArrayOf(1))
    private val content = """{"type":"REMOTEMUTE","future_field":true}"""

    @Test
    fun `call identity survives transport through a different conversation`() {
        val callId = QualifiedId(UUID.randomUUID(), "call.test")
        val original = WireMessage.Calling.create(id, content, callId)
        val result = ProtobufDeserializer.processGenericMessage(
            GenericMessage.parseFrom(ProtobufSerializer.toGenericMessageByteArray(original)),
            id,
            sender,
            original.timestamp
        )
        assertIs<WireMessage.Calling>(result)
        assertEquals(id, result.conversationId)
        assertEquals(callId, result.callConversationId)
    }

    @Test
    fun `older calling payload falls back to transport conversation for call identity`() {
        val proto = GenericMessage.newBuilder()
            .setMessageId(UUID.randomUUID().toString())
            .setCalling(
                com.wire.integrations.protobuf.messages.Messages.Calling.newBuilder()
                    .setContent(content)
            ).build()
        val result = ProtobufDeserializer.processGenericMessage(
            proto,
            id,
            sender,
            Clock.System.now()
        )
        assertIs<WireMessage.Calling>(result)
        assertEquals(id, result.callConversationId)
    }

    @Test
    fun `calling protobuf round trip preserves arbitrary content`() {
        val original = WireMessage.Calling.create(id, content)
        val result = ProtobufDeserializer.processGenericMessage(
            GenericMessage.parseFrom(ProtobufSerializer.toGenericMessageByteArray(original)),
            id,
            sender,
            original.timestamp
        )
        assertIs<WireMessage.Calling>(result)
        assertEquals(content, result.content)
        assertEquals(original.id, result.id)
        assertEquals(sender, result.sender)
    }

    @Test
    fun `both callback variants receive authenticated sender device and unchanged signaling`() =
        runTest {
            val received = mutableListOf<WireMessage.Calling>()
            val handlers = listOf<WireEventsHandler>(
                object : WireEventsHandlerDefault() {
                    override fun onCallingMessageReceived(message: WireMessage.Calling) {
                        received.add(message)
                    }
                },
                object : WireEventsHandlerSuspending() {
                    override suspend fun onCallingMessageReceived(message: WireMessage.Calling) {
                        received.add(message)
                    }
                }
            )
            val dispatcher = StandardTestDispatcher(testScheduler)
            for (handler in handlers) {
                val crypto = mockk<CryptoClient>()
                val conversations = mockk<ConversationService>()
                coEvery { conversations.getConversationById(id) } returns ConversationEntity(
                    id,
                    null,
                    null,
                    group,
                    ConversationEntity.Type.GROUP
                )
                val outgoing = WireMessage.Calling.create(id, content)
                coEvery { crypto.decryptMls(group, "encrypted") } returns DecryptedMlsMessage(
                    ProtobufSerializer.toGenericMessageByteArray(outgoing),
                    "${sender.id}:device-123@${sender.domain}"
                )
                SubconversationService(
                    mockk(),
                    crypto,
                    mockk(),
                    handler,
                    dispatcher
                ).use { service ->
                    EventsRouter(
                        mockk(),
                        mockk(),
                        conversations,
                        mockk(),
                        mockk(),
                        handler,
                        crypto,
                        mockk(),
                        service,
                        dispatcher
                    ).use { router ->
                        router.route(EventResponse("event", listOf(event(null))))
                        runCurrent()
                    }
                }
            }
            assertEquals(2, received.size)
            received.forEach {
                assertEquals(sender, it.sender)
                assertEquals("device-123", it.senderClientId)
                assertEquals(content, it.content)
            }
        }

    @Test
    fun `conference control event uses conference service and never parent decryption`() =
        runTest {
            val crypto = mockk<CryptoClient>(relaxed = true)
            val conversations = mockk<ConversationService>(relaxed = true)
            val service = mockk<SubconversationService>(relaxed = true)
            coEvery { service.decrypt(id, "encrypted") } returns
                DecryptedMlsMessage(null, null)
            EventsRouter(
                mockk(),
                mockk(),
                conversations,
                mockk(),
                mockk(),
                object : WireEventsHandlerDefault() {},
                crypto,
                mockk(),
                service,
                StandardTestDispatcher(testScheduler)
            ).use { router ->
                router.route(EventResponse("event", listOf(event("conference"))))
                runCurrent()
                coVerify(exactly = 1) { service.decrypt(id, "encrypted") }
                coVerify(exactly = 0) { crypto.decryptMls(any(), any()) }
                coVerify(exactly = 0) { conversations.getConversationById(any()) }
            }
        }

    @Test
    fun `buffered calling messages are forwarded even when commit has no application payload`() =
        runTest {
            val crypto = mockk<CryptoClient>(relaxed = true)
            val conversations = mockk<ConversationService>(relaxed = true)
            val service = mockk<SubconversationService>(relaxed = true)
            val message = WireMessage.Calling.create(id, content)
            coEvery { service.decrypt(id, "encrypted") } returns DecryptedMlsMessage(
                null,
                null,
                bufferedMessages = listOf(
                    DecryptedMlsMessage(
                        ProtobufSerializer.toGenericMessageByteArray(message),
                        "${sender.id}:device@${sender.domain}"
                    )
                )
            )
            EventsRouter(
                mockk(),
                mockk(),
                conversations,
                mockk(),
                mockk(),
                object : WireEventsHandlerDefault() {},
                crypto,
                mockk(),
                service,
                StandardTestDispatcher(testScheduler)
            ).use { router ->
                router.route(EventResponse("event", listOf(event("conference"))))
                runCurrent()
                io.mockk.verify { service.forwardCalling(match { it.id == message.id }) }
            }
        }

    private fun event(subconversation: String?) =
        EventContentDTO.Conversation.NewMLSMessageDTO(
            qualifiedConversation = id,
            qualifiedFrom = QualifiedId(UUID.randomUUID(), "untrusted.test"),
            time = Clock.System.now(),
            data = "encrypted",
            subconversation = subconversation
        )
}
