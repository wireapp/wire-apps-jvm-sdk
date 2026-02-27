/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.sdk.client

import com.wire.sdk.client.BackendClient.Companion.API_VERSION
import com.wire.sdk.exception.WireException
import com.wire.sdk.persistence.AppStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.setCookie
import org.slf4j.LoggerFactory

class AuthTokenManager {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private var bearerToken: BearerTokens? = null

    fun getAccessToken(): BearerTokens? {
        return bearerToken
    }

    suspend fun refreshAccessToken(
        httpClient: HttpClient,
        appStorage: AppStorage
    ) {
        val apiToken = appStorage.getBackendCookie()
        val deviceId = appStorage.getDeviceId()
        val url = "/$API_VERSION/access".let {
            if (deviceId != null) "$it?client_id=$deviceId" else it
        }
        val accessResponse = try {
            httpClient.post(url) {
                headers {
                    append(HttpHeaders.Cookie, "zuid=$apiToken")
                }
                accept(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.error("Unable to retrieve access token, Error: ${ex.message}")
            if (isCookieExpired(ex)) {
                appStorage.deleteBackendCookie()
            }
            // Can't recover from this, need to restart the app with a valid api token
            throw Error("Current cookie/api-token is expired. Get a apiToken and restart the App")
        }

        // Chance of cookie renewal -> Store new cookie
        val responseCookies = accessResponse.setCookie()
        if (!responseCookies.isEmpty()) {
            val newCookie = responseCookies.firstOrNull { it.name == "zuid" }?.value
            if (!newCookie.isNullOrBlank()) {
                appStorage.saveBackendCookie(newCookie)
                logger.info("Received new api token from backend, updated stored cookie")
            }
        }

        bearerToken = BearerTokens(accessResponse.body<LoginResponse>().accessToken, null)
    }


    private fun isCookieExpired(ex: WireException.ClientError): Boolean =
        ex.throwable is ClientRequestException &&
            (
            ex.throwable.response.status == HttpStatusCode.Unauthorized ||
                    ex.throwable.response.status == HttpStatusCode.Forbidden
            )
}
