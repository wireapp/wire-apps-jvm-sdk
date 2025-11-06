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

package com.wire.sdk

import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.service.WireApplicationManager
import com.wire.sdk.service.WireTeamEventsListener
import com.wire.sdk.service.conversation.ConversationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.util.UUID
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
    private var listenerJob: Job? = null

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

        // Asynchronously start listening to events
        listenerJob = CoroutineScope(Dispatchers.IO).launch {
            val eventsListener = IsolatedKoinContext.koinApp.koin.get<WireTeamEventsListener>()
            logger.info("Start listening to WebSocket events...")
            // Connect and reconnect if connection closes and the listener function completes
            while (isActive && running.get()) {
                eventsListener.connect()
                if (isActive && running.get()) {
                    logger.info("Connection ended, attempting to reconnect...")
                }
            }
        }

        // Block thread until conversations are re-established
        runBlocking {
            val conversationService = IsolatedKoinContext.koinApp.koin.get<ConversationService>()
            conversationService.establishOrRejoinConversations()
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
        listenerJob?.cancel()
    }

    fun isRunning(): Boolean = running.get()

    fun getApplicationManager(): WireApplicationManager = IsolatedKoinContext.koinApp.koin.get()

    /**
     * Sets or updates the backend connection listener that will receive notifications about
     * connection state changes.
     *
     * This method can be called at any time, even after [startListening] has been called.
     * The new listener will immediately start receiving connection state notifications.
     *
     * @param listener The listener to receive connection state notifications, or null to remove
     *                 the current listener
     */
    fun setBackendConnectionListener(listener: BackendConnectionListener?) {
        val eventsListener = IsolatedKoinContext.koinApp.koin.get<WireTeamEventsListener>()
        eventsListener.setBackendConnectionListener(listener)
    }

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
