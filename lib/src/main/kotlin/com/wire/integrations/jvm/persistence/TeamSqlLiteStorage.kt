package com.wire.integrations.jvm.persistence

import com.wire.integrations.jvm.model.Team

internal class TeamSqlLiteStorage : TeamStorage {
    override fun getAll(): List<Team> {
        // Will probably need to use a SQLite database or similar"
        return listOf(Team("team1"))
    }
}
