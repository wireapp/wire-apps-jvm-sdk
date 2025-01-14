package com.wire.integrations.jvm.service

import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.persistence.TeamStorage

/**
 * Allows fetching and interacting with each Team instance that invited the application.
 */
class WireTeamManager internal constructor(private val teamStorage: TeamStorage) {
    fun getTeams(): List<Team> {
        return teamStorage.getAll()
    }
}
