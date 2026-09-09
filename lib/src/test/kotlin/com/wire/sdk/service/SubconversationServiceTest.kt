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
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.client.CallingApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.DecryptedMlsMessage
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.StandardError
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.calling.SubconversationEpochInfo
import com.wire.sdk.model.http.conversation.SubconversationResponse
import com.wire.sdk.persistence.AppStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubconversationServiceTest {
    private class Fixture(scope: TestScope) : AutoCloseable {
        val id = QualifiedId(UUID.randomUUID(), "wire.test")
        val self = QualifiedId(UUID.randomUUID(), "wire.test")
        val group = ConversationId(byteArrayOf(1, 2, 3))
        val api = mockk<CallingApiClient>()
        val crypto = mockk<CryptoClient>(relaxed = true)
        val events = mutableListOf<String>()
        var epoch = 1L
        var exists = true
        var remote = SubconversationResponse(
            Base64.encode(group.copyBytes()),
            1u,
            listOf(SubconversationResponse.Member(self.id.toString(), self.domain, "device"))
        )
        val app = mockk<AppStorage> {
            every { getApplicationQualifiedId() } returns self
            every { getDeviceId() } returns "device"
        }
        val service = SubconversationService(
            api,
            crypto,
            app,
            object : WireEventsHandlerSuspending() {
                override suspend fun onSubconversationEpochChanged(info: SubconversationEpochInfo) {
                    info.use { events.add("epoch:${it.epoch}") }
                }

                override suspend fun onSubconversationLeft(conversationId: QualifiedId) {
                    events.add("left")
                }

                override suspend fun onCallingMessageReceived(message: WireMessage.Calling) {
                    events.add(message.content)
                }

                override suspend fun onCallingError(
                    conversationId: QualifiedId,
                    error: WireException
                ) {
                    events.add("error")
                }
            },
            StandardTestDispatcher(scope.testScheduler)
        )

        init {
            coEvery { api.getConference(id) } answers { remote }
            coEvery { api.getGroupInfo(id) } returns byteArrayOf(5)
            coEvery { api.leaveConference(id) } answers {
                remote = remote.copy(members = emptyList())
            }
            coEvery { crypto.conversationExists(group) } answers { exists }
            coEvery { crypto.wipeConversation(group) } answers { exists = false }
            coEvery { crypto.conversationEpoch(group) } answers { epoch.toULong() }
            coEvery { crypto.getConferenceEpochInfo(id, group) } answers {
                SubconversationEpochInfo(
                    id,
                    remote.groupId,
                    epoch,
                    emptyMap(),
                    ByteArray(32) {
                        1
                    }
                )
            }
            coEvery { crypto.joinMlsConversationRequest(any()) } answers {
                exists = true
                epoch = remote.epoch.toLong() + 1
                group
            }
        }

        override fun close() = service.close()
    }

    @Test
    fun `duplicate and buffered messages do not trigger recovery`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                val errors = listOf(
                    MlsException.DuplicateMessage(),
                    MlsException.BufferedFutureMessage(),
                    MlsException.SelfCommitIgnored()
                )
                errors.forEach { error ->
                    coEvery { f.crypto.decryptMls(f.group, any()) } throws
                        CoreCryptoException.Mls(error)
                    assertNull(f.service.decrypt(f.id, "replay"))
                }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
                coVerify(exactly = 1) { f.api.getConference(f.id) }
            }
        }

    @Test
    fun `existing membership returns initial key without joining or emitting duplicate epochs`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).use { assertEquals(1, it.epoch) }
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, any()) } returns
                    DecryptedMlsMessage(null, null)
                f.service.decrypt(f.id, "unchanged")
                runCurrent()
                assertEquals(emptyList(), f.events)
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `uninitialized conference is rejected without creating or joining a group`() =
        runTest {
            Fixture(this).use { f ->
                f.remote = f.remote.copy(epoch = 0u, members = emptyList())
                f.exists = false
                assertFailsWith<WireException.EntityNotFound> { f.service.join(f.id) }
                coVerify(exactly = 0) { f.api.getGroupInfo(any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
                coVerify(exactly = 0) { f.crypto.createConversation(any(), any()) }
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
            }
        }

    @Test
    fun `absent conference fails join without attempting initialization`() =
        runTest {
            Fixture(this).use { f ->
                coEvery { f.api.getConference(f.id) } throws WireException.EntityNotFound()
                assertFailsWith<WireException.EntityNotFound> { f.service.join(f.id) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
                coVerify(exactly = 0) { f.crypto.createConversation(any(), any()) }
            }
        }

    @Test
    fun `new participant joins an initialized conference by external commit`() =
        runTest {
            Fixture(this).use { f ->
                f.remote = f.remote.copy(members = emptyList())
                f.exists = false
                f.service.join(f.id).use { assertEquals(2, it.epoch) }
                coVerify(exactly = 1) { f.crypto.joinMlsConversationRequest(any()) }
                coVerify(exactly = 0) { f.crypto.createConversation(any(), any()) }
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
            }
        }

    @Test
    fun `restart restores mapping without a join and decrypts with conference group`() =
        runTest {
            Fixture(this).use { f ->
                val message = DecryptedMlsMessage(null, null)
                coEvery { f.crypto.decryptMls(f.group, "message") } returns message
                assertEquals(message, f.service.decrypt(f.id, "message"))
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `events for a conference this device has not joined are consumed without joining`() =
        runTest {
            Fixture(this).use { f ->
                f.remote = f.remote.copy(members = emptyList())
                assertNull(f.service.decrypt(f.id, "message"))
                coVerify(exactly = 0) { f.crypto.decryptMls(any(), any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `remote membership without local keys does not silently rejoin`() =
        runTest {
            Fixture(this).use { f ->
                f.exists = false
                assertNull(f.service.decrypt(f.id, "message"))
                coVerify(exactly = 0) { f.crypto.decryptMls(any(), any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `incoming epoch updates and signaling stay ordered and deduplicated`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, "commit") } answers {
                    f.epoch++
                    DecryptedMlsMessage(null, null)
                }
                coEvery { f.crypto.decryptMls(f.group, "unchanged") } returns
                    DecryptedMlsMessage(null, null)
                f.service.decrypt(f.id, "commit")
                f.service.forwardCalling(WireMessage.Calling.create(f.id, "signal"))
                f.service.decrypt(f.id, "unchanged")
                runCurrent()
                assertEquals(listOf("epoch:2", "signal"), f.events)
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
            }
        }

    @Test
    fun `proposal waits for another client commit without scheduling local updates`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, "proposal") } returns
                    DecryptedMlsMessage(null, null)
                f.service.decrypt(f.id, "proposal")
                advanceTimeBy(60000)
                runCurrent()
                assertEquals(emptyList(), f.events)
                assertEquals(1, f.epoch)
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }

                coEvery { f.crypto.decryptMls(f.group, "commit") } answers {
                    f.epoch++
                    DecryptedMlsMessage(null, null)
                }
                f.service.decrypt(f.id, "commit")
                runCurrent()
                assertEquals(listOf("epoch:2"), f.events)
            }
        }

    @Test
    fun `decryption failure never automatically rejoins even when remote epoch is newer`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                f.remote = f.remote.copy(epoch = 5u)
                coEvery { f.crypto.decryptMls(f.group, any()) } throws MlsException.Other("invalid")
                assertFailsWith<WireException.CryptographicSystemError> {
                    f.service.decrypt(f.id, "invalid")
                }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
                coVerify(exactly = 0) { f.api.getGroupInfo(any()) }
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
                runCurrent()
                assertEquals(emptyList(), f.events)
            }
        }

    @Test
    fun `removed conference discovered after decrypt failure notifies departure`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                coEvery { f.api.getConference(f.id) } throws WireException.ClientError(
                    StandardError(404, "no-conversation", "Conference not found"),
                    null
                )
                coEvery { f.crypto.decryptMls(f.group, any()) } throws MlsException.Other("removed")
                assertFailsWith<WireException.CryptographicSystemError> {
                    f.service.decrypt(f.id, "invalid")
                }
                runCurrent()
                assertEquals(listOf("left"), f.events)
                coVerify(exactly = 1) { f.crypto.wipeConversation(f.group) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `reset conference discovered after decrypt failure is not initialized again`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                f.remote = f.remote.copy(epoch = 0u)
                coEvery { f.crypto.decryptMls(f.group, any()) } throws MlsException.Other("reset")
                assertFailsWith<WireException.CryptographicSystemError> {
                    f.service.decrypt(f.id, "invalid")
                }
                runCurrent()
                assertEquals(listOf("left"), f.events)
                coVerify(exactly = 0) { f.crypto.createConversation(any(), any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `removal clears local keys and notifies departure without publishing a key`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, any()) } returns
                    DecryptedMlsMessage(null, null, isActive = false)
                f.service.decrypt(f.id, "remove")
                runCurrent()
                assertEquals(listOf("left"), f.events)
                coVerify { f.crypto.wipeConversation(f.group) }
                coVerify(exactly = 1) { f.crypto.getConferenceEpochInfo(any(), any()) }
            }
        }

    @Test
    fun `buffered removal also notifies departure`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, any()) } returns DecryptedMlsMessage(
                    null,
                    null,
                    bufferedMessages = listOf(DecryptedMlsMessage(null, null, isActive = false))
                )
                f.service.decrypt(f.id, "commit")
                runCurrent()
                assertEquals(listOf("left"), f.events)
            }
        }

    @Test
    fun `leave notifies once and repeated leave is harmless`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                f.service.leave(f.id)
                f.service.leave(f.id)
                runCurrent()
                assertEquals(listOf("left"), f.events)
                coVerify(exactly = 1) { f.api.leaveConference(f.id) }
                coVerify(exactly = 1) { f.crypto.wipeConversation(f.group) }
            }
        }

    @Test
    fun `failed backend leave preserves local membership`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                coEvery { f.api.leaveConference(f.id) } throws WireException.Forbidden()
                assertFailsWith<WireException.Forbidden> { f.service.leave(f.id) }
                coEvery { f.crypto.decryptMls(f.group, "commit") } answers {
                    f.epoch++
                    DecryptedMlsMessage(null, null)
                }
                f.service.decrypt(f.id, "commit")
                runCurrent()
                assertEquals(listOf("epoch:2"), f.events)
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
            }
        }

    @Test
    fun `parent removal forgets conference once without deleting parent keys`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                f.service.forgetParent(f.id)
                f.service.forgetParent(f.id)
                runCurrent()
                coVerify(exactly = 1) { f.crypto.wipeConversation(f.group) }
                assertEquals(listOf("left"), f.events)
            }
        }

    @Test
    fun `closing SDK discards queued epoch secrets without leaving remotely`() =
        runTest {
            Fixture(this).use { f ->
                f.service.join(f.id).close()
                val info = SubconversationEpochInfo(
                    f.id,
                    f.remote.groupId,
                    2,
                    emptyMap(),
                    ByteArray(32) { 1 }
                )
                coEvery { f.crypto.getConferenceEpochInfo(f.id, f.group) } returns info
                coEvery { f.crypto.decryptMls(f.group, any()) } returns
                    DecryptedMlsMessage(null, null)
                f.service.decrypt(f.id, "message")
                f.service.close()
                runCurrent()
                assertContentEquals(ByteArray(32), info.getSharedSecret())
                assertEquals(emptyList(), f.events)
                coVerify(exactly = 0) { f.api.leaveConference(any()) }
            }
        }

    @Test
    fun `epoch snapshot copies and clears secret without exposing it in text`() =
        runTest {
            Fixture(this).use { f ->
                val info = f.service.join(f.id)
                val copy = info.getSharedSecret()
                copy.fill(9)
                assertContentEquals(ByteArray(32) { 1 }, info.getSharedSecret())
                info.close()
                assertContentEquals(ByteArray(32), info.getSharedSecret())
            }
        }
}
