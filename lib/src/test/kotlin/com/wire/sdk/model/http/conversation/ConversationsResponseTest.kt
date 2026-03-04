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

package com.wire.sdk.model.http.conversation

import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.QualifiedId
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationsResponseTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `serialize and deserialize conversations response`() {
        val conversationId1 = QualifiedId(UUID.randomUUID(), "example.com")
        val conversationId2 = QualifiedId(UUID.randomUUID(), "example.org")

        val memberSelf = ConversationMemberSelf(conversationId1, ConversationRole.ADMIN)
        val members = ConversationMembers(self = memberSelf, others = emptyList())

        val conv = ConversationResponse(
            id = conversationId1,
            teamId = null,
            groupId = null,
            name = "test",
            epoch = 1L,
            protocol = CryptoProtocol.MLS,
            members = members,
            type = ConversationResponse.Type.GROUP,
            publicKeys = null,
            messageTimer = null
        )

        val original = ConversationsResponse(
            failed = listOf(conversationId1),
            notFound = listOf(conversationId2),
            found = listOf(conv)
        )

        val encoded = json.encodeToString(ConversationsResponse.serializer(), original)
        val decoded = json.decodeFromString(ConversationsResponse.serializer(), encoded)

        assertEquals(original, decoded, "Deserialized object should equal the original")
    }

    @Test
    fun `json contains expected field names`() {
        val empty =
            ConversationsResponse(failed = emptyList(), notFound = emptyList(), found = emptyList())
        val encoded = json.encodeToString(ConversationsResponse.serializer(), empty)
        assertTrue(encoded.contains("\"failed\""), "JSON should contain 'failed' field")
        assertTrue(encoded.contains("\"not_found\""), "JSON should contain 'not_found' field")
        assertTrue(encoded.contains("\"found\""), "JSON should contain 'found' field")
    }

    @Test
    fun `empty lists serialize and deserialize correctly`() {
        val empty =
            ConversationsResponse(failed = emptyList(), notFound = emptyList(), found = emptyList())
        val encoded = json.encodeToString(ConversationsResponse.serializer(), empty)
        val decoded = json.decodeFromString(ConversationsResponse.serializer(), encoded)
        assertEquals(empty, decoded)
        assertEquals(0, decoded.failed.size)
        assertEquals(0, decoded.notFound.size)
        assertEquals(0, decoded.found.size)
    }

    @Test
    fun `equals and hashCode contract`() {
        val conversationId = QualifiedId(UUID.randomUUID(), "domain")
        val memberSelf = ConversationMemberSelf(conversationId, ConversationRole.MEMBER)
        val members = ConversationMembers(self = memberSelf, others = emptyList())

        val conv = ConversationResponse(
            id = conversationId,
            teamId = null,
            groupId = null,
            name = null,
            epoch = null,
            protocol = CryptoProtocol.MLS,
            members = members,
            type = ConversationResponse.Type.GROUP
        )

        val a =
            ConversationsResponse(
                failed = listOf(conversationId),
                notFound = emptyList(),
                found = listOf(conv)
            )
        val b = a.copy()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a.toString(), b.toString())
    }
}
