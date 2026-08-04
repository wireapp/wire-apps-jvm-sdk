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
import com.wire.sdk.calling.types.CallClient
import com.wire.sdk.calling.types.CallClientList
import com.wire.sdk.calling.types.EpochInfo
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.QualifiedId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("TooGenericExceptionCaught")
internal class CallingEpochInfoObserver(
    private val cryptoClient: CryptoClient,
    private val scope: CoroutineScope,
    private val updateEpochInfo: suspend (QualifiedId, EpochInfo) -> Unit
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val isObserverRegistered = AtomicBoolean(false)
    private val conversationIdsByGroupId = ConcurrentHashMap<ConversationId, QualifiedId>()
    private val groupIdsByConversationId = ConcurrentHashMap<QualifiedId, ConversationId>()

    suspend fun startObserving(
        conversationId: QualifiedId,
        mlsGroupId: ConversationId
    ) {
        groupIdsByConversationId.put(conversationId, mlsGroupId)?.let {
            conversationIdsByGroupId.remove(it)
        }
        conversationIdsByGroupId[mlsGroupId] = conversationId

        try {
            registerEpochObserverIfNeeded()
            sendEpochInfo(
                conversationId = conversationId,
                mlsGroupId = mlsGroupId,
                epoch = cryptoClient.conversationEpoch(mlsGroupId)
            )
        } catch (exception: Exception) {
            stopObserving(conversationId)
            throw exception
        }
    }

    fun stopObserving(conversationId: QualifiedId) {
        groupIdsByConversationId.remove(conversationId)?.let {
            conversationIdsByGroupId.remove(it)
        }
    }

    private suspend fun registerEpochObserverIfNeeded() {
        if (!isObserverRegistered.compareAndSet(false, true)) {
            return
        }

        try {
            cryptoClient.registerEpochObserver(
                scope = scope,
                observer = object : EpochObserver {
                    override suspend fun epochChanged(
                        conversationId: ConversationId,
                        epoch: ULong
                    ) {
                        handleEpochChanged(conversationId, epoch)
                    }
                }
            )
        } catch (exception: Exception) {
            isObserverRegistered.set(false)
            throw exception
        }
    }

    private suspend fun handleEpochChanged(
        mlsGroupId: ConversationId,
        epoch: ULong
    ) {
        val conversationId = conversationIdsByGroupId[mlsGroupId] ?: return

        try {
            sendEpochInfo(
                conversationId = conversationId,
                mlsGroupId = mlsGroupId,
                epoch = epoch
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.warn(
                "Failed to send epoch info for conversation {}",
                conversationId,
                exception
            )
        }
    }

    private suspend fun sendEpochInfo(
        conversationId: QualifiedId,
        mlsGroupId: ConversationId,
        epoch: ULong
    ) {
        updateEpochInfo(
            conversationId,
            EpochInfo(
                epoch = epoch,
                members = CallClientList(
                    cryptoClient.getClientIds(mlsGroupId).map { it.toCallClient() }
                ),
                sharedSecret = cryptoClient.exportSecretKey(
                    mlsGroupId = mlsGroupId,
                    keyLength = AVS_SECRET_KEY_LENGTH.toUInt()
                )
            )
        )
    }

    private fun ClientId.toCallClient(): CallClient {
        val rawClientId = copyBytes().decodeToString()
        val userAndClient = rawClientId.substringBefore(FEDERATION_SEPARATOR)
        val domain = rawClientId.substringAfter(FEDERATION_SEPARATOR, "")
        val userId = userAndClient.substringBefore(CLIENT_SEPARATOR)
        val clientId = userAndClient.substringAfter(CLIENT_SEPARATOR, rawClientId)
        val qualifiedUserId = if (domain.isEmpty()) userId else "$userId@$domain"

        return CallClient(
            userId = qualifiedUserId,
            clientId = clientId,
            isMemberOfSubconversation = true
        )
    }

    private companion object {
        const val AVS_SECRET_KEY_LENGTH = 32
        const val CLIENT_SEPARATOR = ":"
        const val FEDERATION_SEPARATOR = "@"
    }
}
