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

package com.wire.sdk.model.http

import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.ConversationRoleChange
import com.wire.sdk.model.http.conversation.MemberJoinEventData
import com.wire.sdk.model.http.conversation.MemberLeaveEventData
import com.wire.sdk.model.http.conversation.TypingEventData
import com.wire.sdk.utils.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlin.time.Instant

@Serializable
data class EventResponse(
    @SerialName("id") val id: String,
    @SerialName("payload") val payload: List<EventContentDTO>?,
    @SerialName("transient") val transient: Boolean = false
)

@Serializable
sealed class EventContentDTO {
    @Serializable
    @SerialName("team.invite")
    data class TeamInvite(
        @Serializable(with = UUIDSerializer::class)
        @SerialName("teamId")
        val teamId: UUID
    ) : EventContentDTO()

    @Serializable
    sealed class Conversation : EventContentDTO() {
        abstract val qualifiedConversation: QualifiedId
        abstract val qualifiedFrom: QualifiedId
        abstract val time: Instant
        abstract val data: Any?

        @Serializable
        @SerialName("conversation.create")
        data class NewConversationDTO(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: ConversationResponse
        ) : Conversation()

        @Serializable
        @SerialName("conversation.delete")
        data class DeleteConversation(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: String?
        ) : Conversation()

        @Serializable
        @SerialName("conversation.mls-message-add")
        data class NewMLSMessageDTO(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: String,
            @SerialName("subconv") val subconversation: String?
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-join")
        data class MemberJoin(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: MemberJoinEventData
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-update")
        data class MemberUpdateDTO(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: ConversationRoleChange
        ) : Conversation()

        @Serializable
        @SerialName("conversation.member-leave")
        data class MemberLeave(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: MemberLeaveEventData
        ) : Conversation()

        @Serializable
        @SerialName("conversation.mls-welcome")
        data class MlsWelcome(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: String
        ) : Conversation()

        @Serializable
        @SerialName("conversation.typing")
        data class Typing(
            @SerialName("qualified_conversation") override val qualifiedConversation: QualifiedId,
            @SerialName("qualified_from") override val qualifiedFrom: QualifiedId,
            @SerialName("time") override val time: Instant,
            @SerialName("data") override val data: TypingEventData
        ) : Conversation()
    }

    @Serializable
    @SerialName("unknown")
    data class Unknown(val type: String) : EventContentDTO()
}
