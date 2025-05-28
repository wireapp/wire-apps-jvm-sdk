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

package com.wire.integrations.jvm.config

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.crypto.MlsTransport
import com.wire.integrations.jvm.AppsSdkDatabase
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.client.BackendClientDemo
import com.wire.integrations.jvm.crypto.CoreCryptoClient
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.crypto.MlsTransportImpl
import com.wire.integrations.jvm.logging.LoggingConfiguration
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.persistence.AppSqlLiteStorage
import com.wire.integrations.jvm.persistence.AppStorage
import com.wire.integrations.jvm.persistence.ConversationSqlLiteStorage
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamSqlLiteStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.jvm.service.EventsRouter
import com.wire.integrations.jvm.service.MlsFallbackStrategy
import com.wire.integrations.jvm.service.WireApplicationManager
import com.wire.integrations.jvm.service.WireTeamEventsListener
import com.wire.integrations.jvm.utils.KtxSerializer
import com.wire.integrations.jvm.utils.mls
import com.wire.integrations.jvm.utils.xprotobuf
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.dsl.module
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
        }
        single<TeamStorage> { TeamSqlLiteStorage(AppsSdkDatabase(get())) }
        single<ConversationStorage> { ConversationSqlLiteStorage(AppsSdkDatabase(get())) }
        single<AppStorage> { AppSqlLiteStorage(AppsSdkDatabase(get())) }
        single<BackendClient> { BackendClientDemo(get()) }
        single<MlsTransport> { MlsTransportImpl(get()) }
        single<MlsFallbackStrategy> { MlsFallbackStrategy(get(), get()) }
        single { EventsRouter(get(), get(), get(), get(), get(), get()) }
        single<HttpClient> {
            createHttpClient(IsolatedKoinContext.getApiHost())
        }
        single<CryptoClient> {
            runBlocking {
                getOrInitCryptoClient(get(), get(), get())
            }
        }
        single { WireTeamEventsListener(get(), get()) }
        single { WireApplicationManager(get(), get(), get(), get(), get()) }
    }

@OptIn(ExperimentalLogbookKtorApi::class)
internal fun createHttpClient(apiHost: String?): HttpClient {
    return HttpClient(CIO) {
        expectSuccess = true
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

        install(SSE)

        install(UserAgent) {
            agent = "Ktor JVM SDK client"
        }

        install(HttpCache)
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
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
internal suspend fun getOrInitCryptoClient(
    backendClient: BackendClient,
    appStorage: AppStorage,
    mlsTransport: MlsTransport
): CryptoClient {
    val mlsCipherSuiteCode = backendClient.getApplicationFeatures()
        .mlsFeatureResponse.mlsFeatureConfigResponse.defaultCipherSuite
    logger.debug("Current ciphersuite: $mlsCipherSuiteCode")

    val storedClientId = appStorage.getClientId()
    return if (storedClientId != null) {
        logger.info("App has a client already, loading it")
        logger.debug("Stored client id: ${storedClientId.value}")
        CoreCryptoClient.create(
            appClientId = AppClientId(storedClientId.value),
            ciphersuiteCode = mlsCipherSuiteCode,
            mlsTransport = mlsTransport
        )
    } else {
        logger.info("App does not have a client yet, initializing it")
        val appData = backendClient.getApplicationData()
        val appClientId = AppClientId(appData.appClientId)

        val cryptoClient = CoreCryptoClient.create(
            appClientId = appClientId,
            ciphersuiteCode = mlsCipherSuiteCode,
            mlsTransport = mlsTransport
        )
        backendClient.updateClientWithMlsPublicKey(
            appClientId = appClientId,
            mlsPublicKeys = cryptoClient.mlsGetPublicKey()
        )
        backendClient.uploadMlsKeyPackages(
            appClientId = appClientId,
            mlsKeyPackages = cryptoClient.mlsGenerateKeyPackages().map { it.value }
        )
        logger.info("MLS client for $appClientId fully initialized")

        appStorage.saveClientId(appClientId.value)
        cryptoClient
    }
}
