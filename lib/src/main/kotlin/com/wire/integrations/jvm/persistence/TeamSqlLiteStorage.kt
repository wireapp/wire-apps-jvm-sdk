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

import com.wire.integrations.jvm.AppsSdkDatabase
import com.wire.integrations.jvm.TeamQueries
import com.wire.integrations.jvm.model.ClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.TeamId
import java.util.UUID

internal class TeamSqlLiteStorage(db: AppsSdkDatabase) : TeamStorage {
    private val teamQueries: TeamQueries = db.teamQueries

    override fun add(team: Team) {
        teamQueries.insert(
            id = team.id.value.toString(),
            user_id = team.userId.id.toString(),
            domain = team.userId.domain,
            client_id = team.clientId.value
        )
    }

    override fun getAll(): List<Team> {
        return teamQueries.selectAll().executeAsList().map {
            Team(
                id = TeamId(UUID.fromString(it.id)),
                userId = QualifiedId(UUID.fromString(it.user_id), it.domain),
                clientId = ClientId(it.client_id)
            )
        }
    }
}
