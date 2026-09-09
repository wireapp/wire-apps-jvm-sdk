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

package com.wire.sdk.utils

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiTokenUtilsTest {
    @Test
    fun `given token with user id, when extracting user id, then return uuid`() {
        val userId = UUID.fromString("b82c3381-37b0-4545-b555-ca32a3a093d0")
        val token = tokenWithUserId(userId)

        val result = ApiTokenUtils.extractUserId(token)

        assertEquals(userId, result)
    }

    @Test
    fun `given token with uppercase user id, when extracting user id, then return uuid`() {
        val userId = UUID.fromString("b82c3381-37b0-4545-b555-ca32a3a093d0")
        val token = tokenWithUserId(userId.toString().uppercase())

        val result = ApiTokenUtils.extractUserId(token)

        assertEquals(userId, result)
    }

    @Test
    fun `given token without user id, when extracting user id, then return null`() {
        val token = "signature.v=1.k=1.d=1792763405.t=u.l=.r=33da446"

        val result = ApiTokenUtils.extractUserId(token)

        assertNull(result)
    }

    @Test
    fun `given user id inside another parameter name, then return null`() {
        val userId = UUID.fromString("b82c3381-37b0-4545-b555-ca32a3a093d0")
        val token = "signature.v=1.k=1.d=1792763405.t=u.l=.zauth_u=$userId.r=33da446"

        val result = ApiTokenUtils.extractUserId(token)

        assertNull(result)
    }

    @Test
    fun `given token with malformed user id, when extracting user id, then return null`() {
        val token = "signature.v=1.k=1.d=1792763405.t=u.l=.u=not-a-uuid.r=33da446"

        val result = ApiTokenUtils.extractUserId(token)

        assertNull(result)
    }

    private fun tokenWithUserId(userId: UUID): String = tokenWithUserId(userId.toString())

    private fun tokenWithUserId(userId: String): String =
        "signature.v=1.k=1.d=1792763405.t=u.l=.u=$userId.r=33da446"
}
