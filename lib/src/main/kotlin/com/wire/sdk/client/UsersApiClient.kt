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

import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.user.ListClientsRequest
import com.wire.sdk.model.http.user.ListClientsResponse
import com.wire.sdk.model.http.user.ListUsersRequest
import com.wire.sdk.model.http.user.ListUsersResponse
import com.wire.sdk.model.http.user.UserClientResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2

internal class UsersApiClient(private val httpClient: HttpClient) {
    private val basePathUsers = "users"
    private val basePathListUsers = "list-users"

    suspend fun getClientsByUserId(userId: QualifiedId): List<UserClientResponse> {
        val clients = httpClient
            .get("/$basePathUsers/${userId.domain}/${userId.id}/clients")
            .body<List<UserClientResponse>>()

        return clients
    }

    suspend fun getClientsByUserIds(
        userIds: List<QualifiedId>
    ): Map<QualifiedId, List<UserClientResponse>> {
        val response = httpClient.post("/$basePathUsers/list-clients") {
            setBody(ListClientsRequest(qualifiedUsers = userIds))
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<ListClientsResponse>()

        return response.qualifiedUserMap.flatMap { (domain, users) ->
            users.map { (userId, clients) ->
                QualifiedId(UUID.fromString(userId), domain) to clients
            }
        }.toMap()
    }

    /**
     * This endpoint uses [basePathListUsers] instead of the default [basePathUsers]
     */
    suspend fun getUsers(userIds: List<QualifiedId>): ListUsersResponse =
        httpClient.post("/$basePathListUsers") {
            setBody(ListUsersRequest(qualifiedIds = userIds))
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<ListUsersResponse>()
}
