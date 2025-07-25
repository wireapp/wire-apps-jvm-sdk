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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    private val executor = Executors.newSingleThreadExecutor()

    init {
        require(cryptographyStoragePassword.length == CRYPTOGRAPHY_STORAGE_PASSWORD_LENGTH) {
            "cryptographyStoragePassword must be exactly $CRYPTOGRAPHY_STORAGE_PASSWORD_LENGTH " +
                "characters long."
        }

        IsolatedKoinContext.start()
        IsolatedKoinContext.setApplicationId(applicationId)
        IsolatedKoinContext.setApiHost(apiHost)
        IsolatedKoinContext.setApiToken(apiToken)
        IsolatedKoinContext.setCryptographyStoragePassword(cryptographyStoragePassword)

        initDynamicModules(wireEventsHandler)
    }

    @Synchronized
    fun startListening() {
        if (running.get()) {
            logger.info("Wire Apps SDK is already running")
            return
        }
        running.set(true)

        executor.execute {
            val eventsListener = IsolatedKoinContext.koinApp.koin.get<WireTeamEventsListener>()
            logger.info("Start listening to WebSocket events...")
            // Connect and reconnect if connection closes and the listener function completes
            while (running.get()) {
                runBlocking(Dispatchers.IO) {
                    eventsListener.connect()
                }
                logger.info("Connection ended, attempting to reconnect...")
            }
        }
    }

    @Synchronized
    fun stopListening() {
        if (!running.get()) {
            logger.info("Wire Apps SDK is not running")
            return
        }
        logger.info("Wire Apps SDK shutting down")
        running.set(false)
        executor.shutdownNow()
    }

    fun isRunning(): Boolean = running.get()

    fun getApplicationManager(): WireApplicationManager = IsolatedKoinContext.koinApp.koin.get()

    private fun initDynamicModules(wireEventsHandler: WireEventsHandler) {
        val dynamicModule = module {
            single {
                wireEventsHandler
            }
        }

        IsolatedKoinContext.koinApp.koin.loadModules(listOf(dynamicModule))
    }

    private companion object {
        const val CRYPTOGRAPHY_STORAGE_PASSWORD_LENGTH = 32
    }
}
