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

package com.wire.sdk.model.calling

import com.wire.sdk.model.QualifiedId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class SubconversationEpochInfoTest {
    @Test
    fun `member map copies all device lists and cannot be mutated by consumers`() {
        val user = QualifiedId(UUID.randomUUID(), "wire.test")
        val devices = mutableListOf("device-1", "device-2")
        val members = mutableMapOf(user to devices)
        SubconversationEpochInfo(user, "AQID", 1, members, ByteArray(32)).use { info ->
            devices.clear()
            members.clear()
            assertEquals(mapOf(user to listOf("device-1", "device-2")), info.members)
            assertFailsWith<UnsupportedOperationException> {
                (info.members as MutableMap).clear()
            }
            assertFailsWith<UnsupportedOperationException> {
                (info.members.getValue(user) as MutableList).clear()
            }
        }
    }
}
