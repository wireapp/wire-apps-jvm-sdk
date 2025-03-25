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
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import kotlinx.coroutines.runBlocking

/**
 * Allows fetching common data and interacting with each Team instance invited to the Application.
 * Some functions are provided as blocking methods for Java interoperability or
 * as suspending methods for Kotlin consumers.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val conversationStorage: ConversationStorage,
    private val backendClient: BackendClient
) {
    fun getStoredTeams(): List<TeamId> = teamStorage.getAll()

    fun getStoredConversations(): List<ConversationData> = conversationStorage.getAll()

    /**
     * Get API configuration from the connected Wire backend.
     * Blocking method for Java interoperability
     */
    @Throws(WireException::class)
    fun getApplicationMetadata(): ApiVersionResponse =
        runBlocking {
            getApplicationMetadataSuspending()
        }

    /**
     * Get API configuration from the connected Wire backend.
     * Suspending method for Kotlin consumers
     */
    @Throws(WireException::class)
    suspend fun getApplicationMetadataSuspending(): ApiVersionResponse =
        backendClient.getBackendVersion()

    /**
     * Get the basic Wire Application data from the connected Wire backend.
     * Blocking method for Java interoperability
     */
    @Throws(WireException::class)
    fun getApplicationData(): AppDataResponse =
        runBlocking {
            getApplicationDataSuspending()
        }

    /**
     * Get the basic Wire Application data from the connected Wire backend.
     * Suspending method for Kotlin consumers
     */
    @Throws(WireException::class)
    suspend fun getApplicationDataSuspending(): AppDataResponse = backendClient.getApplicationData()
}
