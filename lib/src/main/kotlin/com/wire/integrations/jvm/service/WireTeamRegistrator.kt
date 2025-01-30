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

import com.wire.integrations.jvm.cryptography.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.persistence.TeamStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Listens for SSE about new Team invites and sets up the webSocket for the team.
 */
internal class WireTeamRegistrator internal constructor(
    private val teamStorage: TeamStorage,
    private val httpClient: HttpClient,
    private val wireApplicationManager: WireApplicationManager
) {
    private val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    init {
        runBlocking {
            httpClient.sse(path = "/apps/teams/await") {
                while (true) {
                    incoming.collect { event ->
                        logger.info("Event from server:")
                        logger.info(event.toString())

                        newTeamInvite(UUID.fromString(event.data))
                    }
                }
            }
        }
    }

    private fun newTeamInvite(teamId: UUID) {
        val newTeam: Team = confirmTeamInvite(teamId)
        teamStorage.add(newTeam) // Can be done async ?
        // Init Proteus + MLS (keys, keyPackages) and upload them to the Backend
        initCryptoMaterial(newTeam)
        wireApplicationManager.connectToTeam(newTeam)
    }

    private fun confirmTeamInvite(teamId: UUID): Team {
        // TODO Make a call with Ktor client applicationId + teamId -> userId, clientId, JWT + refreshToken
        return Team(teamId, QualifiedId(UUID.randomUUID(), ""), "", "", "")
    }

    private fun initCryptoMaterial(team: Team) {
        runBlocking {
            // TODO get cryptosuite from the backend via api.getFeatureConfig().mlsConfig
            CryptoClient(team).use {
                it.mlsGetPublicKey()
                it.mlsGenerateKeyPackages()
                it.proteusGeneratePrekeys()
                it.proteusGenerateLastPrekey()

                // PUT /clients/{clientId} for MLS public key, prekeys and lastprekey
                // POST mls/key-packages for MLS key packages
            }
        }
    }
}
