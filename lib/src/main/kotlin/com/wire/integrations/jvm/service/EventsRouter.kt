/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
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
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.persistence.TeamStorage
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

internal class EventsRouter internal constructor(
    private val teamStorage: TeamStorage,
    private val backendClient: BackendClient,
    private val wireEventsHandler: WireEventsHandler
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    val teamClients = mutableMapOf<TeamId, CryptoClient>()

    fun routeEvents(event: EventResponse) {
        event.payload?.forEach { eventContentDTO ->
            when (eventContentDTO) {
                is EventContentDTO.TeamInvite -> {
                    logger.debug("Team invite: ${eventContentDTO.teamId}")
                    newTeamInvite(TeamId(eventContentDTO.teamId))
                }
                is EventContentDTO.Conversation.NewConversationDTO -> {
                    // Check if there are enough Proteus keys or start MLS join procedure
                    wireEventsHandler.onNewConversation(eventContentDTO.time.toString())
                }
                is EventContentDTO.Conversation.NewProteusMessageDTO -> {
                    // Decrypt Proteus with cryptoClient from teamClients
                    // TODO are the TeamId or UserId present in the event?
                    wireEventsHandler.onNewMessage(eventContentDTO.time.toString())
                }
                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    // Decrypt MLS with cryptoClient from teamClients
                    // TODO are the TeamId or UserId present in the event?
                    wireEventsHandler.onNewMLSMessage(eventContentDTO.time.toString())
                }
                is EventContentDTO.Unknown -> {
                    logger.warn("Unknown event type: {}", eventContentDTO)
                }
            }
        }
    }

    private fun newTeamInvite(teamId: TeamId) {
        try {
            val userId: QualifiedId = backendClient.confirmTeam(teamId).userId
            val mlsDefaultCiphersuite = backendClient.getApplicationFeatures(teamId)
                .mlsFeatureResponse.mlsFeatureConfigResponse.defaultCipherSuite
            val newTeam: Team = createTeamWithCryptoMaterial(teamId, userId, mlsDefaultCiphersuite)
            teamStorage.add(newTeam) // Can be done async ?

            openCryptoClient(newTeam, mlsDefaultCiphersuite)
        } catch (e: ResponseException) {
            logger.error("Error fetching events from the backend", e)
        } catch (e: CoreCryptoException) {
            logger.error("Error while creating crypto material", e)
        } catch (e: WireException) {
            logger.error("Internal error", e)
        }
    }

    private fun createTeamWithCryptoMaterial(
        teamId: TeamId,
        userId: QualifiedId,
        mlsCipherSuiteCode: Int
    ): Team {
        return runBlocking {
            logger.info("Registering client with Proteus for team $teamId")
            val prekeys = CryptoClient.generateFirstPrekeys(teamId)
            val clientId = backendClient.registerClientWithProteus(
                teamId = teamId,
                prekeys = prekeys.keys,
                lastPreKey = prekeys.lastKey
            )
            logger.info("Client registered team $teamId with clientId $clientId")
            val team = Team(teamId, userId, clientId)

            CryptoClient(team, mlsCipherSuiteCode).use {
                backendClient.updateClientWithMlsPublicKey(teamId, clientId, it.mlsGetPublicKey())
                backendClient.uploadMlsKeyPackages(teamId, clientId, it.mlsGenerateKeyPackages())
                logger.info("MLS client for $clientId fully initialized")
            }
            team
        }
    }

    /**
     * Get all teams previously saved in the local database, open a CryptoClient for each of them.
     */
    fun openCurrentTeamClients() {
        teamStorage.getAll().forEach { team ->
            openCryptoClient(
                team = team,
                mlsCipherSuiteCode = backendClient.getApplicationFeatures(team.id)
                    .mlsFeatureResponse.mlsFeatureConfigResponse.defaultCipherSuite
            )
        }
    }

    /**
     * Opens a CryptoClient for a specific Team, then keeps them in a Map.
     *
     * This function should be called when the application is started and the Team is already stored, and
     * when a Team invite is accepted.
     *
     * @param team The Team opening the client.
     * @param mlsCipherSuiteCode The MLS ciphersuite enabled on the backend
     */
    private fun openCryptoClient(
        team: Team,
        mlsCipherSuiteCode: Int
    ) {
        val cryptoClient = CryptoClient(team, mlsCipherSuiteCode)
        teamClients[team.id] = cryptoClient
    }
}
