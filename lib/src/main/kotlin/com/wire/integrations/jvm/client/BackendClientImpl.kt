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

package com.wire.integrations.jvm.client

import com.wire.integrations.jvm.exception.runWithWireException
import com.wire.integrations.jvm.model.ClientId
import com.wire.integrations.jvm.model.ProteusPreKey
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.http.ConfirmTeamResponse
import com.wire.integrations.jvm.model.http.FeaturesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Backend client implementation targeting the Wire APIs specific to Applications
 */
internal class BackendClientImpl internal constructor(
    private val httpClient: HttpClient
) : BackendClient {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun getBackendVersion(): ApiVersionResponse {
        logger.info("Fetching Wire backend version")
        return runWithWireException {
            runBlocking { httpClient.get("/$API_VERSION/api-version").body() }
        }
    }

    override fun getApplicationData(): AppDataResponse {
        logger.info("Fetching application data")
        return runWithWireException {
            runBlocking { httpClient.get("/$API_VERSION/app-data").body() }
        }
    }

    override fun getApplicationFeatures(teamId: TeamId): FeaturesResponse {
        logger.info("Fetching application enabled features")
        return runWithWireException {
            runBlocking {
                httpClient.get("/$API_VERSION/apps/teams/${teamId.value}/feature-configs").body()
            }
        }
    }

    override fun confirmTeam(teamId: TeamId): ConfirmTeamResponse {
        logger.info("Confirming team invite")
        return runWithWireException {
            runBlocking {
                httpClient.post("/$API_VERSION/apps/teams/${teamId.value}/confirm").body()
            }
        }
    }

    override fun registerClientWithProteus(
        prekeys: List<ProteusPreKey>,
        lastPreKey: ProteusPreKey
    ): ClientId {
        TODO("Not yet implemented")
    }

    override fun updateClientWithMlsPublicKey(
        clientId: ClientId,
        mlsPublicKey: ByteArray
    ) {
        TODO("Not yet implemented")
    }

    override fun uploadMlsKeyPackages(
        clientId: ClientId,
        mlsKeyPackages: List<ByteArray>
    ) {
        TODO("Not yet implemented")
    }

    override fun uploadCommitBundle(commitBundle: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun sendMlsMessage(mlsMessage: ByteArray) {
        TODO("Not yet implemented")
    }

    companion object {
        private const val API_VERSION = "v7"
    }
}
