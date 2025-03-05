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
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.http.ClientAddRequest
import com.wire.integrations.jvm.model.http.ClientAddResponse
import com.wire.integrations.jvm.model.http.ClientUpdateRequest
import com.wire.integrations.jvm.model.http.ConfirmTeamResponse
import com.wire.integrations.jvm.model.http.FeaturesResponse
import com.wire.integrations.jvm.model.http.MlsKeyPackageRequest
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.utils.Mls
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.UUID

/**
 * Backend client implementation for test/demo purposes
 * Useful for testing the SDK without the Backend having new Application API implemented,
 * instead using the standard Client API
 */
internal class BackendClientDemo internal constructor(
    private val httpClient: HttpClient
) : BackendClient {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Simple cache of the Backend features, as the MLS values we care about
    // will be the same for all teams, so we can make the API call only once.
    private var cachedFeatures: FeaturesResponse? = null
    private var cachedAccessToken: String? = null

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
        return cachedFeatures ?: runWithWireException {
            runBlocking {
                val token = loginUser()
                httpClient.get("/$API_VERSION/feature-configs") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }.body<FeaturesResponse>().also { cachedFeatures = it }
            }
        }
    }

    override fun confirmTeam(teamId: TeamId): ConfirmTeamResponse {
        logger.info("Confirming team invite")
        return ConfirmTeamResponse(QualifiedId(DEMO_USER_ID, DEMO_DOMAIN))
    }

    /**
     * Login DEMO user in the backend, get access_token for further requests.
     * Not needed in the actual implementation, as the SDK is authenticated with the API_TOKEN
     */
    private suspend fun loginUser(): String {
        return cachedAccessToken ?: httpClient.post("/$API_VERSION/login") {
            setBody(LoginRequest(DEMO_USER_EMAIL, DEMO_USER_PASSWORD))
            contentType(ContentType.Application.Json)
        }.body<LoginResponse>().accessToken.also { cachedAccessToken = it }
    }

    override fun registerClientWithProteus(
        prekeys: List<ProteusPreKey>,
        lastPreKey: ProteusPreKey
    ): ClientId {
        return runWithWireException {
            runBlocking {
                val token = loginUser()
                val clientAddResponse: HttpResponse = httpClient.post("/$API_VERSION/clients") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(
                        ClientAddRequest(
                            password = DEMO_USER_PASSWORD,
                            lastkey = lastPreKey,
                            prekeys = prekeys
                        )
                    )
                    contentType(ContentType.Application.Json)
                }
                clientAddResponse.bodyAsText().let { logger.info(it) }
                val clientId: String = clientAddResponse.body<ClientAddResponse>().id
                logger.info("Registered new client with id $clientId")
                ClientId(clientId)
            }
        }
    }

    override fun updateClientWithMlsPublicKey(
        clientId: ClientId,
        mlsPublicKey: ByteArray
    ) {
        return runWithWireException {
            runBlocking {
                val token = loginUser()
                val mlsPublicKeys =
                    MlsPublicKeys(
                        ecdsaSecp256r1Sha256 = Base64.getEncoder().encodeToString(mlsPublicKey)
                    )
                httpClient.put("/$API_VERSION/clients/${clientId.value}") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(
                        ClientUpdateRequest(
                            mlsPublicKeys = mlsPublicKeys
                        )
                    )
                    contentType(ContentType.Application.Json)
                }
                logger.info("Updated client with mls info for client: $clientId")
            }
        }
    }

    override fun uploadMlsKeyPackages(
        clientId: ClientId,
        mlsKeyPackages: List<ByteArray>
    ) = runWithWireException {
        runBlocking {
            val token = loginUser()
            val mlsKeyPackageRequest =
                MlsKeyPackageRequest(mlsKeyPackages.map { Base64.getEncoder().encodeToString(it) })
            httpClient.post("/$API_VERSION/mls/key-packages/self/${clientId.value}") {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(mlsKeyPackageRequest)
                contentType(ContentType.Application.Json)
            }
            logger.info("Updated client with mls key packages for client: $clientId")
        }
    }

    override fun uploadCommitBundle(commitBundle: ByteArray): Unit =
        runWithWireException {
            runBlocking {
                val token = loginUser()
                httpClient.post("/$API_VERSION/mls/commit-bundles") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(commitBundle)
                    contentType(Mls)
                }
            }
        }

    override fun sendMlsMessage(mlsMessage: ByteArray): Unit =
        runWithWireException {
            runBlocking {
                val token = loginUser()
                httpClient.post("/$API_VERSION/mls/messages") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                    }
                    setBody(mlsMessage)
                    contentType(Mls)
                }
            }
        }

    companion object {
        private const val API_VERSION = "v7"

        private val DEMO_USER_ID = UUID.fromString("853eaec4-7b01-4800-85c7-642c4e426e69")
        private const val DEMO_USER_EMAIL = "smoketester+rowe105480@wire.com"
        private const val DEMO_USER_PASSWORD = "Aqa123456!"
        private const val DEMO_DOMAIN = "staging.zinfra.io"
    }
}

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("expires_in")
    val expiresIn: Int
)
