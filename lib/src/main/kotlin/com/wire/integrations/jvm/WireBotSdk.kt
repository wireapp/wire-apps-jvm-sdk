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

package com.wire.integrations.jvm

import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.service.WireApplicationManager
import com.wire.integrations.jvm.util.WireErrorException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import java.util.UUID

class WireBotSdk(
    applicationId: UUID,
    apiToken: String,
    apiHost: String,
    cryptographyStoragePassword: String,
    wireBotListener: WireEventsHandler
) {
    init {
        if (apiHost.contains("http://") || apiHost.contains("https://")) {
            throw WireErrorException.InvalidParameter(
                message = "Please remove http:// or https:// from apiHost"
            )
        }

        IsolatedKoinContext.setApplicationId(applicationId)
        IsolatedKoinContext.setApiHost(apiHost)
        IsolatedKoinContext.setCryptographyStoragePassword(cryptographyStoragePassword)

        initDynamicModules(
            apiToken = apiToken,
            apiHost = apiHost,
            wireBotListener = wireBotListener
        )

        // TODO: probably trigger here the connections to Server-Sent Events and the WebSockets
    }

    fun getTeamManager(): WireApplicationManager {
        return IsolatedKoinContext.koinApp.koin.get()
    }

    private fun initDynamicModules(
        apiToken: String,
        apiHost: String,
        wireEventsHandler: WireEventsHandler
    ) {
        val dynamicModule =
            module {
                single {
                    wireEventsHandler
                }

                single<HttpClient> {
                    HttpClient(OkHttp) {
                        expectSuccess = true
                        followRedirects = true

                        install(ContentNegotiation) {
                            json(
                                Json {
                                    prettyPrint = true
                                    isLenient = true
                                }
                            )
                        }

                        install(WebSockets) {
                            contentConverter = KotlinxWebsocketSerializationConverter(Json)
                        }

                        install(SSE)

                        install(Logging) {
                            level = LogLevel.ALL
                            sanitizeHeader { header -> header == HttpHeaders.Authorization }
                        }

                        install(UserAgent) {
                            agent = "Ktor JVM SDK client"
                        }

                        install(HttpCache)
                        install(HttpRequestRetry) {
                            retryOnServerErrors(maxRetries = 3)
                            exponentialDelay()
                        }

                        defaultRequest {
                            header("Authorization", "Bearer $apiToken")
                            url.host = apiHost
                        }
                    }
                }
            }

        IsolatedKoinContext.koinApp.koin.loadModules(listOf(dynamicModule))
    }
}
