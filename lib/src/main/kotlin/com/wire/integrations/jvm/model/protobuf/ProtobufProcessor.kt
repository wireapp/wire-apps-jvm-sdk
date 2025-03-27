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

package com.wire.integrations.jvm.model.protobuf
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.util.UUID

internal interface ProtobufProcessor {
    fun processGenericMessage(genericMessage: GenericMessage)
}

internal class ProtobufProcessorImpl(
    private val wireEventsHandler: WireEventsHandler
) : ProtobufProcessor {
    override fun processGenericMessage(genericMessage: GenericMessage) {
        if (genericMessage.hasText()) {
            val text = genericMessage.text

            if (text.linkPreviewList.isNotEmpty()) {
                handleLinkPreview(text = text)
            }

            if (text.hasContent()) {
                val message = fromText(text = text)
                wireEventsHandler.onNewMLSMessage(
                    wireMessage = message
                )
            }
        }
    }

    private fun fromText(text: Messages.Text): WireMessage {
        val wireMessage = WireMessage.Text(
            text = text.content,
            quotedMessageId =
                if (text.hasQuote()) UUID.fromString(text.quote.quotedMessageId) else null
        )

        for (mention in text.mentionsList) {
            val userMentionedId = QualifiedId(
                UUID.fromString(mention.qualifiedUserId.getId()),
                mention.qualifiedUserId.getDomain()
            )
            wireMessage.addMention(userMentionedId, mention.start, mention.length)
        }
        return wireMessage
    }

    private fun handleLinkPreview(text: Messages.Text) {
        text.linkPreviewList.forEach { link ->
            if (text.hasContent()) {
                val linkPreview = WireMessage.LinkPreview(
                    summary = link.summary,
                    title = link.title,
                    url = link.url,
                    urlOffset = link.urlOffset,
                    size = if (link.hasImage()) link.image.original.size else 0,
                    mimeType = if (link.hasImage()) link.image.original.mimeType else null,
                    text = text.content
                )

                wireEventsHandler.onNewMLSMessage(
                    wireMessage = linkPreview
                )
            }
        }
    }
}
