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

import com.wire.crypto.CoreCryptoException
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.cryptography.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.http.TeamServerSentEvent
import com.wire.integrations.jvm.persistence.TeamStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.sse.sse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Listens for SSE about new Team invites and sets up the webSocket for the team.
 */
internal class WireTeamRegistrator internal constructor(
    private val teamStorage: TeamStorage,
    private val httpClient: HttpClient,
    private val backendClient: BackendClient,
    private val wireApplicationManager: WireApplicationManager
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun connect() {
        runBlocking {
            logger.info("Connecting to the SSE endpoint, waiting for new team invites")
            httpClient.sse(host = "localhost", port = 8086, path = "/apps/teams/await") {
                while (true) {
                    incoming.collect { event ->
                        logger.info("Event from server: $event")
                        val eventData = event.data
                            ?: throw WireException.ClientError("No data in team invite event")
                        val obj = Json.decodeFromString<TeamServerSentEvent>(eventData)

                        newTeamInvite(UUID.fromString(obj.teamId))
                    }
                }
            }
        }
    }

    private fun newTeamInvite(teamId: UUID) {
        try {
            val userId: QualifiedId = backendClient.confirmTeam(teamId).userId
            val newTeam: Team = createTeamWithCryptoMaterial(teamId, userId)
            teamStorage.add(newTeam) // Can be done async ?

            wireApplicationManager.connectToTeam(newTeam)
        } catch (e: ResponseException) {
            logger.error("Error fetching events from the backend", e)
        } catch (e: CoreCryptoException) {
            logger.error("Error while creating crypto material", e)
        }
    }

    private fun createTeamWithCryptoMaterial(
        teamId: UUID,
        userId: QualifiedId
    ): Team {
        return runBlocking {
            val prekeys = CryptoClient.generateFirstPrekeys(teamId)
            val clientId = backendClient.registerClientWithProteus(
                teamId = teamId,
                prekeys = prekeys.keys,
                lastPreKey = prekeys.lastKey
            )
            val team = Team(teamId, userId, clientId)

            val mlsFeature = backendClient.getApplicationFeatures(teamId).mlsFeatureResponse
            CryptoClient(team, mlsFeature.mlsFeatureConfigResponse.defaultCipherSuite).use {
                backendClient.updateClientWithMlsPublicKey(teamId, clientId, it.mlsGetPublicKey())
                backendClient.uploadMlsKeyPackages(teamId, clientId, it.mlsGenerateKeyPackages())
            }
            team
        }
    }
}
