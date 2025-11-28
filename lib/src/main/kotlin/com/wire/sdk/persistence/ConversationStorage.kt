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

import com.wire.sdk.model.ConversationData
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId

interface ConversationStorage {
    /**
     * Save (UPSERT) a conversation with its teamId and mlsGroupId.
     * The mlsGroupId might come from a Rest API call or from a local MLS group creation (welcome)
     */
    fun save(conversation: ConversationData)

    /**
     * Save (UPSERT) all the members of a conversation.
     */
    fun saveMembers(
        conversationId: QualifiedId,
        members: List<ConversationMember>
    )

    fun updateMember(
        conversationId: QualifiedId,
        conversationMember: ConversationMember
    )

    fun getAll(): List<ConversationData>

    fun getAllMembers(): List<ConversationMember>

    /**
     * Get conversation by its ID. To be able to send messages to a conversation,
     * it must be fully created, meaning both the teamId and mlsGroupId must be present.
     */
    fun getById(conversationId: QualifiedId): ConversationData?

    /**
     * Get all the members of a conversation by its ID.
     */
    fun getMembersByConversationId(conversationId: QualifiedId): List<ConversationMember>

    fun delete(conversationId: QualifiedId)

    fun deleteMembers(
        conversationId: QualifiedId,
        users: List<QualifiedId>
    )
}
