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

package com.wire.sdk.service.self

import com.wire.sdk.client.SelfApiClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.persistence.AppStorage
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * Service layer responsible for fetching self user (App user) data from the backend, mapping and
 * saving them locally.
 */
internal class SelfService(
    private val selfApiClient: SelfApiClient,
    private val appStorage: AppStorage
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun fetchAndSaveApplicationData() {
        this.logger.info("Fetching application QualifiedId")
        val applicationData = selfApiClient.getSelfUser()

        saveApplicationQualified(applicationData.qualifiedId)
        saveApplicationTeamId(applicationData.teamId)
    }

    private fun saveApplicationQualified(applicationQualifiedId: QualifiedId) {
        if (!appStorage.hasApplicationQualifiedId()) {
            this.logger.info("Saving application QualifiedId: $applicationQualifiedId")
            appStorage.saveApplicationQualified(applicationQualifiedId)
            return
        }

        val storedApplicationQualifiedId = appStorage.getApplicationQualifiedId()
        if (storedApplicationQualifiedId.toFullString() != applicationQualifiedId.toFullString()) {
            throw WireException.UnknownError(
                """
                    Stored application QualifiedId $storedApplicationQualifiedId does not match fetched self QualifiedId $applicationQualifiedId. Clear SDK storage before using a token for another app.
                """.trimIndent()
            )
        }

        this.logger.info("Application QualifiedId already stored: $storedApplicationQualifiedId")
    }

    private fun saveApplicationTeamId(teamId: UUID?) {
        teamId ?: throw WireException.InvalidParameter("The application does not belong to a team")

        val applicationTeamId = TeamId(teamId)
        if (!appStorage.hasApplicationTeamId()) {
            this.logger.info("Saving application TeamId: $applicationTeamId")
            appStorage.saveApplicationTeamId(applicationTeamId)
            return
        }

        val storedApplicationTeamId = appStorage.getApplicationTeamId()
        if (storedApplicationTeamId.value.toString() != applicationTeamId.value.toString()) {
            throw WireException.UnknownError(
                """
                    Stored application TeamId $storedApplicationTeamId does not match fetched self TeamId $applicationTeamId. Clear SDK storage before using a token for another app.
                """.trimIndent()
            )
        }

        this.logger.info("Application TeamId already stored: $storedApplicationTeamId")
    }
}
