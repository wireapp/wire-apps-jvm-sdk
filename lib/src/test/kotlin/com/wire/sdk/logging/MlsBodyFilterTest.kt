/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.sdk.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MlsBodyFilterTest {
    private val filter = mlsBodyFilter()

    @Test
    fun `should replace body when content type is message mls`() {
        val contentType = "message/mls"
        val originalBody = "binary-mls-data"

        val result = filter.filter(contentType, originalBody)

        assertEquals("<mls binary payload>", result)
    }

    @Test
    fun `should replace body even if content type has parameters like charset`() {
        val contentType = "message/mls; charset=utf-8"
        val originalBody = "binary-mls-data"

        val result = filter.filter(contentType, originalBody)

        assertEquals("<mls binary payload>", result)
    }

    @Test
    fun `should return original body and ignore filtering when content type is application json`() {
        val contentType = "application/json"
        val originalBody = "{\"id\": 123}"

        val result = filter.filter(contentType, originalBody)

        assertEquals(originalBody, result)
    }
}
