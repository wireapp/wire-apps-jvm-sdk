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

import com.wire.crypto.ClientId
import com.wire.crypto.DeviceId
import com.wire.crypto.Uuid
import com.wire.sdk.crypto.MlsClientIdentity
import com.wire.sdk.model.QualifiedId
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class MlsClientIdentityTest {
    private val user = QualifiedId(UUID.randomUUID(), "wire.test")

    @Test
    fun `decodes the user and device from a structured client identity`() {
        assertEquals(MlsClientIdentity(user, "a11ce"), identity("a11ce"))
    }

    @Test
    fun `preserves different devices for the same user`() {
        val identities = listOf("a11ce", "b0b").map { identity(it) }
        assertEquals(
            listOf(MlsClientIdentity(user, "a11ce"), MlsClientIdentity(user, "b0b")),
            identities
        )
    }

    @Test
    fun `device IDs use unsigned lowercase hex without padding`() {
        assertEquals(MlsClientIdentity(user, "0"), identity("0"))
        assertEquals(MlsClientIdentity(user, "a11ce"), identity("00000000000A11CE"))
        assertEquals(MlsClientIdentity(user, "ffffffffffffffff"), identity("ffffffffffffffff"))
    }

    private fun identity(device: String): MlsClientIdentity =
        Uuid(user.id.toString()).use { userId ->
            DeviceId.fromHexString(device).use { deviceId ->
                ClientId(userId, deviceId, user.domain).use { it.toMlsClientIdentity() }
            }
        }
}
