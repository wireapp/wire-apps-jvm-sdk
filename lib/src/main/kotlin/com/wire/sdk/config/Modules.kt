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

package com.wire.sdk.config

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.crypto.MlsTransport
import com.wire.sdk.AppsSdkDatabase
import com.wire.sdk.client.BackendClient
import com.wire.sdk.client.BackendClientDemo
import com.wire.sdk.crypto.CoreCryptoClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.MlsTransportImpl
import com.wire.sdk.exception.WireException
import com.wire.sdk.exception.mapToWireException
import com.wire.sdk.logging.LoggingConfiguration
import com.wire.sdk.model.AppClientId
import com.wire.sdk.model.http.client.RegisterClientRequest
import com.wire.sdk.model.http.client.toApi
import com.wire.sdk.persistence.AppSqlLiteStorage
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.persistence.ConversationSqlLiteStorage
import com.wire.sdk.persistence.ConversationStorage
import com.wire.sdk.persistence.TeamSqlLiteStorage
import com.wire.sdk.persistence.TeamStorage
import com.wire.sdk.service.EventsRouter
import com.wire.sdk.service.MlsFallbackStrategy
import com.wire.sdk.service.WireApplicationManager
import com.wire.sdk.service.WireTeamEventsListener
import com.wire.sdk.service.conversation.ConversationService
import com.wire.sdk.utils.KtxSerializer
import com.wire.sdk.utils.mls
import com.wire.sdk.utils.obfuscateClientId
import com.wire.sdk.utils.obfuscateId
import com.wire.sdk.utils.xprotobuf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.slf4j.LoggerFactory
import org.zalando.logbook.client.LogbookClient
import org.zalando.logbook.common.ExperimentalLogbookKtorApi

private const val WEBSOCKET_PING_INTERVAL_MILLIS = 20_000L
private val logger = LoggerFactory.getLogger(object {}::class.java.`package`.name)

val sdkModule =
    module {
        single<SqlDriver> {
            val driver: SqlDriver = JdbcSqliteDriver(getProperty("database-jdbc-url"))
            AppsSdkDatabase.Schema.create(driver)
            driver
        } onClose { it?.close() }
        single<TeamStorage> { TeamSqlLiteStorage(AppsSdkDatabase(get())) }
        single<ConversationStorage> { ConversationSqlLiteStorage(AppsSdkDatabase(get())) }
        single<AppStorage> { AppSqlLiteStorage(AppsSdkDatabase(get())) }
        single<BackendClient> { BackendClientDemo(get(), get()) }
        single<MlsTransport> { MlsTransportImpl(get()) }
        single<MlsFallbackStrategy> { MlsFallbackStrategy(get(), get()) }
        single { EventsRouter(get(), get(), get(), get(), get(), get()) }
        single<HttpClient> {
            createHttpClient(IsolatedKoinContext.getApiHost())
        } onClose { it?.close() }
        single<CryptoClient> {
            runBlocking {
                getOrInitCryptoClient(get(), get(), get())
            }
        } onClose { it?.close() }
        single { WireTeamEventsListener(get(), get(), get()) }

        // Services
        single { ConversationService(get(), get(), get(), get()) }

        // Manager
        single { WireApplicationManager(get(), get(), get(), get(), get()) }
    }

internal const val MAX_RETRY_NUMBER_ON_SERVER_ERROR = 10

@OptIn(ExperimentalLogbookKtorApi::class)
internal fun createHttpClient(apiHost: String?): HttpClient {
    return HttpClient(CIO) {
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                (exception as? ResponseException)?.mapToWireException()
            }
        }
        followRedirects = true
        install(LogbookClient) {
            logbook = LoggingConfiguration.logbook
        }

        install(ContentNegotiation) {
            json(KtxSerializer.json)
            mls()
            xprotobuf()
        }

        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
            pingIntervalMillis = WEBSOCKET_PING_INTERVAL_MILLIS
        }

        install(UserAgent) {
            agent = "Ktor JVM SDK client"
        }

        install(HttpCache)
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = MAX_RETRY_NUMBER_ON_SERVER_ERROR)
            exponentialDelay()
        }

        apiHost?.let {
            defaultRequest {
                url(apiHost)
            }
        }
    }
}

