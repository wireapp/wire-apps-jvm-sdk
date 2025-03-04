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
import com.wire.crypto.MlsTransport
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
    private val mlsTransport: MlsTransport,
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
                    // TODO get Proteus clientId from event.convId -> user -> team in SqlLite
                    wireEventsHandler.onNewMessage(eventContentDTO.time.toString())
                }
                is EventContentDTO.Conversation.NewMLSMessageDTO -> {
                    // Decrypt MLS with cryptoClient from teamClients
                    // TODO get MLS clientId from event.convId -> user -> team in SqlLite, and groupId from conv table
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
                prekeys = prekeys.keys,
                lastPreKey = prekeys.lastKey
            )
            logger.info("Client registered team $teamId with clientId $clientId")
            val team = Team(teamId, userId, clientId)

            val teamCryptoClient = CryptoClient(team, mlsCipherSuiteCode, mlsTransport)
            teamClients[team.id] = teamCryptoClient

            backendClient.updateClientWithMlsPublicKey(
                clientId = clientId,
                mlsPublicKey = teamCryptoClient.mlsGetPublicKey()
            )
            backendClient.uploadMlsKeyPackages(
                clientId = clientId,
                mlsKeyPackages = teamCryptoClient.mlsGenerateKeyPackages().map { it.value }
            )
            logger.info("MLS client for $clientId fully initialized")

            team
        }
    }

    /**
     * Get all teams previously saved in the local database, open a CryptoClient for each of them,
     * keep them open and store them in a Map.
     *
     * This function should be called when the application is started.
     */
    fun openCurrentTeamClients() {
        teamStorage.getAll().forEach { team ->
            val mlsCipherSuiteCode = backendClient.getApplicationFeatures(team.id)
                .mlsFeatureResponse.mlsFeatureConfigResponse.defaultCipherSuite

            val cryptoClient = CryptoClient(team, mlsCipherSuiteCode, mlsTransport)
            teamClients[team.id] = cryptoClient
        }
    }
}
