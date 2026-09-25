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
import com.wire.sdk.exception.WireException
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.service.KeyPackageReplenisher
import com.wire.sdk.service.WireApplicationManager
import com.wire.sdk.service.WireTeamEventsListener
import com.wire.sdk.service.conversation.ConversationService
import com.wire.sdk.utils.ApiTokenUtils
import com.wire.sdk.utils.obfuscateId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
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
 * @param apiToken The API token for authenticating with the Wire backend
 * @param apiHost The Wire backend API host URL (e.g., "https://prod-nginz-https.wire.com")
 * @param cryptographyStorageKey A 32-byte key used to encrypt the local cryptographic storage.
 *                                   This key must be consistent across restarts.
 *                                   It is advisable to use a secure random 256 bits key.
 * @param wireEventsHandler An implementation of [WireEventsHandler] to receive and process
 *                              incoming Wire events (messages, assets, etc.)
 * @throws IllegalArgumentException if [cryptographyStorageKey] is not exactly 32 bytes
 */
class WireAppSdk(
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
        IsolatedKoinContext.setApiHost(apiHost)
        IsolatedKoinContext.setCryptographyStorageKey(cryptographyStorageKey.copyOf())

        initDynamicModules(wireEventsHandler)

        initializeCredentials(apiToken)
        // Register shutdown hook for graceful termination on SIGTERM/SIGINT
        Runtime.getRuntime().addShutdownHook(shutdownHook)
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

        if (executor.isShutdown) {
            executor = Executors.newSingleThreadExecutor()
        }

        val keyPackageReplenisher = IsolatedKoinContext.koinApp.koin.get<KeyPackageReplenisher>()
        keyPackageReplenisher.start()
        running.set(true)

        try {
            executor.execute {
                try {
                    val eventsListener =
                        IsolatedKoinContext.koinApp.koin.get<WireTeamEventsListener>()
                    logger.info("Start listening to WebSocket events...")
                    // Connect and reconnect if the connection closes and the listener function
                    // completes.
                    while (running.get()) {
                        runBlocking(Dispatchers.IO) {
                            eventsListener.connect()
                        }
                    }
                } finally {
                    keyPackageReplenisher.stop()
                    running.set(false)
                    logger.info("WebSocket listener stopped")
                }
            }
        } catch (exception: RejectedExecutionException) {
            keyPackageReplenisher.stop()
            running.set(false)
            throw exception
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
    @JvmOverloads
    fun stopListening(gracefulTimeoutMs: Long = DEFAULT_GRACEFUL_SHUTDOWN_TIMEOUT_MS) {
        if (!running.getAndSet(false)) {
            logger.info("Wire Apps SDK is not running")
            return
        }
        logger.info("Wire Apps SDK initiating graceful shutdown")
        IsolatedKoinContext.koinApp.koin.get<KeyPackageReplenisher>().stop()

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
     * Validates the startup token against the stored app before changing any credentials.
     * The backend cookie may have been refreshed, so preserve it unless it is missing or
     * a previously stored startup token has changed. This also preserves legacy cookies
     * when storing a startup token for the first time.
     */
    private fun initializeCredentials(apiToken: String) {
        val appStorage = IsolatedKoinContext.koinApp.koin.get<AppStorage>()
        val storedApiToken = appStorage.getApiToken()
        val storedBackendCookie = appStorage.getBackendCookie()
        val tokenUserId = ApiTokenUtils.extractUserId(apiToken)

        validateApiTokenForStoredApp(
            tokenUserId = tokenUserId,
            storedApiToken = storedApiToken,
            storedBackendCookie = storedBackendCookie,
            appStorage = appStorage
        )

        val startupTokenChanged = storedApiToken != null && storedApiToken != apiToken

        if (storedApiToken != apiToken) {
            appStorage.saveApiToken(apiToken)
            logger.info("Stored startup API token. apiToken:${apiToken.obfuscateId()}")
        }

        if (storedBackendCookie == null || startupTokenChanged) {
            appStorage.saveBackendCookie(apiToken)
            logger.info("Stored startup API token as backend cookie.")
        } else {
            logger.info("Preserving existing backend cookie.")
        }
    }

    private fun validateApiTokenForStoredApp(
        tokenUserId: UUID,
        storedApiToken: String?,
        storedBackendCookie: String?,
        appStorage: AppStorage
    ) {
        // The persisted app ID takes precedence over either stored credential.
        if (appStorage.hasApplicationQualifiedId()) {
            val storedApplicationQualifiedId = appStorage.getApplicationQualifiedId()
            if (!storedApplicationQualifiedId.hasSameUserId(tokenUserId)) {
                throw WireException.InvalidParameter(
                    """
                        Stored application QualifiedId $storedApplicationQualifiedId does not match App QualifiedId ${tokenUserId.obfuscateId()} retrieved from the API token. Clear SDK storage before using a token for another app.
                    """.trimIndent()
                )
            }
        } else if (storedApiToken != null || storedBackendCookie != null) {
            // Older storage may only identify the app through its credentials.
            val storedUserId = ApiTokenUtils.extractUserId(
                tokens = listOfNotNull(storedBackendCookie, storedApiToken),
                errorMessage = "Stored credentials don't contain a valid userId."
            )
            if (storedUserId != tokenUserId) {
                throw WireException.InvalidParameter(
                    "Received API token userId does not match stored App userId. " +
                        "Clear SDK storage before using a token for another app."
                )
            }
        } else {
            // Fresh storage has no app identity to compare against.
            return
        }

        logger.info("Received API token userId matches stored App userId.")
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
