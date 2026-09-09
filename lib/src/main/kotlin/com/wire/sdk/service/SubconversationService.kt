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
import com.wire.crypto.toGroupInfo
import com.wire.sdk.WireEventsHandler
import com.wire.sdk.WireEventsHandlerDefault
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.client.CallingApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.DecryptedMlsMessage
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.calling.SubconversationEpochInfo
import com.wire.sdk.model.http.conversation.SubconversationResponse
import com.wire.sdk.persistence.AppStorage
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory

/**
 * Joins the single "conference" child group and applies incoming MLS updates.
 * Other clients initialize conferences and commit proposals. CoreCrypto persists MLS state;
 * the backend supplies the group ID and membership when this process has no cached mapping.
 */
@Suppress("TooManyFunctions")
internal class SubconversationService(
    private val api: CallingApiClient,
    private val crypto: CryptoClient,
    private val appStorage: AppStorage,
    private val handler: WireEventsHandler,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : AutoCloseable {
    private class Conference(val groupId: ConversationId) {
        var lastEpoch: Long? = null
    }

    private class Callback(
        val discard: () -> Unit,
        val action: suspend () -> Unit
    )

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val conferences = ConcurrentHashMap<QualifiedId, Conference>()
    private val locks = ConcurrentHashMap<QualifiedId, Mutex>()
    private val callbacks = ConcurrentHashMap<QualifiedId, Channel<Callback>>()

    suspend fun join(conversationId: QualifiedId): SubconversationEpochInfo =
        operation {
            lock(conversationId).withLock {
                val remote = api.getConference(conversationId)
                joinRemote(conversationId, remote)
                val conference = conferences.getValue(conversationId)
                snapshot(conversationId, conference).also { conference.lastEpoch = it.epoch }
            }
        }

    private suspend fun joinRemote(
        id: QualifiedId,
        remote: SubconversationResponse
    ) {
        if (remote.epoch == 0uL) {
            throw WireException.EntityNotFound(
                "The conference has not been initialized by another client"
            )
        }
        var groupId = ConversationId(Base64.decode(remote.groupId))
        val exists = crypto.conversationExists(groupId)
        val alreadyJoined = remote.hasSelf() &&
            exists &&
            crypto.conversationEpoch(groupId) == remote.epoch
        if (!alreadyJoined) {
            // Joining is the only conference commit the SDK initiates.
            crypto.joinMlsConversationRequest(api.getGroupInfo(id).toGroupInfo())
        }
        val previous = conferences[id]
        if (previous?.groupId != groupId) {
            conferences[id] = Conference(groupId)
            if (previous != null && crypto.conversationExists(previous.groupId)) {
                crypto.wipeConversation(previous.groupId)
            }
        }
    }

    suspend fun leave(conversationId: QualifiedId) =
        operation {
            lock(conversationId).withLock {
                val conference = conferences[conversationId] ?: restore(conversationId)
                // Check remote membership so repeat leaves and restart recovery are safe.
                if (api.getConference(conversationId).hasSelf()) api.leaveConference(conversationId)
                if (conference != null) forget(conversationId, conference)
            }
        }

    /** Unknown/non-member conferences are consumed without implicitly joining the call. */
    suspend fun decrypt(
        conversationId: QualifiedId,
        data: String
    ): DecryptedMlsMessage? =
        operation {
            lock(conversationId).withLock {
                val conference = conferences[conversationId] ?: restore(conversationId)
                    ?: return@withLock null
                val decrypted = try {
                    crypto.decryptMls(conference.groupId, data)
                } catch (exception: CoreCryptoException.Mls) {
                    if (exception.mlsError.isConsumed()) return@withLock null
                    reconcile(conversationId, conference)
                    throw exception
                } catch (exception: MlsException) {
                    if (exception.isConsumed()) return@withLock null
                    reconcile(conversationId, conference)
                    throw exception
                }
                val messages = listOf(decrypted) + decrypted.bufferedMessages
                if (messages.any { !it.isActive }) {
                    forget(conversationId, conference)
                } else {
                    publishEpoch(conversationId, conference)
                }
                decrypted
            }
        }

    /** Parent deletion, reset or removal also ends the app's conference participation. */
    suspend fun forgetParent(conversationId: QualifiedId) =
        operation {
            lock(conversationId).withLock {
                conferences[conversationId]?.let { forget(conversationId, it) }
            }
        }

    fun forwardCalling(message: WireMessage.Calling) {
        dispatch(message.conversationId) {
            when (handler) {
                is WireEventsHandlerDefault -> handler.onCallingMessageReceived(message)
                is WireEventsHandlerSuspending -> handler.onCallingMessageReceived(message)
            }
        }
    }

    fun reportError(
        conversationId: QualifiedId,
        error: WireException
    ) {
        dispatch(conversationId) {
            when (handler) {
                is WireEventsHandlerDefault -> handler.onCallingError(conversationId, error)
                is WireEventsHandlerSuspending -> handler.onCallingError(conversationId, error)
            }
        }
    }

    private suspend fun restore(id: QualifiedId): Conference? {
        val remote = api.getConference(id)
        val groupId = ConversationId(Base64.decode(remote.groupId))
        if (remote.epoch == 0uL ||
            !remote.hasSelf() ||
            !crypto.conversationExists(groupId)
        ) {
            return null
        }
        return Conference(groupId).also { conferences[id] = it }
    }

    private fun SubconversationResponse.hasSelf(): Boolean =
        members.any { it.matches(appStorage.getApplicationQualifiedId(), appStorage.getDeviceId()) }

    private suspend fun reconcile(
        id: QualifiedId,
        conference: Conference
    ) {
        val remote = try {
            api.getConference(id)
        } catch (exception: WireException.ClientError) {
            if (exception.response.code != HTTP_NOT_FOUND) throw exception
            forget(id, conference)
            return
        }
        if (remote.epoch == 0uL ||
            !remote.hasSelf() ||
            remote.groupId != Base64.encode(conference.groupId.copyBytes())
        ) {
            forget(id, conference)
        }
    }

    private suspend fun forget(
        id: QualifiedId,
        conference: Conference
    ) {
        if (crypto.conversationExists(conference.groupId)) {
            crypto.wipeConversation(conference.groupId)
        }
        conferences.remove(id, conference)
        dispatch(id) {
            when (handler) {
                is WireEventsHandlerDefault -> handler.onSubconversationLeft(id)
                is WireEventsHandlerSuspending -> handler.onSubconversationLeft(id)
            }
        }
    }

    private suspend fun snapshot(
        id: QualifiedId,
        conference: Conference
    ): SubconversationEpochInfo = crypto.getConferenceEpochInfo(id, conference.groupId)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun publishEpoch(
        id: QualifiedId,
        conference: Conference
    ) {
        val info = snapshot(id, conference)
        if (info.epoch == conference.lastEpoch) {
            info.close()
            return
        }
        conference.lastEpoch = info.epoch
        dispatch(id, discard = info::close) {
            try {
                when (handler) {
                    is WireEventsHandlerDefault -> handler.onSubconversationEpochChanged(info)
                    is WireEventsHandlerSuspending -> handler.onSubconversationEpochChanged(info)
                }
            } catch (exception: Exception) {
                info.close()
                throw exception
            }
        }
    }

    private fun lock(id: QualifiedId): Mutex = locks.computeIfAbsent(id) { Mutex() }

    private fun MlsException.isConsumed(): Boolean =
        this is MlsException.DuplicateMessage ||
            this is MlsException.SelfCommitIgnored ||
            this is MlsException.BufferedFutureMessage ||
            this is MlsException.BufferedCommit ||
            this is MlsException.StaleProposal ||
            this is MlsException.StaleCommit ||
            this is MlsException.MessageEpochTooOld

    // Enqueue without holding up crypto work. App callbacks can call the manager themselves.
    @Suppress("TooGenericExceptionCaught")
    private fun dispatch(
        id: QualifiedId,
        discard: () -> Unit = {},
        action: suspend () -> Unit
    ) {
        callbacks.computeIfAbsent(id) {
            Channel<Callback>(
                Channel.UNLIMITED,
                onUndeliveredElement = { it.discard() }
            ).also { channel ->
                scope.launch {
                    for (callback in channel) {
                        try {
                            callback.action()
                        } catch (exception: CancellationException) {
                            currentCoroutineContext().ensureActive()
                            logger.warn("Calling event handler cancelled for {}", id, exception)
                        } catch (exception: Exception) {
                            logger.error("Calling event handler failed for {}", id, exception)
                        }
                    }
                }
            }
        }.trySend(Callback(discard, action)).onFailure { discard() }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> operation(action: suspend () -> T): T =
        try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: WireException) {
            throw exception
        } catch (exception: CoreCryptoException) {
            throw WireException.CryptographicSystemError("Conference operation failed", exception)
        } catch (exception: MlsException) {
            throw WireException.CryptographicSystemError("Conference operation failed", exception)
        } catch (exception: Exception) {
            throw WireException.UnknownError("Conference operation failed", exception)
        }

    override fun close() {
        scope.cancel()
        callbacks.values.forEach { it.cancel() }
        conferences.clear()
        callbacks.clear()
        locks.clear()
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
