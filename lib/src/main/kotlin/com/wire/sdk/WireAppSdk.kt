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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main entry point for the Wire Apps SDK.
 *
 * This class provides the primary interface for Wire third-party applications to connect
 * to the Wire backend, handle real-time events via WebSocket, and interact with the
 * Wire messaging platform.
 *
 * Example usage:
 * ```kotlin
 * val sdk = WireAppSdk(
 *     applicationId = UUID.fromString("your-app-id"),
 *     apiToken = "your-api-token",
 *     apiHost = "https://prod-nginz-https.wire.com",
 *     cryptographyStorageKey = yourSecureKey, // 32 bytes
 *     wireEventsHandler = MyEventsHandler()
 * )
 *
 * sdk.startListening() // Start receiving events
 * // ... your application logic ...
 * sdk.stopListening() // Stop when done
 * ```
 *
 * The SDK handles:
 * - MLS (Messaging Layer Security) and Proteus encryption/decryption
 * - WebSocket connections for real-time event streaming
 * - HTTP client calls to the Wire backend API
 * - Local storage for conversation and team data
 *
 * @property applicationId The unique identifier for your Wire application
 * @property apiToken The API token for authenticating with the Wire backend
 * @property apiHost The Wire backend API host URL (e.g., "https://prod-nginz-https.wire.com")
 * @property cryptographyStorageKey A 32-byte key used to encrypt the local cryptographic storage.
 *                                   This key must be consistent across restarts.
 *                                   It is advisable to use a secure random 256 bits key.
 * @property wireEventsHandler An implementation of [WireEventsHandler] to receive and process
 *                              incoming Wire events (messages, assets, etc.)
 * @throws IllegalArgumentException if [cryptographyStorageKey] is not exactly 32 bytes
 */
class WireAppSdk(
    applicationId: UUID,
    apiToken: String,
    apiHost: String,
    cryptographyStorageKey: ByteArray,
    wireEventsHandler: WireEventsHandler
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val running = AtomicBoolean(false)
    private var executor = Executors.newSingleThreadExecutor()
    private var shutdownHook = Thread {
        logger.info("Shutdown hook triggered")
        if (isRunning()) stopListening()
    }

    init {
        require(cryptographyStorageKey.size == CRYPTOGRAPHY_STORAGE_KEY_BYTES) {
            "cryptographyStorageKey must be exactly $CRYPTOGRAPHY_STORAGE_KEY_BYTES bytes long"
        }

        initializeStorageDirectory()

        IsolatedKoinContext.start()
        IsolatedKoinContext.setApplicationId(applicationId)
        IsolatedKoinContext.setApiHost(apiHost)
        IsolatedKoinContext.setApiToken(apiToken)
        IsolatedKoinContext.setCryptographyStorageKey(cryptographyStorageKey.copyOf())

        initDynamicModules(wireEventsHandler)

        // Register shutdown hook for graceful termination on SIGTERM/SIGINT
        Runtime.getRuntime().addShutdownHook(shutdownHook)
    }

    private fun initializeStorageDirectory() {
        val storageDirectory = File("storage")

        if (!storageDirectory.exists()) {
            logger.info("Creating storage root directory at: ${storageDirectory.absolutePath}")
            val created = storageDirectory.mkdirs()
            check(created) {
                "Failed to create storage directory: ${storageDirectory.absolutePath}"
            }
        } else {
            logger.info("Storage directory already exists: ${storageDirectory.absolutePath}")
        }
    }

    /**
     * Launches a separate process that listens for WebSocket events from the Wire backend,
     * and stays open indefinitely until [stopListening] is called or the application is terminated.
     *
     * The events received will be handled automatically and routed
     * to the provided [WireEventsHandler].
     *
     * startListening is mandatory to have the SDK functioning properly, but without it some data
     * can still be accessed via the [WireApplicationManager].
     * This method is thread-safe and can be called multiple times;
     */
    @Synchronized
    fun startListening() {
        if (running.get()) {
            logger.info("Wire Apps SDK is already running")
            return
        }
        running.set(true)

        // Recreate executor if it was previously shut down
        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }

        executor.execute {
            try {
                val eventsListener = IsolatedKoinContext.koinApp.koin.get<WireTeamEventsListener>()
                logger.info("Start listening to WebSocket events...")
                // Connect and reconnect if connection closes and the listener function completes
                while (running.get()) {
                    runBlocking(Dispatchers.IO) {
                        eventsListener.connect()
                    }
                }
            } finally {
                running.set(false)
                logger.info("WebSocket listener stopped")
            }
        }

        // After webSocket is started, check if there are broken conversations to rejoin
        runBlocking {
            val conversationService = IsolatedKoinContext.koinApp.koin.get<ConversationService>()
            conversationService.establishOrRejoinConversations()
        }
    }

    /**
     * Stops listening to WebSocket events from the Wire backend.
     *
     * This method gracefully shuts down the WebSocket connection and stops the background
     * event processing thread. It first closes the WebSocket connection to stop receiving
     * new events, then waits for any in-flight event processing to complete before
     * fully shutting down.
     *
     * It is safe to call this method multiple times; subsequent calls while not running
     * will have no effect.
     *
     * After calling this method, [startListening] can be called again to resume
     * event processing.
     *
     * This method is thread-safe and synchronized.
     *
     * @param gracefulTimeoutMs Maximum time in milliseconds to wait for in-flight events
     *                          to complete before forcing shutdown. Defaults to 20 seconds.
     */
    @Synchronized
    fun stopListening(gracefulTimeoutMs: Long = DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MS) {
        if (!running.get()) {
            logger.info("Wire Apps SDK is not running")
            return
        }
        logger.info("Wire Apps SDK initiating graceful shutdown")
        running.set(false)

        // Close WebSocket gracefully to stop receiving new events
        runBlocking {
            val eventsListener = IsolatedKoinContext.koinApp.koin.get<WireTeamEventsListener>()
            eventsListener.requestShutdown()
        }

        // Wait for in-flight event processing to complete
        executor.shutdown()
        if (!executor.awaitTermination(gracefulTimeoutMs, TimeUnit.MILLISECONDS)) {
            logger.warn("Graceful shutdown timed out, forcing stop")
            executor.shutdownNow()
        }
        logger.info("Wire Apps SDK shutdown complete")
    }

    /**
     * Returns whether the SDK is currently listening for WebSocket events.
     *
     * @return `true` if [startListening] has been called and the SDK is actively
     *         listening for events, `false` otherwise
     */
    fun isRunning(): Boolean = running.get()

    /**
     * Returns the [WireApplicationManager] instance for interacting with the Wire backend.
     *
     * The application manager provides methods for:
     * - Sending text messages and assets
     * - Downloading and uploading files
     * - Creating and managing conversations
     * - Accessing team information
     *
     * This method can be called at any time after SDK initialization, even before
     * [startListening] is called. However, some operations may require an active
     * WebSocket connection.
     *
     * @return The [WireApplicationManager] instance configured for this SDK
     * @see WireApplicationManager
     */
    fun getApplicationManager(): WireApplicationManager = IsolatedKoinContext.koinApp.koin.get()

    /**
     * Sets or updates the backend connection listener that will receive notifications about
     * websocket state changes.
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
        const val CRYPTOGRAPHY_STORAGE_KEY_BYTES = 32
        const val DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MS = 20_000L
    }
}
