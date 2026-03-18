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

package com.wire.sdk.client

import com.wire.sdk.model.http.ApiVersionResponse
import com.wire.sdk.model.http.FeaturesResponse
import com.wire.sdk.model.http.user.SelfUserResponse
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession

interface BackendClient {
    suspend fun connectWebSocket(handleFrames: suspend (DefaultClientWebSocketSession) -> Unit)

    /**
     * Gracefully closes the active WebSocket connection if one exists.
     * This stops receiving new frames while allowing in-flight processing to complete.
     */
    suspend fun closeWebSocket()

    suspend fun getAvailableApiVersions(): ApiVersionResponse

    suspend fun getApplicationFeatures(): FeaturesResponse

    suspend fun getSelfUser(): SelfUserResponse

    companion object {
        const val API_VERSION = "v15"
        const val CLIENT_QUERY_KEY = "client"
    }
}
