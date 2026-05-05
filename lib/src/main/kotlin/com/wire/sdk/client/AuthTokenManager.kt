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

import com.wire.sdk.exception.WireException
import com.wire.sdk.persistence.AppStorage
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.RefreshTokensParams
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

    fun loadTokens(): BearerTokens? =
        appStorage.getAccessToken()?.let { accessToken ->
            BearerTokens(accessToken, null)
        }

    suspend fun refreshAccessToken(params: RefreshTokensParams): BearerTokens {
        logger.debug("Refreshing access token using stored cookie")
        val accessResponse = getAccessResponse(params)

        // Chance of cookie renewal -> Store new cookie
        val responseCookies = accessResponse.setCookie()
        if (responseCookies.isNotEmpty()) {
            val newCookie = responseCookies.firstOrNull { it.name == "zuid" }?.value
            if (!newCookie.isNullOrBlank()) {
                appStorage.saveBackendCookie(newCookie)
                logger.info("Received new api token from backend, updated stored cookie")
            }
        }

        val accessToken = accessResponse.body<AccessResponse>().accessToken
        appStorage.saveAccessToken(accessToken)
        logger.debug("Stored refreshed access token")
        return BearerTokens(accessToken, null)
    }

    private suspend fun getAccessResponse(params: RefreshTokensParams): HttpResponse =
        try {
            val cookie = appStorage.getBackendCookie()
            val deviceId = appStorage.getDeviceId()
            val basePath = "access"
            val url = "/$basePath".let {
                if (deviceId != null) "$it?client_id=$deviceId" else it
            }
            with(params) {
                client.post(url) {
                    markAsRefreshTokenRequest()
                    headers {
                        append(HttpHeaders.Cookie, "zuid=$cookie")
                    }
                    accept(ContentType.Application.Json)
                }
            }
        } catch (ex: WireException.ClientError) {
            logger.error("Unable to retrieve access token, Error: ${ex.message}")
            if (ex.response.isCredentialsInvalid()) {
                appStorage.deleteBackendCookie()
                appStorage.deleteAccessToken()
            }
            // TODO Can't recover from this, need to restart the app with a valid api token
            error("Current cookie/api-token is expired. Get a apiToken and restart the App")
        }

    @Serializable
    private data class AccessResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int
    )
}
