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

import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.jvm.util.WireErrorException
import com.wire.integrations.jvm.util.runWithWireErrorException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/**
 * Allows fetching and interacting with each Team instance that invited the application.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val httpClient: HttpClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java.canonicalName)

    fun getTeams(): List<Team> {
        return teamStorage.getAll()
    }

    fun getApplicationMetadata(): ApiVersionResponse {
        logger.info("Fetching application metadata")
        return runWithWireErrorException {
            runBlocking { httpClient.get("/v7/api-version").body() }
        }
    }

    @Throws(WireErrorException::class)
    fun fetchApplicationData(): AppDataResponse {
        logger.info("Fetching application data")
        return runWithWireErrorException {
            runBlocking { httpClient.get("/v7/app-data").body() }
        }
    }
}
