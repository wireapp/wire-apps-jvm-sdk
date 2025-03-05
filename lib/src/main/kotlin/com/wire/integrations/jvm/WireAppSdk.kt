/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.integrations.jvm

import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.service.WireApplicationManager
import com.wire.integrations.jvm.service.WireTeamEventsListener
import com.wire.integrations.jvm.utils.KtxSerializer
import com.wire.integrations.jvm.utils.mls
import com.wire.integrations.jvm.utils.xprotobuf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class WireAppSdk(
    applicationId: UUID,
    apiToken: String,
    apiHost: String,
    cryptographyStoragePassword: String,
    wireEventsHandler: WireEventsHandler
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val running = AtomicBoolean(false)
    private var executor = Executors.newSingleThreadExecutor()

    init {
        IsolatedKoinContext.setApplicationId(applicationId)
        IsolatedKoinContext.setApiHost(apiHost)
        IsolatedKoinContext.setApiToken(apiToken)
        IsolatedKoinContext.setCryptographyStoragePassword(cryptographyStoragePassword)

        initDynamicModules(
            apiHost = apiHost,
            wireEventsHandler = wireEventsHandler
        )
    }

    @Synchronized
    fun start() {
        if (running.get()) {
            logger.info("Wire Apps SDK is already running")
            return
        }
        running.set(true)
        executor = Executors.newSingleThreadExecutor()
        executor.submit { listenToWebSocketEvents() }
    }

    private fun listenToWebSocketEvents() {
        val eventsListener = IsolatedKoinContext.koinApp.koin.get<WireTeamEventsListener>()
        try {
            eventsListener.connect() // Blocks thread
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    @Synchronized
    fun stop() {
        if (!running.get()) {
            logger.info("Wire Apps SDK is not running")
            return
        }
        running.set(false)
        logger.info("Wire Apps SDK shutting down...")
        executor.shutdownNow()
    }

    fun isRunning(): Boolean = running.get()

    fun getTeamManager(): WireApplicationManager {
        return IsolatedKoinContext.koinApp.koin.get()
    }

    private fun initDynamicModules(
        apiHost: String,
        wireEventsHandler: WireEventsHandler
    ) {
        val dynamicModule =
            module {
                single {
                    wireEventsHandler
                }

                single<HttpClient> {
                    HttpClient(CIO) {
                        expectSuccess = true
                        followRedirects = true

                        install(ContentNegotiation) {
                            json(KtxSerializer.json)
                            mls()
                            xprotobuf()
                        }

                        install(WebSockets) {
                            contentConverter = KotlinxWebsocketSerializationConverter(Json)
                            pingIntervalMillis = WEBSOCKET_PING_INTERVAL_MILLIS
                        }

                        install(SSE)

                        install(Logging) {
                            level = LogLevel.ALL
//                            sanitizeHeader { header -> header == HttpHeaders.Authorization }
                        }

                        install(UserAgent) {
                            agent = "Ktor JVM SDK client"
                        }

                        install(HttpCache)
                        install(HttpRequestRetry) {
                            retryOnServerErrors(maxRetries = 3)
                            exponentialDelay()
                        }

                        defaultRequest {
                            url(apiHost)
                        }
                    }
                }
            }

        IsolatedKoinContext.koinApp.koin.loadModules(listOf(dynamicModule))
    }

    companion object {
        internal const val WEBSOCKET_PING_INTERVAL_MILLIS = 20_000L
    }
}
