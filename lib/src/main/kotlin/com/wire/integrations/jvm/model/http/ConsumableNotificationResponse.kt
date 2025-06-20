/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.integrations.jvm.model.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalSerializationApi::class)
@Serializable
sealed class ConsumableNotificationResponse {
    @Serializable
    @SerialName("event")
    data class EventNotification(
        @SerialName("data") val data: EventDataDTO
    ) : ConsumableNotificationResponse()

    @Serializable
    @SerialName("message_count")
    data class MessageCount(
        @SerialName("data") val data: NotificationCount
    ) : ConsumableNotificationResponse()

    @Serializable
    @SerialName("notifications_missed")
    data object MissedNotification : ConsumableNotificationResponse()
}

@Serializable
data class EventDataDTO(
    @SerialName("delivery_tag")
    val deliveryTag: ULong,
    @SerialName("event")
    val event: EventResponse
)

@Serializable
data class NotificationCount(
    @SerialName("count")
    val count: ULong
)
