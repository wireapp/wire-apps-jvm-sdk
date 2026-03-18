/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.persistence.AppStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal class NotificationsApiClient(
    private val httpClient: HttpClient,
    private val appStorage: AppStorage
) {
    private companion object {
        // TODO: This is used in different classes as well. Consider moving it to a common place.
        const val CLIENT_QUERY_KEY = "client"
    }

    suspend fun getLastNotification(): EventResponse {
        val lastNotification = httpClient.get("notifications/last") {
            appStorage.getDeviceId()?.let { parameter(CLIENT_QUERY_KEY, it) }
        }.body<EventResponse>()

        return lastNotification
    }
}
