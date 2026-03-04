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

package com.wire.sdk.model

import com.wire.crypto.ConversationId
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationTest {
    @Test
    fun `fromEntity maps GROUP correctly and preserves messageTimer`() {
        val conversationId = QualifiedId(UUID.randomUUID(), "example.com")
        val teamId = TeamId(UUID.randomUUID())
        val groupId = ConversationId(ByteArray(16) { 1 })

        val entity = ConversationEntity(
            id = conversationId,
            name = "Group chat",
            teamId = teamId,
            mlsGroupId = groupId,
            type = ConversationEntity.Type.GROUP,
            messageTimer = 600L
        )

        val conversation = Conversation.fromEntity(entity)

        assertEquals(conversationId, conversation.id)
        assertEquals("Group chat", conversation.name)
        assertEquals(teamId, conversation.teamId)
        assertEquals(Conversation.Type.GROUP, conversation.type)
        assertEquals(600L, conversation.messageTimer)
    }

    @Test
    fun `fromEntity maps ONE_TO_ONE correctly`() {
        val conversationId = QualifiedId(UUID.randomUUID(), "example.org")
        val teamId: TeamId? = null
        val groupId = ConversationId(ByteArray(16) { 2 })

        val entity = ConversationEntity(
            id = conversationId,
            name = null,
            teamId = teamId,
            mlsGroupId = groupId,
            type = ConversationEntity.Type.ONE_TO_ONE,
            messageTimer = null
        )

        val conv = Conversation.fromEntity(entity)

        assertEquals(conversationId, conv.id)
        assertNull(conv.name)
        assertNull(conv.teamId)
        assertEquals(Conversation.Type.ONE_TO_ONE, conv.type)
        assertNull(conv.messageTimer)
    }

    @Test
    fun `fromEntity with SELF type throws IllegalStateException`() {
        val conversationId = QualifiedId(UUID.randomUUID(), "example.net")
        val teamId = TeamId(UUID.randomUUID())
        val groupId = ConversationId(ByteArray(16) { 3 })

        val entity = ConversationEntity(
            id = conversationId,
            name = "Self conv",
            teamId = teamId,
            mlsGroupId = groupId,
            type = ConversationEntity.Type.SELF,
            messageTimer = null
        )

        val thrown = assertThrows(IllegalStateException::class.java) {
            Conversation.fromEntity(entity)
        }

        assertTrue(thrown.message?.contains("App cannot be added to Self conversation") == true)
    }

    @Test
    fun `equals and hashCode contract for Conversation`() {
        val conversationId = QualifiedId(UUID.randomUUID(), "dom")
        val team = TeamId(UUID.randomUUID())
        val convA =
            Conversation(
                id = conversationId,
                name = "n",
                teamId = team,
                type = Conversation.Type.GROUP,
                messageTimer = 100L
            )
        val convB =
            Conversation(
                id = conversationId,
                name = "n",
                teamId = team,
                type = Conversation.Type.GROUP,
                messageTimer = 100L
            )

        assertEquals(convA, convB)
        assertEquals(convA.hashCode(), convB.hashCode())
        assertEquals(convA.toString(), convB.toString())
    }
}
