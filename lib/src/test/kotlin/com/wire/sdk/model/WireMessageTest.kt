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

package com.wire.sdk.model

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class WireMessageTest {
    private val conversationId = QualifiedId(
        id = UUID.randomUUID(),
        domain = "wire.com"
    )

    private fun textMessage(expiresAfterMillis: Long? = null): WireMessage.Text =
        WireMessage.Text.create(
            conversationId = conversationId,
            text = "Hello",
            expiresAfterMillis = expiresAfterMillis
        )

    private fun locationMessage(expiresAfterMillis: Long? = null): WireMessage.Location =
        WireMessage.Location.create(
            conversationId = conversationId,
            latitude = 1.0f,
            longitude = 2.0f,
            expiresAfterMillis = expiresAfterMillis
        )

    @Test
    fun `Text create returns message with correct text and conversationId`() {
        val message = WireMessage.Text.create(
            conversationId = conversationId,
            text = "Hello"
        )

        assertEquals("Hello", message.text)
        assertEquals(conversationId, message.conversationId)
    }

    @Test
    fun `Text create assigns a random UUID`() {
        val first = WireMessage.Text.create(conversationId = conversationId, text = "Hi")
        val second = WireMessage.Text.create(conversationId = conversationId, text = "Hi")

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `Text create defaults to null expiresAfterMillis`() {
        val message = textMessage()

        assertNull(message.expiresAfterMillis)
    }

    @Test
    fun `Text create sets expiresAfterMillis when provided`() {
        val message = textMessage(expiresAfterMillis = 10_000L)

        assertEquals(10_000L, message.expiresAfterMillis)
    }

    @Test
    fun `Text create defaults to empty mentions and linkPreviews`() {
        val message = textMessage()

        assertTrue(message.mentions.isEmpty())
        assertTrue(message.linkPreviews.isEmpty())
    }

    @Test
    fun `Text create sets mentions when provided`() {
        val mention = WireMessage.Mention(userId = QualifiedId(UUID.randomUUID(), "wire.com"))
        val message = WireMessage.Text.create(
            conversationId = conversationId,
            text = "@user",
            mentions = listOf(mention)
        )

        assertEquals(listOf(mention), message.mentions)
    }

    @Test
    fun `Text create sets linkPreviews when provided`() {
        val preview = WireMessage.LinkPreview(url = "https://wire.com", urlOffset = 0)
        val message = WireMessage.Text.create(
            conversationId = conversationId,
            text = "check https://wire.com",
            linkPreviews = listOf(preview)
        )

        assertEquals(listOf(preview), message.linkPreviews)
    }

    @Test
    fun `Text implements Ephemeral and Replyable`() {
        val message = textMessage()

        assertTrue(message is WireMessage.Ephemeral)
        assertTrue(message is WireMessage.Replyable)
    }

    @Test
    fun `createReply succeeds for a non-ephemeral replyable message`() {
        val original = textMessage()

        val reply = WireMessage.Text.createReply(
            text = "Reply",
            originalMessage = original
        )

        assertEquals(original.id, reply.quotedMessageId)
        assertEquals(original.conversationId, reply.conversationId)
        assertEquals("Reply", reply.text)
    }

    @Test
    fun `createReply succeeds for an ephemeral message without expiry`() {
        val original = textMessage(expiresAfterMillis = null)

        assertDoesNotThrow {
            WireMessage.Text.createReply(
                text = "Reply",
                originalMessage = original
            )
        }
    }

    @Test
    fun `createReply throws when original message is expiring ephemeral`() {
        val original = textMessage(expiresAfterMillis = 5_000L)

        assertThrows<IllegalArgumentException> {
            WireMessage.Text.createReply(
                text = "Reply",
                originalMessage = original
            )
        }
    }

    @Test
    fun `createReply throws when original message is not Replyable`() {
        val ping = WireMessage.Ping.create(conversationId = conversationId)

        assertThrows<IllegalArgumentException> {
            WireMessage.Text.createReply(
                text = "Reply",
                originalMessage = ping
            )
        }
    }

    @Test
    fun `createReply works with a Location as original message`() {
        val original = locationMessage()

        val reply = WireMessage.Text.createReply(
            text = "Nice spot!",
            originalMessage = original
        )

        assertEquals(original.id, reply.quotedMessageId)
    }

    @Test
    fun `createReply throws for expiring Location message`() {
        val original = locationMessage(expiresAfterMillis = 3_000L)

        assertThrows<IllegalArgumentException> {
            WireMessage.Text.createReply(
                text = "Nice spot!",
                originalMessage = original
            )
        }
    }

    @Test
    fun `createReply inherits conversationId from original message`() {
        val original = textMessage()
        val reply = WireMessage.Text.createReply(text = "Reply", originalMessage = original)

        assertEquals(original.conversationId, reply.conversationId)
    }

    @Test
    fun `createReply sets mentions when provided`() {
        val original = textMessage()
        val mention = WireMessage.Mention(userId = QualifiedId(UUID.randomUUID(), "wire.com"))

        val reply = WireMessage.Text.createReply(
            text = "@user reply",
            mentions = listOf(mention),
            originalMessage = original
        )

        assertEquals(listOf(mention), reply.mentions)
    }

    @Test
    fun `Ping create returns message with correct conversationId`() {
        val ping = WireMessage.Ping.create(conversationId = conversationId)

        assertEquals(conversationId, ping.conversationId)
    }

    @Test
    fun `Ping create defaults to null expiresAfterMillis`() {
        val ping = WireMessage.Ping.create(conversationId = conversationId)

        assertNull(ping.expiresAfterMillis)
    }

    @Test
    fun `Ping create sets expiresAfterMillis when provided`() {
        val ping = WireMessage.Ping.create(
            conversationId = conversationId,
            expiresAfterMillis = 2_000L
        )

        assertEquals(2_000L, ping.expiresAfterMillis)
    }

    @Test
    fun `Ping create assigns a random UUID`() {
        val first = WireMessage.Ping.create(conversationId = conversationId)
        val second = WireMessage.Ping.create(conversationId = conversationId)

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `Ping implements Ephemeral`() {
        val ping = WireMessage.Ping.create(conversationId = conversationId)

        assertTrue(ping is WireMessage.Ephemeral)
    }

    @Test
    fun `Location create returns message with correct coordinates`() {
        val location = WireMessage.Location.create(
            conversationId = conversationId,
            latitude = 48.8566f,
            longitude = 2.3522f
        )

        assertEquals(48.8566f, location.latitude)
        assertEquals(2.3522f, location.longitude)
    }

    @Test
    fun `Location create defaults to null name and zero zoom`() {
        val location = locationMessage()

        assertNull(location.name)
        assertEquals(0, location.zoom)
    }

    @Test
    fun `Location create sets name and zoom when provided`() {
        val location = WireMessage.Location.create(
            conversationId = conversationId,
            latitude = 1.0f,
            longitude = 2.0f,
            name = "Wire HQ",
            zoom = 5
        )

        assertEquals("Wire HQ", location.name)
        assertEquals(5, location.zoom)
    }

    @Test
    fun `Location create defaults to null expiresAfterMillis`() {
        val location = locationMessage()

        assertNull(location.expiresAfterMillis)
    }

    @Test
    fun `Location create sets expiresAfterMillis when provided`() {
        val location = locationMessage(expiresAfterMillis = 8_000L)

        assertEquals(8_000L, location.expiresAfterMillis)
    }

    @Test
    fun `Location implements Ephemeral and Replyable`() {
        val location = locationMessage()

        assertTrue(location is WireMessage.Ephemeral)
        assertTrue(location is WireMessage.Replyable)
    }

    @Test
    fun `Reaction create succeeds for a non-ephemeral message`() {
        val original = textMessage()

        val reaction = WireMessage.Reaction.create(
            originalMessage = original,
            emojiSet = setOf("👍")
        )

        assertEquals(original.id.toString(), reaction.messageId)
        assertEquals(original.conversationId, reaction.conversationId)
        assertEquals(setOf("👍"), reaction.emojiSet)
    }

    @Test
    fun `Reaction create succeeds with empty emoji set`() {
        val original = textMessage()

        val reaction = WireMessage.Reaction.create(originalMessage = original)

        assertTrue(reaction.emojiSet.isEmpty())
    }

    @Test
    fun `Reaction create throws when original message is expiring ephemeral`() {
        val original = textMessage(expiresAfterMillis = 5_000L)

        assertThrows<IllegalArgumentException> {
            WireMessage.Reaction.create(
                originalMessage = original,
                emojiSet = setOf("❤️")
            )
        }
    }

    @Test
    fun `Reaction create succeeds for ephemeral message without expiry`() {
        val original = textMessage(expiresAfterMillis = null)

        assertDoesNotThrow {
            WireMessage.Reaction.create(
                originalMessage = original,
                emojiSet = setOf("😊")
            )
        }
    }

    @Test
    fun `Reaction create supports multiple emojis`() {
        val original = textMessage()
        val emojis = setOf("👍", "❤️", "😂")

        val reaction = WireMessage.Reaction.create(
            originalMessage = original,
            emojiSet = emojis
        )

        assertEquals(emojis, reaction.emojiSet)
    }

    @Test
    fun `Reaction create assigns a random UUID`() {
        val original = textMessage()
        val first = WireMessage.Reaction.create(originalMessage = original)
        val second = WireMessage.Reaction.create(originalMessage = original)

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `Deleted create sets correct messageId and conversationId`() {
        val targetId = UUID.randomUUID()
        val deleted = WireMessage.Deleted.create(
            conversationId = conversationId,
            messageId = targetId
        )

        assertEquals(targetId, deleted.messageId)
        assertEquals(conversationId, deleted.conversationId)
    }

    @Test
    fun `Deleted create assigns a random UUID`() {
        val targetId = UUID.randomUUID()
        val first = WireMessage.Deleted.create(
            conversationId = conversationId,
            messageId = targetId
        )
        val second = WireMessage.Deleted.create(
            conversationId = conversationId,
            messageId = targetId
        )

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `Receipt create sets correct type and messageIds`() {
        val msgIds = listOf("id-1", "id-2")
        val receipt = WireMessage.Receipt.create(
            conversationId = conversationId,
            type = WireMessage.Receipt.Type.READ,
            messages = msgIds
        )

        assertEquals(WireMessage.Receipt.Type.READ, receipt.type)
        assertEquals(msgIds, receipt.messageIds)
    }

    @Test
    fun `Receipt create defaults to empty messageIds`() {
        val receipt = WireMessage.Receipt.create(
            conversationId = conversationId,
            type = WireMessage.Receipt.Type.DELIVERED
        )

        assertTrue(receipt.messageIds.isEmpty())
    }

    @Test
    fun `Receipt create assigns a random UUID`() {
        val first = WireMessage.Receipt.create(
            conversationId = conversationId,
            type = WireMessage.Receipt.Type.READ
        )
        val second = WireMessage.Receipt.create(
            conversationId = conversationId,
            type = WireMessage.Receipt.Type.READ
        )

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `TextEdited create sets replacingMessageId and new content`() {
        val originalId = UUID.randomUUID()
        val edited = WireMessage.TextEdited.create(
            replacingMessageId = originalId,
            conversationId = conversationId,
            text = "Edited text"
        )

        assertEquals(originalId, edited.replacingMessageId)
        assertEquals("Edited text", edited.newContent)
    }

    @Test
    fun `TextEdited create defaults to empty mentions`() {
        val edited = WireMessage.TextEdited.create(
            replacingMessageId = UUID.randomUUID(),
            conversationId = conversationId,
            text = "Edited"
        )

        assertTrue(edited.newMentions.isEmpty())
    }

    @Test
    fun `TextEdited create sets mentions when provided`() {
        val mention = WireMessage.Mention(userId = QualifiedId(UUID.randomUUID(), "wire.com"))
        val edited = WireMessage.TextEdited.create(
            replacingMessageId = UUID.randomUUID(),
            conversationId = conversationId,
            text = "Edited @user",
            mentions = listOf(mention)
        )

        assertEquals(listOf(mention), edited.newMentions)
    }

    @Test
    fun `TextEdited create assigns a random UUID`() {
        val replacingId = UUID.randomUUID()
        val first = WireMessage.TextEdited.create(
            replacingMessageId = replacingId,
            conversationId = conversationId,
            text = "A"
        )
        val second = WireMessage.TextEdited.create(
            replacingMessageId = replacingId,
            conversationId = conversationId,
            text = "A"
        )

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `Composite create contains text item followed by buttons`() {
        val buttons = listOf(
            WireMessage.Button(text = "Yes"),
            WireMessage.Button(text = "No")
        )
        val composite = WireMessage.Composite.create(
            conversationId = conversationId,
            text = "Choose one",
            buttonList = buttons
        )

        val textItem = composite.items.first()
        assertTrue(textItem is WireMessage.Text)
        assertEquals("Choose one", (textItem as WireMessage.Text).text)
        assertEquals(3, composite.items.size)
    }

    @Test
    fun `Composite create assigns a random UUID`() {
        val buttons = listOf(WireMessage.Button(text = "Ok"))
        val first = WireMessage.Composite.create(
            conversationId = conversationId,
            text = "A",
            buttonList = buttons
        )
        val second = WireMessage.Composite.create(
            conversationId = conversationId,
            text = "A",
            buttonList = buttons
        )

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `CompositeEdited create sets replacingMessageId and new items`() {
        val replacingId = UUID.randomUUID()
        val buttons = listOf(WireMessage.Button(text = "Maybe"))
        val edited = WireMessage.CompositeEdited.create(
            replacingMessageId = replacingId,
            conversationId = conversationId,
            text = "Updated question",
            buttonList = buttons
        )

        assertEquals(replacingId, edited.replacingMessageId)
        assertEquals(2, edited.newItems.size)
        assertTrue(edited.newItems.first() is WireMessage.Text)
    }

    @Test
    fun `ButtonActionConfirmation create sets referencedMessageId and buttonId`() {
        val refId = UUID.randomUUID().toString()
        val confirmation = WireMessage.ButtonActionConfirmation.create(
            conversationId = conversationId,
            referencedMessageId = refId,
            buttonId = "btn-1"
        )

        assertEquals(refId, confirmation.referencedMessageId)
        assertEquals("btn-1", confirmation.buttonId)
    }

    @Test
    fun `ButtonActionConfirmation create allows null buttonId`() {
        val confirmation = WireMessage.ButtonActionConfirmation.create(
            conversationId = conversationId,
            referencedMessageId = UUID.randomUUID().toString(),
            buttonId = null
        )

        assertNull(confirmation.buttonId)
    }

    @Test
    fun `Button defaults to a random UUID string as id`() {
        val first = WireMessage.Button(text = "Click me")
        val second = WireMessage.Button(text = "Click me")

        assertNotEquals(first.id, second.id)
    }

    @Test
    fun `Button sets custom id when provided`() {
        val button = WireMessage.Button(text = "Submit", id = "custom-id")

        assertEquals("custom-id", button.id)
    }

    @Test
    fun `Ignored throws on id access`() {
        assertThrows<Exception> { WireMessage.Ignored.id }
    }

    @Test
    fun `Ignored throws on conversationId access`() {
        assertThrows<Exception> { WireMessage.Ignored.conversationId }
    }

    @Test
    fun `Ignored throws on sender access`() {
        assertThrows<Exception> { WireMessage.Ignored.sender }
    }

    @Test
    fun `Unknown throws on id access`() {
        assertThrows<Exception> { WireMessage.Unknown.id }
    }

    @Test
    fun `Unknown throws on conversationId access`() {
        assertThrows<Exception> { WireMessage.Unknown.conversationId }
    }

    @Test
    fun `Unknown throws on sender access`() {
        assertThrows<Exception> { WireMessage.Unknown.sender }
    }
}
