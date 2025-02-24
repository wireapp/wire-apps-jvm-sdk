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

package com.wire.integrations.jvm.service

import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.cryptography.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.persistence.TeamStorage
import io.ktor.client.HttpClient
import kotlin.collections.set

/**
 * Allows fetching and interacting with each Team instance that invited the application.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val httpClient: HttpClient,
    private val backendClient: BackendClient,
    private val eventsRouter: EventsRouter
) {
    // TODO this can become instead a Map of CryptoClient instances
    private val teamOpenConnections = mutableMapOf<TeamId, WireTeamEventsListener>()

    fun getStoredTeams(): List<Team> = teamStorage.getAll()

    fun getConnectedTeamsCount(): Int = teamOpenConnections.count()

    /**
     * Opens a connection fetching events for a specific Team.
     *
     * This function should be called when the application is started and the Team is already stored, and
     * when a Team invite is accepted.
     *
     * @param team The Team to connect to.
     */
    fun connectToTeam(team: Team) {
        // TODO store the Cypersuite in SQLite when any team creates a client
        val cryptoClient = CryptoClient(team)
        val openTeamConnection =
            WireTeamEventsListener(
                httpClient = httpClient,
                cryptoClient = cryptoClient,
                eventsRouter = eventsRouter
            )
        teamOpenConnections[team.id] = openTeamConnection
        openTeamConnection.connect()
    }

    fun getApplicationMetadata(): ApiVersionResponse = backendClient.getBackendVersion()

    @Throws(WireException::class)
    fun fetchApplicationData(): AppDataResponse = backendClient.getApplicationData()
}
