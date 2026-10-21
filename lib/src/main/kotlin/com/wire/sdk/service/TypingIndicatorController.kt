/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.conversation.TypingStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/** Owns ordered, bounded background typing traffic independently of the caller's work. */
internal class TypingIndicatorController(
    private val send: suspend (QualifiedId, TypingStatus) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val lock = Any()
    private val sessions = mutableMapOf<QualifiedId, Session>()

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 30_000L
        const val MAX_DURATION_MILLIS = 300_000L
        const val REQUEST_TIMEOUT_MILLIS = 5_000L
    }

    private class Session(var pending: Job?) {
        var references = 0
        var closed = false
        var refresh: Job? = null
        var maximum: Job? = null
    }

    fun acquire(conversationId: QualifiedId): () -> Unit =
        synchronized(lock) {
            val previous = sessions[conversationId]
            val session = if (previous == null || previous.references == 0) {
                Session(previous?.pending).also {
                    sessions[conversationId] = it
                    enqueue(conversationId, it, TypingStatus.STARTED)
                    it.refresh = scope.launch {
                        while (true) {
                            delay(REFRESH_INTERVAL_MILLIS)
                            synchronized(lock) {
                                if (!it.closed) enqueue(conversationId, it, TypingStatus.STARTED)
                            }
                        }
                    }
                    it.maximum = scope.launch {
                        delay(MAX_DURATION_MILLIS)
                        synchronized(lock) { close(conversationId, it) }
                    }
                }
            } else {
                previous
            }
            session.references++
            var released = false
            val release: () -> Unit = {
                synchronized(lock) {
                    if (!released) {
                        released = true
                        session.references--
                        if (session.references == 0) {
                            close(conversationId, session)
                            if (session.pending?.isCompleted == true) {
                                removeIdle(conversationId, session)
                            }
                        }
                    }
                }
            }
            release
        }

    // Called with lock held. The last status from the previous session is inherited by the next.
    private fun enqueue(
        conversationId: QualifiedId,
        session: Session,
        status: TypingStatus
    ) {
        val previous = session.pending
        lateinit var request: Job
        request = scope.launch(start = CoroutineStart.LAZY) {
            try {
                previous?.join()
                val skipped = synchronized(lock) {
                    status == TypingStatus.STARTED && session.closed
                }
                if (!skipped) sendStatus(conversationId, status)
            } finally {
                synchronized(lock) {
                    if (session.pending === request) removeIdle(conversationId, session)
                }
            }
        }
        session.pending = request
        request.start()
    }

    private fun close(
        conversationId: QualifiedId,
        session: Session
    ) {
        if (session.closed) return
        session.closed = true
        session.refresh?.cancel()
        session.maximum?.cancel()
        enqueue(conversationId, session, TypingStatus.STOPPED)
    }

    private fun removeIdle(
        conversationId: QualifiedId,
        session: Session
    ) {
        if (session.closed && session.references == 0 && sessions[conversationId] === session) {
            sessions.remove(conversationId)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendStatus(
        conversationId: QualifiedId,
        status: TypingStatus
    ) {
        // Auth refresh can deliberately ignore cancellation. Bound our wait independently;
        // cancelling this request still aborts its transport and prevents subsequent retries.
        val operation = scope.async { send(conversationId, status) }
        try {
            if (status == TypingStatus.STOPPED) {
                withContext(NonCancellable) {
                    withTimeout(REQUEST_TIMEOUT_MILLIS) { operation.await() }
                }
            } else {
                withTimeout(REQUEST_TIMEOUT_MILLIS) { operation.await() }
            }
        } catch (exception: TimeoutCancellationException) {
            logger.warn("Typing request timed out in conversation {}", conversationId, exception)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.warn(
                "Could not send typing status {} in conversation {}",
                status,
                conversationId,
                exception
            )
        } finally {
            operation.cancel()
        }
    }
}
