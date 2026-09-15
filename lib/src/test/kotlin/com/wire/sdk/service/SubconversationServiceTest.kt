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
import com.wire.sdk.client.CallingApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.DecryptedMlsMessage
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.StandardError
import com.wire.sdk.model.calling.SubconversationEpochInfo
import com.wire.sdk.model.http.conversation.SubconversationResponse
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.utils.MlsTestFixtures
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SubconversationServiceTest {
    private class Fixture : AutoCloseable {
        val id = QualifiedId(UUID.randomUUID(), "wire.test")
        val self = QualifiedId(UUID.randomUUID(), "wire.test")
        val group = ConversationId(byteArrayOf(1, 2, 3))
        val api = mockk<CallingApiClient>()
        val crypto = mockk<CryptoClient>(relaxed = true)
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
        val service = SubconversationService(api, crypto, app)

        init {
            coEvery { api.getConference(id) } answers { remote }
            coEvery { api.getGroupInfo(id) } returns MlsTestFixtures.groupInfoBytes()
            coEvery { api.leaveConference(id) } answers {
                remote = remote.copy(members = emptyList())
            }
            coEvery { crypto.conversationExists(group) } answers { exists }
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
            }
        }

        override fun close() = service.close()
    }

    @Test
    fun `duplicate and buffered messages do not trigger recovery`() =
        runTest {
            Fixture().use { f ->
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
            Fixture().use { f ->
                f.service.join(f.id).use { assertEquals(1, it.epoch) }
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, any()) } returns
                    DecryptedMlsMessage(null, null)
                assertNull(f.service.decrypt(f.id, "unchanged")?.epochInfo)
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `uninitialized conference is rejected without creating or joining a group`() =
        runTest {
            Fixture().use { f ->
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
            Fixture().use { f ->
                coEvery { f.api.getConference(f.id) } throws WireException.EntityNotFound()
                assertFailsWith<WireException.EntityNotFound> { f.service.join(f.id) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
                coVerify(exactly = 0) { f.crypto.createConversation(any(), any()) }
            }
        }

    @Test
    fun `new participant joins an initialized conference by external commit`() =
        runTest {
            Fixture().use { f ->
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
            Fixture().use { f ->
                val message = DecryptedMlsMessage(null, null)
                coEvery { f.crypto.decryptMls(f.group, "message") } returns message
                val result = assertNotNull(f.service.decrypt(f.id, "message"))
                assertEquals(message, result.message)
                assertNotNull(result.epochInfo).use { assertEquals(1, it.epoch) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `events for a conference this device has not joined are consumed without joining`() =
        runTest {
            Fixture().use { f ->
                f.remote = f.remote.copy(members = emptyList())
                f.exists = false
                assertNull(f.service.decrypt(f.id, "message"))
                coVerify(exactly = 0) { f.crypto.decryptMls(any(), any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `remote membership without local keys does not silently rejoin`() =
        runTest {
            Fixture().use { f ->
                f.exists = false
                assertNull(f.service.decrypt(f.id, "message"))
                coVerify(exactly = 0) { f.crypto.decryptMls(any(), any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `incoming epoch updates are returned once per epoch`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, "commit") } answers {
                    f.epoch++
                    DecryptedMlsMessage(null, null)
                }
                coEvery { f.crypto.decryptMls(f.group, "unchanged") } returns
                    DecryptedMlsMessage(null, null)
                val result = assertNotNull(f.service.decrypt(f.id, "commit"))
                assertFalse(result.hasLeft)
                assertNotNull(result.epochInfo).use { assertEquals(2, it.epoch) }
                assertNull(f.service.decrypt(f.id, "unchanged")?.epochInfo)
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
            }
        }

    @Test
    fun `proposal waits for another client commit without scheduling local updates`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, "proposal") } returns
                    DecryptedMlsMessage(null, null)
                assertNull(f.service.decrypt(f.id, "proposal")?.epochInfo)
                advanceTimeBy(60000.milliseconds)
                assertEquals(1, f.epoch)
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }

                coEvery { f.crypto.decryptMls(f.group, "commit") } answers {
                    f.epoch++
                    DecryptedMlsMessage(null, null)
                }
                assertNotNull(f.service.decrypt(f.id, "commit")?.epochInfo).use {
                    assertEquals(2, it.epoch)
                }
            }
        }

    @Test
    fun `decryption failure never automatically rejoins even when remote epoch is newer`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                f.remote = f.remote.copy(epoch = 5u)
                coEvery { f.crypto.decryptMls(f.group, any()) } throws MlsException.Other("invalid")
                assertFailsWith<WireException.CryptographicSystemError> {
                    f.service.decrypt(f.id, "invalid")
                }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
                coVerify(exactly = 0) { f.api.getGroupInfo(any()) }
                coVerify(exactly = 0) { f.crypto.updateKeyingMaterial(any()) }
            }
        }

    @Test
    fun `decrypt failure retains mapping for removal even if backend metadata is gone`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                coEvery { f.api.getConference(f.id) } throws WireException.ClientError(
                    StandardError(404, "no-conversation", "Conference not found"),
                    null
                )
                coEvery { f.crypto.decryptMls(f.group, any()) } throws MlsException.Other("removed")
                assertFailsWith<WireException.CryptographicSystemError> {
                    f.service.decrypt(f.id, "invalid")
                }
                coEvery { f.crypto.decryptMls(f.group, "remove") } answers {
                    f.exists = false
                    DecryptedMlsMessage(null, null, isActive = false)
                }
                assertTrue(assertNotNull(f.service.decrypt(f.id, "remove")).hasLeft)
                coVerify(exactly = 1) { f.api.getConference(f.id) }
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `restart restores pending removal despite missing backend membership`() =
        runTest {
            Fixture().use { f ->
                f.remote = f.remote.copy(members = emptyList())
                coEvery { f.crypto.decryptMls(f.group, "remove") } answers {
                    f.exists = false
                    DecryptedMlsMessage(null, null, isActive = false)
                }
                assertTrue(assertNotNull(f.service.decrypt(f.id, "remove")).hasLeft)
                coVerify(exactly = 1) { f.crypto.decryptMls(f.group, "remove") }
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
                coVerify(exactly = 0) { f.crypto.createConversation(any(), any()) }
                coVerify(exactly = 0) { f.crypto.joinMlsConversationRequest(any()) }
            }
        }

    @Test
    fun `removal is returned once without SDK wiping or exporting a key`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, any()) } answers {
                    f.exists = false
                    DecryptedMlsMessage(null, null, isActive = false)
                }
                val result = assertNotNull(f.service.decrypt(f.id, "remove"))
                assertTrue(result.hasLeft)
                assertNull(result.epochInfo)
                assertNull(f.service.decrypt(f.id, "replay"))
                coVerify(exactly = 1) { f.crypto.decryptMls(f.group, any()) }
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
                coVerify(exactly = 1) { f.crypto.getConferenceEpochInfo(any(), any()) }
            }
        }

    @Test
    fun `buffered removal also returns departure`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                coEvery { f.crypto.decryptMls(f.group, any()) } returns DecryptedMlsMessage(
                    null,
                    null,
                    bufferedMessages = listOf(DecryptedMlsMessage(null, null, isActive = false))
                )
                val result = assertNotNull(f.service.decrypt(f.id, "commit"))
                assertTrue(result.hasLeft)
                assertNull(result.epochInfo)
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
            }
        }

    @Test
    fun `leave retains mapping until removal is committed`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                f.service.leave(f.id)
                f.service.leave(f.id)
                coVerify(exactly = 1) { f.api.leaveConference(f.id) }
                coVerify(exactly = 3) { f.api.getConference(f.id) }
                coEvery { f.api.getConference(f.id) } throws WireException.EntityNotFound()
                coEvery { f.crypto.decryptMls(f.group, "remove") } answers {
                    f.exists = false
                    DecryptedMlsMessage(null, null, isActive = false)
                }
                assertTrue(assertNotNull(f.service.decrypt(f.id, "remove")).hasLeft)
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
            }
        }

    @Test
    fun `failed backend leave preserves local membership`() =
        runTest {
            Fixture().use { f ->
                f.service.join(f.id).close()
                coEvery { f.api.leaveConference(f.id) } throws WireException.Forbidden()
                assertFailsWith<WireException.Forbidden> { f.service.leave(f.id) }
                coEvery { f.crypto.decryptMls(f.group, "commit") } answers {
                    f.epoch++
                    DecryptedMlsMessage(null, null)
                }
                assertNotNull(f.service.decrypt(f.id, "commit")?.epochInfo).use {
                    assertEquals(2, it.epoch)
                }
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
            }
        }

    @Test
    fun `leave after restart fetches metadata once and retains pending removal mapping`() =
        runTest {
            Fixture().use { f ->
                f.service.leave(f.id)
                coVerify(exactly = 1) { f.api.getConference(f.id) }
                coEvery { f.api.getConference(f.id) } throws WireException.EntityNotFound()
                coEvery { f.crypto.decryptMls(f.group, "remove") } answers {
                    f.exists = false
                    DecryptedMlsMessage(null, null, isActive = false)
                }
                assertTrue(assertNotNull(f.service.decrypt(f.id, "remove")).hasLeft)
                coVerify(exactly = 0) { f.crypto.wipeConversation(any()) }
            }
        }

    @Test
    fun `closing service leaves returned snapshots under caller ownership`() =
        runTest {
            Fixture().use { f ->
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
                val result = assertNotNull(f.service.decrypt(f.id, "message"))
                f.service.close()
                assertEquals(info, result.epochInfo)
                assertContentEquals(ByteArray(32) { 1 }, info.getSharedSecret())
                info.close()
                coVerify(exactly = 0) { f.api.leaveConference(any()) }
            }
        }

    @Test
    fun `epoch snapshot copies and clears secret without exposing it in text`() =
        runTest {
            Fixture().use { f ->
                val info = f.service.join(f.id)
                val copy = info.getSharedSecret()
                copy.fill(9)
                assertContentEquals(ByteArray(32) { 1 }, info.getSharedSecret())
                info.close()
                assertContentEquals(ByteArray(32), info.getSharedSecret())
            }
        }
}
