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
 * Represents the connection status to the Wire backend.
 *
 * Use this enum to determine the current state of the SDK's connection
 * and react accordingly in your application.
 */
enum class BackendConnectionStatus {
    /**
     * The SDK is successfully connected to the Wire backend.
     * Events are being received and API calls can be made.
     */
    CONNECTED,

    /**
     * The SDK is disconnected from the Wire backend.
     * This may happen due to network issues, server errors, or when the SDK is stopped.
     * The SDK will attempt to reconnect automatically.
     */
    DISCONNECTED
}
