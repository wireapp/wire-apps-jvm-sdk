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
import com.wire.sdk.model.http.user.UserClientResponse
import com.wire.sdk.model.http.user.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.collections.component1
import kotlin.collections.component2

internal class UsersApiClient(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val basePath = "users"

    /**
     * Get User details
     *
     * @param [QualifiedId] The ID of the user to be requested
     * @return [UserResponse]
     */
    suspend fun getUserData(userId: QualifiedId): UserResponse {
        logger.info("Fetching user: $userId")
        return httpClient.get(
            "/$basePath/${userId.domain}/${userId.id}"
        ).body<UserResponse>()
    }

    suspend fun getClientsByUserId(userId: QualifiedId): List<UserClientResponse> {
        val clients = httpClient
            .get("/$basePath/${userId.domain}/${userId.id}/clients")
            .body<List<UserClientResponse>>()

        return clients
    }

    suspend fun getClientsByUserIds(
        userIds: List<QualifiedId>
    ): Map<QualifiedId, List<UserClientResponse>> {
        val response = httpClient.post("/$basePath/list-clients") {
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
}
