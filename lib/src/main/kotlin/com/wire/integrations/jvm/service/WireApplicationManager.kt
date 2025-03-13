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

package com.wire.integrations.jvm.service

import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.persistence.TeamStorage

/**
 * Allows fetching common data and interacting with each Team instance invited to the Application.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val backendClient: BackendClient
) {
    fun getStoredTeams(): List<TeamId> = teamStorage.getAll()

    @Throws(WireException::class)
    fun getApplicationMetadata(): ApiVersionResponse = backendClient.getBackendVersion()

    @Throws(WireException::class)
    fun getApplicationData(): AppDataResponse = backendClient.getApplicationData()
}
