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

package com.wire.sdk.model.http.conversation

import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateConversationRequest private constructor(
    @SerialName("qualified_users")
    val qualifiedUsers: List<QualifiedId> = emptyList(),
    @SerialName("name")
    val name: String?,
    @SerialName("access")
    val access: List<ConversationAccess> = DEFAULT_ACCESS_LIST,
    @SerialName("access_role")
    val accessRole: List<ConversationAccessRole> = DEFAULT_ACCESS_ROLE_LIST,
    @SerialName("group_conv_type")
    val groupConversationType: GroupConversationType?,
    @SerialName("add_permission")
    val channelAddPermissionTypeDTO: ChannelAddPermissionType = ChannelAddPermissionType.ADMINS,
    @SerialName("team")
    val conversationTeamInfo: ConversationTeamInfo? = null,
    @SerialName("message_timer")
    val messageTimer: Long? = null,
    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode = ReceiptMode.DISABLED,
    @SerialName("conversation_role")
    val conversationRole: String = DEFAULT_MEMBER_ROLE,
    @SerialName("protocol")
    val protocol: CryptoProtocol = CryptoProtocol.MLS,
    @SerialName("cells")
    val cellEnabled: Boolean = false,
    @SerialName("skip_creator")
    val skipCreator: Boolean = false
) {
    companion object {
        const val DEFAULT_MEMBER_ROLE = "wire_member"
        val DEFAULT_ACCESS_LIST = listOf(
            ConversationAccess.INVITE,
            ConversationAccess.CODE
        )
        val DEFAULT_ACCESS_ROLE_LIST = listOf(
            ConversationAccessRole.GUEST,
            ConversationAccessRole.NON_TEAM_MEMBER,
            ConversationAccessRole.TEAM_MEMBER,
            ConversationAccessRole.SERVICE
        )

        fun createGroup(
            name: String,
            teamId: TeamId
        ): CreateConversationRequest =
            CreateConversationRequest(
                name = name,
                groupConversationType = GroupConversationType.REGULAR_GROUP,
                conversationTeamInfo = ConversationTeamInfo(
                    managed = false,
                    teamId = teamId.value.toString()
                )
            )

        fun createChannel(
            name: String,
            teamId: TeamId
        ): CreateConversationRequest =
            CreateConversationRequest(
                name = name,
                groupConversationType = GroupConversationType.CHANNEL,
                conversationTeamInfo = ConversationTeamInfo(
                    managed = false,
                    teamId = teamId.value.toString()
                )
            )
    }
}
