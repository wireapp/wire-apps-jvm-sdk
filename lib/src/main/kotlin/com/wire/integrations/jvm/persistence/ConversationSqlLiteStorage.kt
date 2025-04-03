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
import com.wire.integrations.jvm.AppsSdkDatabase
import com.wire.integrations.jvm.Conversation
import com.wire.integrations.jvm.ConversationQueries
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import java.util.Base64
import java.util.UUID

internal class ConversationSqlLiteStorage(db: AppsSdkDatabase) : ConversationStorage {
    private val conversationQueries: ConversationQueries = db.conversationQueries

    override fun save(
        conversationId: QualifiedId,
        mlsGroupId: MLSGroupId,
        teamId: TeamId?
    ) {
        conversationQueries.insert(
            id = conversationId.id.toString(),
            domain = conversationId.domain,
            mls_group_id = Base64.getEncoder().encodeToString(mlsGroupId.value),
            team_id = teamId?.value.toString()
        )
    }

    override fun saveOnlyMlsGroupId(
        conversationId: QualifiedId,
        mlsGroupId: MLSGroupId
    ) {
        conversationQueries.insertWithMlsGroupId(
            id = conversationId.id.toString(),
            domain = conversationId.domain,
            mls_group_id = Base64.getEncoder().encodeToString(mlsGroupId.value)
        )
    }

    override fun saveOnlyTeamId(
        conversationId: QualifiedId,
        teamId: TeamId
    ) {
        conversationQueries.insertWithTeamId(
            id = conversationId.id.toString(),
            domain = conversationId.domain,
            team_id = teamId.value.toString()
        )
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

    private fun conversationMapper(conv: Conversation) =
        ConversationData(
            id = QualifiedId(UUID.fromString(conv.id), conv.domain),
            teamId = conv.team_id?.let {
                if (it == "" || it == "null") {
                    null
                } else {
                    TeamId(UUID.fromString(it))
                }
            },
            mlsGroupId = conv.mls_group_id?.let { MLSGroupId(Base64.getDecoder().decode(it)) }
        )
}
