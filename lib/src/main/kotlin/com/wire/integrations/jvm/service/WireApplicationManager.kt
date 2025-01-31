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

import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.cryptography.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.exception.runWithWireException
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.persistence.TeamStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Allows fetching and interacting with each Team instance that invited the application.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val httpClient: HttpClient,
    private val wireEventsHandler: WireEventsHandler
) {
    private val logger = LoggerFactory.getLogger(this::class.java.canonicalName)
    private val teamOpenConnections = mutableMapOf<UUID, WireTeamEventsListener>()

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
        // Create CoreCryptoCentral instance and share it
        val cryptoClient = CryptoClient(team)
        val openTeamConnection =
            WireTeamEventsListener(
                team = team,
                httpClient = httpClient,
                cryptoClient = cryptoClient,
                wireEventsHandler = wireEventsHandler
            )
        teamOpenConnections[team.id] = openTeamConnection
        openTeamConnection.connect()
    }

    fun getApplicationMetadata(): ApiVersionResponse {
        logger.info("Fetching application metadata")
        return runWithWireException {
            runBlocking { httpClient.get("/v7/api-version").body() }
        }
    }

    @Throws(WireException::class)
    fun fetchApplicationData(): AppDataResponse {
        logger.info("Fetching application data")
        return runWithWireException {
            runBlocking { httpClient.get("/v7/app-data").body() }
        }
    }
}
