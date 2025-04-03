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

import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.util.UUID

object ProtobufProcessor {
    fun processGenericMessage(
        genericMessage: GenericMessage,
        conversationId: QualifiedId
    ): WireMessage {
        if (genericMessage.hasText()) {
            val text = genericMessage.text

            return WireMessage.Text(
                conversationId = conversationId,
                id = UUID.fromString(genericMessage.messageId),
                text = text.content,
                quotedMessageId =
                    if (text.hasQuote()) UUID.fromString(text.quote.quotedMessageId) else null,
                linkPreviews = text
                    .linkPreviewList
                    .mapNotNull {
                        MessageLinkPreviewMapper.fromProtobuf(it)
                    },
                mentions = text
                    .mentionsList
                    .mapNotNull {
                        MessageMentionMapper.fromProtobuf(it)
                    }
            )
        }

        return WireMessage.Unknown
    }
}
