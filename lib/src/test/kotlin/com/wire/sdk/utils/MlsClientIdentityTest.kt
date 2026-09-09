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

import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class MlsClientIdentityTest {
    private val user = QualifiedId(UUID.randomUUID(), "wire.test")

    @Test
    fun `parses the identity produced by CryptoClientId`() {
        val identity = CryptoClientId.create(user, "device-123").value
        assertEquals(user to "device-123", identity.parseMlsClientIdentity())
    }

    @Test
    fun `preserves different devices for the same user`() {
        val identities = listOf("device-1", "device-2").map {
            CryptoClientId.create(user, it).value.parseMlsClientIdentity()
        }
        assertEquals(listOf(user to "device-1", user to "device-2"), identities)
    }

    @Test
    fun `rejects missing user device or domain and invalid user IDs`() {
        val invalidIdentities = listOf(
            "",
            ":device@wire.test",
            "${user.id}@wire.test",
            "${user.id}:@wire.test",
            "${user.id}:device",
            "${user.id}:device@",
            "invalid-user:device@wire.test"
        )
        invalidIdentities.forEach {
            assertFailsWith<IllegalArgumentException> { it.parseMlsClientIdentity() }
        }
    }
}
