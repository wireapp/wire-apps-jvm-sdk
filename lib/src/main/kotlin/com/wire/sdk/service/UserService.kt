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

package com.wire.sdk.service

import com.wire.sdk.client.UsersApiClient
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireUser
import com.wire.sdk.model.http.user.UserResponse
import org.slf4j.LoggerFactory

/**
 * Service layer responsible for fetching user data from the backend and mapping
 * internal HTTP response models to public SDK [WireUser] objects.
 */
internal class UserService(private val usersApiClient: UsersApiClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Fetches the user data for the given [userId] and returns a [WireUser].
     *
     * Nullable fields in the backend response ([UserResponse.email], [UserResponse.handle],
     * [UserResponse.teamId], [UserResponse.deleted]) are preserved as `null` in the
     * returned [WireUser].
     *
     * @param userId The qualified ID of the user to fetch.
     * @return A [WireUser] populated with the response data.
     */
    suspend fun getUser(userId: QualifiedId): WireUser {
        logger.info("Fetching user: $userId")
        return usersApiClient.getUserData(userId).toWireUser()
    }

    private fun UserResponse.toWireUser(): WireUser = WireUser(
        id = id,
        name = name,
        email = email,
        handle = handle,
        teamId = teamId,
        supportedProtocols = supportedProtocols,
        deleted = deleted
    )
}


