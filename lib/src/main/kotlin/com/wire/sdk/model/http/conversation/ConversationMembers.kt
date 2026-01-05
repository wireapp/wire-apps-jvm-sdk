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

package com.wire.sdk.model.http.conversation

import com.wire.sdk.model.QualifiedId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationMembers(
    @SerialName("self")
    val self: ConversationMemberSelf,
    @SerialName("others")
    val others: List<ConversationMemberOther>
)

internal interface ConversationMemberInfo {
    val id: QualifiedId
    val conversationRole: ConversationRole
}

@Serializable
data class ConversationMemberOther(
    @SerialName("qualified_id")
    override val id: QualifiedId,
    @SerialName("conversation_role")
    override val conversationRole: ConversationRole
) : ConversationMemberInfo

@Serializable
data class ConversationMemberSelf(
    @SerialName("qualified_id")
    override val id: QualifiedId,
    @SerialName("conversation_role")
    override val conversationRole: ConversationRole
) : ConversationMemberInfo
