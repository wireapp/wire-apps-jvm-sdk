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

package com.wire.integrations.jvm.persistence

import com.wire.crypto.MLSGroupId
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId

interface ConversationStorage {
    /**
     * Save a conversation with its teamId and mlsGroupId.
     */
    fun save(
        conversationId: QualifiedId,
        mlsGroupId: MLSGroupId,
        teamId: TeamId?
    )

    /**
     * Conversations can be created partially via 2 different events, member-join and mls-welcome.
     * Only when both events are received, the conversation is considered fully created for MLS.
     *
     * This function is called when a mls-welcome event is received.
     */
    fun saveWithMlsGroupId(
        conversationId: QualifiedId,
        mlsGroupId: MLSGroupId
    )

    fun getAll(): List<ConversationData>

    /**
     * Get conversation by its ID. To be able to send messages to a conversation,
     * it must be fully created, meaning both the teamId and mlsGroupId must be present.
     */
    fun getById(conversationId: QualifiedId): ConversationData?
}
