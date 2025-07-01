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
package com.wire.integrations.jvm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventAcknowledgeRequest(
    @SerialName("type")
    val type: AcknowledgeType,
    @SerialName("data")
    val data: AcknowledgeData? = null
) {
    companion object {
        fun basicAck(deliveryTag: ULong): EventAcknowledgeRequest {
            return EventAcknowledgeRequest(
                type = AcknowledgeType.ACK,
                data = AcknowledgeData(deliveryTag, false)
            )
        }

        fun notificationMissedAck(): EventAcknowledgeRequest {
            return EventAcknowledgeRequest(
                type = AcknowledgeType.ACK_FULL_SYNC
            )
        }
    }
}

@Serializable
data class AcknowledgeData(
    @SerialName("delivery_tag")
    val deliveryTag: ULong,
    @SerialName("multiple")
    val multiple: Boolean = false
)

@Serializable
enum class AcknowledgeType {
    @SerialName("ack")
    ACK,

    @SerialName("ack_full_sync")
    ACK_FULL_SYNC;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
