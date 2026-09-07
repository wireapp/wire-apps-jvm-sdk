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
import com.wire.sdk.model.http.user.SelfUserResponse
import com.wire.sdk.persistence.AppStorage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class SelfServiceTest {
    @Test
    fun `given application data is not stored, when fetchAndSaveApplicationData, then save it`() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { hasApplicationQualifiedId() } returns false
                every { saveApplicationQualified(APPLICATION_QUALIFIED_ID) } returns Unit
                every { hasApplicationTeamId() } returns false
                every { saveApplicationTeamId(APPLICATION_TEAM_ID) } returns Unit
            }
            val service = SelfService(selfApiClient(), appStorage)

            service.fetchAndSaveApplicationData()

            verify(exactly = 1) {
                appStorage.saveApplicationQualified(APPLICATION_QUALIFIED_ID)
                appStorage.saveApplicationTeamId(APPLICATION_TEAM_ID)
            }
        }

    @Test
    fun `given matching application data is stored, when fetching, then keep it`() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { hasApplicationQualifiedId() } returns true
                every { getApplicationQualifiedId() } returns APPLICATION_QUALIFIED_ID
                every { hasApplicationTeamId() } returns true
                every { getApplicationTeamId() } returns APPLICATION_TEAM_ID
            }
            val service = SelfService(selfApiClient(), appStorage)

            service.fetchAndSaveApplicationData()

            verify(exactly = 0) {
                appStorage.saveApplicationQualified(any())
                appStorage.saveApplicationTeamId(any())
            }
        }

    @Test
    fun `given different qualified id is stored, when fetchAndSaveApplicationData, then throw`() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { hasApplicationQualifiedId() } returns true
                every { getApplicationQualifiedId() } returns QualifiedId(
                    id = UUID.randomUUID(),
                    domain = APPLICATION_QUALIFIED_ID.domain
                )
            }
            val service = SelfService(selfApiClient(), appStorage)

            assertFailsWith<WireException.UnknownError> {
                service.fetchAndSaveApplicationData()
            }
        }

    @Test
    fun `given different team id is stored, when fetchAndSaveApplicationData, then throw`() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { hasApplicationQualifiedId() } returns true
                every { getApplicationQualifiedId() } returns APPLICATION_QUALIFIED_ID
                every { hasApplicationTeamId() } returns true
                every { getApplicationTeamId() } returns TeamId(UUID.randomUUID())
            }
            val service = SelfService(selfApiClient(), appStorage)

            assertFailsWith<WireException.UnknownError> {
                service.fetchAndSaveApplicationData()
            }
        }

    @Test
    fun `given self response has no team, when fetchAndSaveApplicationData, then throw`() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { hasApplicationQualifiedId() } returns true
                every { getApplicationQualifiedId() } returns APPLICATION_QUALIFIED_ID
            }
            val service = SelfService(selfApiClient(teamId = null), appStorage)

            assertFailsWith<WireException.InvalidParameter> {
                service.fetchAndSaveApplicationData()
            }
        }

    private fun selfApiClient(teamId: UUID? = APPLICATION_TEAM_ID.value): SelfApiClient =
        mockk {
            coEvery { getSelfUser() } returns SelfUserResponse(
                qualifiedId = APPLICATION_QUALIFIED_ID,
                teamId = teamId
            )
        }

    private companion object {
        val APPLICATION_QUALIFIED_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = "wire.example.com"
        )
        val APPLICATION_TEAM_ID = TeamId(UUID.randomUUID())
    }
}
