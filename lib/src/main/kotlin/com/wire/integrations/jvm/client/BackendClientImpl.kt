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

import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.exception.runWithWireException
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.http.FeaturesResponse
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Backend client implementation targeting the Wire APIs specific to Applications
 */
internal class BackendClientImpl internal constructor(
    private val httpClient: HttpClient
) : BackendClient {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun connectWebSocket(handleFrames: suspend (ReceiveChannel<Frame>) -> Unit) {
        logger.info("Connecting to the webSocket, waiting for events")

        httpClient.webSocket(
            path = "/apps/events",
            request = {
                header(HttpHeaders.Authorization, "Bearer ${IsolatedKoinContext.getApiToken()}")
            }
        ) {
            handleFrames(incoming)
        }
    }

    override fun getBackendVersion(): ApiVersionResponse {
        logger.info("Fetching Wire backend version")
        return runWithWireException {
            runBlocking { httpClient.get("/$API_VERSION/api-version").body() }
        }
    }

    override fun getApplicationData(): AppDataResponse {
        logger.info("Fetching application data")
        return runWithWireException {
            runBlocking { httpClient.get("/$API_VERSION/apps").body() }
        }
    }

    override fun getApplicationFeatures(): FeaturesResponse {
        logger.info("Fetching application enabled features")
        return runWithWireException {
            runBlocking {
                httpClient.get("/$API_VERSION/apps/feature-configs").body()
            }
        }
    }

    override fun confirmTeam(teamId: TeamId) {
        logger.info("Confirming team invite")
        runWithWireException {
            runBlocking {
                httpClient.post("/$API_VERSION/apps/teams/${teamId.value}/confirm")
            }
        }
    }

    override fun updateClientWithMlsPublicKey(
        appClientId: AppClientId,
        mlsPublicKeys: MlsPublicKeys
    ) {
        TODO("Not yet implemented")
    }

    override fun uploadMlsKeyPackages(
        appClientId: AppClientId,
        mlsKeyPackages: List<ByteArray>
    ) {
        TODO("Not yet implemented")
    }

    override fun uploadCommitBundle(commitBundle: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun sendMessage(mlsMessage: ByteArray) {
        TODO("Not yet implemented")
    }

    override fun getConversation(conversationId: QualifiedId): ConversationResponse {
        TODO("Not yet implemented")
    }

    override fun getConversationGroupInfo(conversationId: QualifiedId): ByteArray {
        TODO("Not yet implemented")
    }

    companion object {
        private const val API_VERSION = "v7"
    }
}