/**
 * Initialize the [CoreCryptoClient] if it's not already initialized.
 * Uploads the MLS public keys and key packages to the backend.
 *
 * The following times the SDK is started, the client will be loaded from the storage.
 */
@Suppress("LongMethod")
internal suspend fun getOrInitCryptoClient(
    backendClient: BackendClient,
    appStorage: AppStorage,
    mlsTransport: MlsTransport
): CryptoClient {
    val mlsCipherSuiteCode = backendClient.getApplicationFeatures()
        .mlsFeatureResponse.mlsFeatureConfigResponse.defaultCipherSuite

    // Fetch and store the backend domain from the api-version endpoint
    val backendDomain = backendClient.getAvailableApiVersions().domain
    IsolatedKoinContext.setBackendDomain(backendDomain)
    logger.info("Retrieved Wire backend domain: $backendDomain")

    val userId = System.getenv("WIRE_SDK_USER_ID")

    requireNotNull(userId) { "WIRE_SDK_USER_ID environment variable must be set" }

    val cryptoClient = CoreCryptoClient.Companion.create(
        userId = userId,
        ciphersuiteCode = mlsCipherSuiteCode
    )

    val storedDeviceId = appStorage.getDeviceId()
    if (storedDeviceId != null) {
        logger.info("Loading MLS Client for: ${storedDeviceId.obfuscateClientId()}")
        val appClientId = AppClientId.Companion.create(
            userId = userId,
            deviceId = storedDeviceId,
            userDomain = backendDomain
        )
        // App has a client, load MLS client
        cryptoClient.initializeMlsClient(
            appClientId = appClientId,
            mlsTransport = mlsTransport
        )
        appStorage.setShouldRejoinConversations(should = false)
    } else {
        val userPassword = System.getenv("WIRE_SDK_PASSWORD")
        requireNotNull(userPassword)

        // App doesn't have a client, create one
        logger.info("Initializing Proteus Client")
        cryptoClient.initializeProteusClient()
        val preKeys = cryptoClient.generateProteusPreKeys()
        val lastKey = cryptoClient.generateProteusLastPreKey()

        val clientResponse = try {
            backendClient.registerClient(
                registerClientRequest = RegisterClientRequest(
                    password = userPassword,
                    lastKey = lastKey.toApi(),
                    preKeys = preKeys.map { it.toApi() },
                    capabilities = RegisterClientRequest.Companion.DEFAULT_CAPABILITIES
                )
            )
        } catch (exception: WireException.ClientError) {
            throw IllegalStateException(
                "Error when registering client",
                exception
            )
        }

        val deviceId = clientResponse.id
        val appClientId = AppClientId.Companion.create(
            userId = userId,
            deviceId = deviceId,
            userDomain = backendDomain
        )
        appStorage.saveDeviceId(deviceId = deviceId)

        logger.info(
            "Initializing MLS Client for {} on device: {}",
            userId.obfuscateId(),
            deviceId.obfuscateClientId()
        )
        cryptoClient.initializeMlsClient(
            appClientId = appClientId,
            mlsTransport = mlsTransport
        )

        backendClient.updateClientWithMlsPublicKey(
            appClientId = appClientId,
            mlsPublicKeys = cryptoClient.mlsGetPublicKey()
        )

        backendClient.uploadMlsKeyPackages(
            appClientId = appClientId,
            mlsKeyPackages = cryptoClient.mlsGenerateKeyPackages().map { it.value.copyBytes() }
        )

        appStorage.setShouldRejoinConversations(should = true)
    }

    return cryptoClient
}
