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

package com.wire.integrations.jvm.model.http

import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import com.wire.integrations.jvm.model.http.message.MessageEventData
import com.wire.integrations.jvm.utils.UUIDSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import java.util.UUID

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class EventResponse(
    @Serializable
    @SerialName("id") val id: String,
    @SerialName("payload") val payload: List<EventContentDTO>?,
    @SerialName("transient") val transient: Boolean = false
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
sealed class EventContentDTO {
    @OptIn(ExperimentalSerializationApi::class)
    @JsonIgnoreUnknownKeys
    @Serializable
    @SerialName("team.invite")
    data class TeamInvite(
        @Serializable(with = UUIDSerializer::class)
        @SerialName("teamId")
        val teamId: UUID
    ) : EventContentDTO()

    @OptIn(ExperimentalSerializationApi::class)
    @JsonIgnoreUnknownKeys
    @Serializable
    sealed class Conversation : EventContentDTO() {
        @OptIn(ExperimentalSerializationApi::class)
        @JsonIgnoreUnknownKeys
        @Serializable
        @SerialName("conversation.create")
        data class NewConversationDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") val qualifiedFrom: QualifiedId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val data: ConversationResponse
        ) : Conversation()

        @OptIn(ExperimentalSerializationApi::class)
        @JsonIgnoreUnknownKeys
        @Serializable
        @SerialName("conversation.otr-message-add")
        data class NewProteusMessageDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") val qualifiedFrom: QualifiedId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val data: MessageEventData
        ) : Conversation()

        @OptIn(ExperimentalSerializationApi::class)
        @JsonIgnoreUnknownKeys
        @Serializable
        @SerialName("conversation.mls-message-add")
        data class NewMLSMessageDTO(
            @SerialName("qualified_conversation") val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") val qualifiedFrom: QualifiedId,
            @SerialName("time") val time: Instant,
            @SerialName("data") val message: String,
            @SerialName("subconv") val subconversation: String?
        ) : Conversation()
    }

    @OptIn(ExperimentalSerializationApi::class)
    @JsonIgnoreUnknownKeys
    @Serializable
    @SerialName("unknown")
    data class Unknown(val type: String) : EventContentDTO()
}
