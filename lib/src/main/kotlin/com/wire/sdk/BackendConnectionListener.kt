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

/**
 * Listener interface for receiving notifications about backend connection state changes.
 *
 * Implement this interface and register it with [WireAppSdk.setBackendConnectionListener]
 * to receive callbacks when the connection to the Wire backend is established or lost.
 *
 * Example usage:
 * ```kotlin
 * val listener = object : BackendConnectionListener {
 *     override fun onConnected() {
 *         println("Connected to Wire backend")
 *     }
 *
 *     override fun onDisconnected() {
 *         println("Disconnected from Wire backend")
 *     }
 * }
 * wireAppSdk.setBackendConnectionListener(listener)
 * ```
 */
interface BackendConnectionListener {
    /**
     * Called when the SDK successfully establishes a connection to the Wire backend.
     *
     * This callback is invoked when the WebSocket connection is successfully opened
     * and the SDK is ready to receive events and process API calls.
     */
    fun onConnected()

    /**
     * Called when the SDK loses connection to the Wire backend.
     *
     * This callback is invoked when:
     * - The WebSocket connection is closed due to network issues or Wire servers return 5xx.
     *     This includes automatic retries to re-establish the connection
     * - The SDK is stopped via [WireAppSdk.stopListening]
     *
     * The SDK will automatically attempt to reconnect unless it has been stopped.
     */
    fun onDisconnected()
}
