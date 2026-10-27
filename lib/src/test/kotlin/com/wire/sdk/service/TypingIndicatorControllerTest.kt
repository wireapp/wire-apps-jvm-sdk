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

import com.wire.sdk.client.ConversationsApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.Json
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.conversation.TypingStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TypingIndicatorControllerTest {
    private val id = QualifiedId(UUID.randomUUID(), "example.com")

    @Test
    fun `auth refresh can outlive the deadline but cannot replay timed out typing`() =
        runTest {
            var requests = 0
            var refreshes = 0
            val client = HttpClient(MockEngine) {
                install(ContentNegotiation) { json(Json) }
                install(Auth) {
                    bearer {
                        nonCancellableRefresh = true
                        loadTokens { BearerTokens("expired", null) }
                        refreshTokens {
                            refreshes++
                            kotlinx.coroutines.delay(60_000)
                            BearerTokens("valid", null)
                        }
                    }
                }
                engine {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        requests++
                        if (request.headers[HttpHeaders.Authorization] == "Bearer valid") {
                            respond("", HttpStatusCode.OK)
                        } else {
                            respond(
                                "",
                                HttpStatusCode.Unauthorized,
                                headersOf(HttpHeaders.WWWAuthenticate, "Bearer")
                            )
                        }
                    }
                }
            }
            try {
                val controller = TypingIndicatorController(
                    ConversationsApiClient(client)::sendTypingStatus,
                    backgroundScope
                )
                val release = controller.acquire(id)
                runCurrent()
                release()
                advanceTimeBy(10_000)
                runCurrent()
                assertEquals(2, requests)
                assertEquals(1, refreshes)
                advanceTimeBy(120_000)
                runCurrent()
                assertEquals(2, requests)
            } finally {
                client.close()
            }
        }

    @Test
    fun `overlapping work shares refreshes until the last scope ends`() =
        runTest {
            val statuses = mutableListOf<TypingStatus>()
            val controller = TypingIndicatorController(
                { _, status -> statuses += status },
                backgroundScope
            )
            val first = controller.acquire(id)
            val second = controller.acquire(id.copy())
            runCurrent()
            advanceTimeBy(30_000)
            runCurrent()
            first()
            advanceTimeBy(30_000)
            runCurrent()
            assertEquals(List(3) { TypingStatus.STARTED }, statuses)
            second()
            runCurrent()
            assertEquals(TypingStatus.STOPPED, statuses.last())
            val count = statuses.size
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(count, statuses.size)
        }

    @Test
    fun `a hanging typing request is cancelled before ordered cleanup`() =
        runTest {
            val statuses = mutableListOf<TypingStatus>()
            var cancelled = 0
            val controller = TypingIndicatorController({ _, status ->
                statuses += status
                try {
                    awaitCancellation()
                } finally {
                    cancelled++
                }
            }, backgroundScope)
            val release = controller.acquire(id)
            runCurrent()
            release()
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(listOf(TypingStatus.STARTED, TypingStatus.STOPPED), statuses)
            assertEquals(1, cancelled)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals(2, cancelled)
        }

    @Test
    fun `typing expires after five minutes while work continues`() =
        runTest {
            val statuses = mutableListOf<TypingStatus>()
            val controller = TypingIndicatorController(
                { _, status -> statuses += status },
                backgroundScope
            )
            val release = controller.acquire(id)
            runCurrent()
            advanceTimeBy(300_000)
            runCurrent()
            assertEquals(TypingStatus.STOPPED, statuses.last())
            val count = statuses.size
            val overlap = controller.acquire(id)
            advanceTimeBy(300_000)
            runCurrent()
            assertEquals(count, statuses.size)
            overlap()
            release()
            val next = controller.acquire(id)
            runCurrent()
            assertEquals(TypingStatus.STARTED, statuses.last())
            next()
            runCurrent()
        }

    @Test
    fun `a new scope sends started only after pending stopped from the previous scope`() =
        runTest {
            val statuses = mutableListOf<TypingStatus>()
            val firstRequest = CompletableDeferred<Unit>()
            val controller = TypingIndicatorController({ _, status ->
                statuses += status
                if (statuses.size == 1) firstRequest.await()
            }, backgroundScope)
            val first = controller.acquire(id)
            runCurrent()
            first()
            val second = controller.acquire(id)
            runCurrent()
            assertEquals(listOf(TypingStatus.STARTED), statuses)
            firstRequest.complete(Unit)
            runCurrent()
            assertEquals(
                listOf(TypingStatus.STARTED, TypingStatus.STOPPED, TypingStatus.STARTED),
                statuses
            )
            second()
            runCurrent()
            assertEquals(TypingStatus.STOPPED, statuses.last())
        }

    @Test
    fun `caller cancellation schedules cleanup in an active independent context`() =
        runTest {
            val statuses = mutableListOf<TypingStatus>()
            val controller = TypingIndicatorController({ _, status ->
                statuses += status
                if (status == TypingStatus.STOPPED) {
                    assertTrue(currentCoroutineContext()[Job]!!.isActive)
                }
            }, backgroundScope)
            val caller = launch {
                val release = controller.acquire(id)
                try {
                    awaitCancellation()
                } finally {
                    release()
                }
            }
            runCurrent()
            caller.cancelAndJoin()
            runCurrent()
            assertEquals(listOf(TypingStatus.STARTED, TypingStatus.STOPPED), statuses)
        }
}
