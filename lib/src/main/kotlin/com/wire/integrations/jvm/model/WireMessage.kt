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

package com.wire.integrations.jvm.model

import java.util.UUID

sealed interface WireMessage {
    @JvmRecord
    data class Text(
        val text: String? = null,
        val quotedMessageId: UUID? = null,
        val quotedMessageSha256: ByteArray? = null,
        val mentions: ArrayList<Mention> = ArrayList<Mention>()
    ) : WireMessage {
        fun addMention(
            userId: QualifiedId?,
            offset: Int,
            len: Int
        ) {
            val mention = Mention()
            mention.userId = userId
            mention.offset = offset
            mention.length = len

            mentions.add(mention)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Text

            if (text != other.text) return false
            if (quotedMessageId != other.quotedMessageId) return false
            if (!quotedMessageSha256.contentEquals(other.quotedMessageSha256)) return false
            if (mentions != other.mentions) return false

            return true
        }

        override fun hashCode(): Int {
            var result = text.hashCode()
            result = 31 * result + (quotedMessageId?.hashCode() ?: 0)
            result = 31 * result + quotedMessageSha256.contentHashCode()
            result = 31 * result + mentions.hashCode()
            return result
        }

        class Mention {
            var userId: QualifiedId? = null
            var offset: Int = 0
            var length: Int = 0
        }
    }

    @JvmRecord
    data class LinkPreview(
        val summary: String? = null,
        val title: String? = null,
        val url: String? = null,
        val text: String? = null,
        val urlOffset: Int = 0,
        val mimeType: String? = null,
        val name: String? = null,
        val size: Long = 0
    ) : WireMessage
}
