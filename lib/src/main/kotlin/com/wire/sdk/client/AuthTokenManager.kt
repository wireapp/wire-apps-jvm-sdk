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
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.setCookie
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

class AuthTokenManager(private val appStorage: AppStorage) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Suppress("TooGenericExceptionThrown")
    suspend fun refreshAccessToken(httpClient: HttpClient): BearerTokens {
        logger.debug("Refreshing access token using stored cookie")
        val accessResponse = getAccessResponse(httpClient)

        // Chance of cookie renewal -> Store new cookie
        val responseCookies = accessResponse.setCookie()
        if (responseCookies.isNotEmpty()) {
            val newCookie = responseCookies.firstOrNull { it.name == "zuid" }?.value
            if (!newCookie.isNullOrBlank()) {
                appStorage.saveBackendCookie(newCookie)
                logger.info("Received new api token from backend, updated stored cookie")
            }
        }

        return BearerTokens(accessResponse.body<LoginResponse>().accessToken, null)
    }

    private suspend fun getAccessResponse(httpClient: HttpClient): HttpResponse =
        try {
            val apiToken = appStorage.getBackendCookie()
            val deviceId = appStorage.getDeviceId()
            val url = "/$API_VERSION/access".let {
                if (deviceId != null) "$it?client_id=$deviceId" else it
            }
            httpClient.post(url) {
                headers {
                    append(HttpHeaders.Cookie, "zuid=$apiToken")
                }
                accept(ContentType.Application.Json)
            }
        } catch (ex: WireException.ClientError) {
            logger.error("Unable to retrieve access token, Error: ${ex.message}")
            if (ex.response.isCredentialsInvalid()) {
                appStorage.deleteBackendCookie()
            }
            // TODO Can't recover from this, need to restart the app with a valid api token
            error("Current cookie/api-token is expired. Get a apiToken and restart the App")
        }

    @Serializable
    data class LoginResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int
    )
}
