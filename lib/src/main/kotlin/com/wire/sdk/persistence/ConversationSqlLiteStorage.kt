/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.sdk.persistence

import com.wire.crypto.toGroupId
import com.wire.sdk.AppsSdkDatabase
import com.wire.sdk.Conversation
import com.wire.sdk.ConversationMemberQueries
import com.wire.sdk.ConversationQueries
import com.wire.sdk.Conversation_member
import com.wire.sdk.model.ConversationData
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.conversation.ConversationRole
import java.util.Base64
import java.util.UUID

@Suppress("TooManyFunctions")
internal class ConversationSqlLiteStorage(db: AppsSdkDatabase) : ConversationStorage {
    private val conversationQueries: ConversationQueries = db.conversationQueries
    private val conversationMemberQueries: ConversationMemberQueries = db.conversationMemberQueries

    override fun save(conversation: ConversationData) {
        conversationQueries.insert(
            id = conversation.id.id.toString(),
            domain = conversation.id.domain,
            name = conversation.name,
            mls_group_id =
                Base64.getEncoder().encodeToString(conversation.mlsGroupId.copyBytes()),
            team_id = conversation.teamId?.value?.toString(),
            type = conversation.type.name
        )
    }

    /**
     * Stores the members of a conversation, if the conversation already exists in the database,
     * else does nothing (members will be stored later when fetching
     * the whole conversation from the backend).
     *
     * Currently, there is no way to have a random number of members in Sqlite parametric queries.
     * Therefore, we cannot insert all members in one go. The recommendation is to use a transaction
     */
    override fun saveMembers(
        conversationId: QualifiedId,
        members: List<ConversationMember>
    ) {
        if (members.isEmpty() || getById(conversationId) == null) return
        conversationMemberQueries.transaction {
            members.forEach {
                conversationMemberQueries.insert(
                    user_id = it.userId.id.toString(),
                    user_domain = it.userId.domain,
                    conversation_id = conversationId.id.toString(),
                    conversation_domain = conversationId.domain,
                    role = it.role.name
                )
            }
        }
    }

    override fun getAll(): List<ConversationData> =
        conversationQueries.selectAll().executeAsList().map { conversationMapper(it) }

    override fun getById(conversationId: QualifiedId): ConversationData? {
        return runCatching {
            conversationQueries
                .selectByIdAndDomain(conversationId.id.toString(), conversationId.domain)
                .executeAsOne().let { conversationMapper(it) }
        }.getOrNull()
    }

    override fun getAllMembers(): List<ConversationMember> =
        conversationMemberQueries.selectAll().executeAsList().map { conversationMemberMapper(it) }

    override fun getMembersByConversationId(conversationId: QualifiedId): List<ConversationMember> =
        conversationMemberQueries
            .selectByConversationIdAndDomain(conversationId.id.toString(), conversationId.domain)
            .executeAsList().map { conversationMemberMapper(it) }

    override fun delete(conversationId: QualifiedId) {
        conversationQueries.delete(conversationId.id.toString(), conversationId.domain)
    }

    override fun deleteMembers(
        conversationId: QualifiedId,
        users: List<QualifiedId>
    ) {
        conversationMemberQueries.transaction {
            users.forEach {
                conversationMemberQueries.delete(
                    user_id = it.id.toString(),
                    user_domain = it.domain,
                    conversation_id = conversationId.id.toString(),
                    conversation_domain = conversationId.domain
                )
            }
        }
    }

    private fun conversationMapper(conv: Conversation) =
        ConversationData(
            id = QualifiedId(UUID.fromString(conv.id), conv.domain),
            name = conv.name,
            teamId = conv.team_id?.let { TeamId(UUID.fromString(it)) },
            mlsGroupId = Base64.getDecoder().decode(conv.mls_group_id).toGroupId(),
            type = ConversationData.Type.fromString(value = conv.type)
        )

    private fun conversationMemberMapper(member: Conversation_member) =
        ConversationMember(
            userId = QualifiedId(
                id = UUID.fromString(member.user_id),
                domain = member.user_domain
            ),
            role = ConversationRole.valueOf(member.role)
        )
}
