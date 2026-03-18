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

import com.wire.sdk.client.BackendClient.Companion.CLIENT_QUERY_KEY
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.http.EventResponse
import com.wire.sdk.model.http.NotificationsResponse
import com.wire.sdk.persistence.AppStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import org.slf4j.LoggerFactory
import kotlin.time.Clock

internal class NotificationsApiClient(
    private val httpClient: HttpClient,
    private val appStorage: AppStorage
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val SIZE_QUERY_KEY = "size"
        const val SINCE_QUERY_KEY = "since"

        /**
         * The backend doesn't allow queries smaller than a minimum value.
         */
        const val NOTIFICATION_MINIMUM_QUERY_SIZE = 100
    }

    suspend fun getLastNotification(): EventResponse {
        val lastNotification = httpClient.get("notifications/last") {
            appStorage.getDeviceId()?.let { parameter(CLIENT_QUERY_KEY, it) }
        }.body<EventResponse>()

        return lastNotification
    }

    suspend fun getPaginatedNotifications(
        querySize: Int = NOTIFICATION_MINIMUM_QUERY_SIZE,
        querySince: String?
    ): NotificationsResponse {
        try {
            val notifications = httpClient.get("notifications") {
                parameter(SIZE_QUERY_KEY, querySize)
                appStorage.getDeviceId()?.let { parameter(CLIENT_QUERY_KEY, it) }
                querySince?.let { parameter(SINCE_QUERY_KEY, it) }
            }.body<NotificationsResponse>()
            return notifications
        } catch (exception: WireException.ClientError) {
            logger.warn("Notifications not found.", exception)
            return NotificationsResponse(
                hasMore = false,
                events = emptyList(),
                time = Clock.System.now()
            )
        }
    }
}
