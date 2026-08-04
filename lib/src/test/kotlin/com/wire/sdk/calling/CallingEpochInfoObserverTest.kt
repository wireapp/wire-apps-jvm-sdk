/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.sdk.calling

import com.wire.crypto.ClientId
import com.wire.crypto.ConversationId
import com.wire.crypto.EpochObserver
import com.wire.sdk.calling.types.EpochInfo
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.QualifiedId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import java.util.UUID

class CallingEpochInfoObserverTest {
    @Test
    fun `startObserving sends current epoch info and forwards epoch changes`() =
        runTest {
            val mlsGroupId = ConversationId("mls-group-id".toByteArray())
            val conversationId = QualifiedId(
                id = UUID.randomUUID(),
                domain = "example.com"
            )
            val registeredObserver = slot<EpochObserver>()
            val updates = mutableListOf<Pair<QualifiedId, EpochInfo>>()
            val cryptoClient = mockk<CryptoClient> {
                coEvery { registerEpochObserver(any(), capture(registeredObserver)) } returns Unit
                coEvery { conversationEpoch(mlsGroupId) } returns INITIAL_EPOCH
                coEvery { getClientIds(mlsGroupId) } returns listOf(
                    ClientId("user-id:client-id@example.com".toByteArray())
                )
                coEvery {
                    exportSecretKey(
                        mlsGroupId = mlsGroupId,
                        keyLength = AVS_SECRET_KEY_LENGTH
                    )
                } returns SHARED_SECRET
            }
            val epochInfoObserver = CallingEpochInfoObserver(
                cryptoClient = cryptoClient,
                scope = this,
                updateEpochInfo = { updateConversationId, epochInfo ->
                    updates += updateConversationId to epochInfo
                }
            )

            epochInfoObserver.startObserving(
                conversationId = conversationId,
                mlsGroupId = mlsGroupId
            )
            registeredObserver.captured.epochChanged(mlsGroupId, NEXT_EPOCH)

            assertEquals(2, updates.size)
            assertEquals(conversationId, updates[0].first)
            assertEquals(INITIAL_EPOCH, updates[0].second.epoch)
            assertEquals(NEXT_EPOCH, updates[1].second.epoch)
            assertContentEquals(SHARED_SECRET, updates[1].second.sharedSecret)
            assertEquals("user-id@example.com", updates[1].second.members.clients.single().userId)
            assertEquals("client-id", updates[1].second.members.clients.single().clientId)
            coVerify(exactly = 1) { cryptoClient.registerEpochObserver(any(), any()) }
        }

    @Test
    fun `stopObserving ignores future epoch changes`() =
        runTest {
            val mlsGroupId = ConversationId("mls-group-id".toByteArray())
            val conversationId = QualifiedId(
                id = UUID.randomUUID(),
                domain = "example.com"
            )
            val registeredObserver = slot<EpochObserver>()
            val updates = mutableListOf<EpochInfo>()
            val cryptoClient = mockk<CryptoClient> {
                coEvery { registerEpochObserver(any(), capture(registeredObserver)) } returns Unit
                coEvery { conversationEpoch(mlsGroupId) } returns INITIAL_EPOCH
                coEvery { getClientIds(mlsGroupId) } returns listOf(
                    ClientId("user-id:client-id@example.com".toByteArray())
                )
                coEvery {
                    exportSecretKey(
                        mlsGroupId = mlsGroupId,
                        keyLength = AVS_SECRET_KEY_LENGTH
                    )
                } returns SHARED_SECRET
            }
            val epochInfoObserver = CallingEpochInfoObserver(
                cryptoClient = cryptoClient,
                scope = this,
                updateEpochInfo = { _, epochInfo -> updates += epochInfo }
            )

            epochInfoObserver.startObserving(
                conversationId = conversationId,
                mlsGroupId = mlsGroupId
            )
            epochInfoObserver.stopObserving(conversationId)
            registeredObserver.captured.epochChanged(mlsGroupId, NEXT_EPOCH)

            assertEquals(1, updates.size)
            assertEquals(INITIAL_EPOCH, updates.single().epoch)
        }

    private companion object {
        const val AVS_SECRET_KEY_LENGTH = 32u
        const val INITIAL_EPOCH = 1UL
        const val NEXT_EPOCH = 2UL
        val SHARED_SECRET = byteArrayOf(1, 2, 3)
    }
}
