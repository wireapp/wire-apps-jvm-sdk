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
import com.wire.sdk.model.http.client.RegisterClientRequest
import com.wire.sdk.model.http.client.RegisterClientResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class ClientsApiClient(private val httpClient: HttpClient) {
    suspend fun registerClient(
        registerClientRequest: RegisterClientRequest
    ): RegisterClientResponse {
        val clientCreatedResponse = httpClient.post("/$API_VERSION/clients") {
            setBody(registerClientRequest)
            contentType(ContentType.Application.Json)
        }.body<RegisterClientResponse>()

        // Register client is performed with an access_token having limited scope.
        //  clear the token to force a refresh with the full-scope token for next requests.
        httpClient.authProviders
            .filterIsInstance<BearerAuthProvider>()
            .first()
            .clearToken()

        return clientCreatedResponse
    }
}
