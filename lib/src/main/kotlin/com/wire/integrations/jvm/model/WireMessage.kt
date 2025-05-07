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

import com.wire.integrations.jvm.exception.WireException
import java.util.UUID

@Suppress("ArrayInDataClass")
sealed interface WireMessage {
    val id: UUID
    val conversationId: QualifiedId
    val sender: QualifiedId?

    sealed interface Item

    @JvmRecord
    data class Text @JvmOverloads constructor(
        override val id: UUID,
        override val conversationId: QualifiedId,
        override val sender: QualifiedId? = null,
        val text: String? = null,
        val quotedMessageId: UUID? = null,
        val quotedMessageSha256: ByteArray? = null,
        val mentions: List<Mention> = emptyList(),
        val linkPreviews: List<LinkPreview> = emptyList()
    ) : WireMessage, Item {
        @JvmRecord
        data class Mention @JvmOverloads constructor(
            val userId: QualifiedId? = null,
            val offset: Int = 0,
            val length: Int = 0
        )

        @JvmRecord
        data class LinkPreview @JvmOverloads constructor(
            val summary: String? = null,
            val title: String? = null,
            val url: String? = null,
            val urlOffset: Int = 0,
            val mimeType: String? = null,
            val name: String? = null,
            val size: Long = 0
        )

        companion object {
            /**
             * Creates a basic text message with minimal required parameters.
             *
             * Usage from Kotlin:
             * ```kotlin
             * val message = Text.create(conversationId, "Hello world")
             * ```
             *
             * Usage from Java:
             * ```java
             * Text message = Text.Companion.create(conversationId, "Hello world");
             * ```
             *
             * @param conversationId The qualified ID of the conversation
             * @param text The text content of the message
             * @return A new Text message with a random UUID
             */
            @JvmStatic
            fun create(
                conversationId: QualifiedId,
                text: String
            ): Text {
                return Text(
                    id = UUID.randomUUID(),
                    conversationId = conversationId,
                    text = text
                )
            }
        }
    }

    @JvmRecord
    data class Asset @JvmOverloads constructor(
        override val id: UUID,
        override val conversationId: QualifiedId,
        override val sender: QualifiedId? = null,
        val sizeInBytes: Long,
        val name: String? = null,
        val mimeType: String,
        val metadata: AssetMetadata? = null,
        val remoteData: AssetMetadata.RemoteData? = null
    ) : WireMessage {
        sealed class AssetMetadata {
            data class Image(val width: Int, val height: Int) : AssetMetadata()

            data class Video(
                val width: Int?,
                val height: Int?,
                val durationMs: Long?
            ) : AssetMetadata()

            data class Audio(
                val durationMs: Long?,
                val normalizedLoudness: ByteArray?
            ) : AssetMetadata()

            enum class MessageEncryptionAlgorithm { AES_CBC, AES_GCM }

            @JvmRecord
            data class RemoteData @JvmOverloads constructor(
                val otrKey: ByteArray,
                val sha256: ByteArray,
                val assetId: String,
                val assetToken: String? = null,
                val assetDomain: String,
                val encryptionAlgorithm: MessageEncryptionAlgorithm? = null
            )
        }
    }

    @JvmRecord
    data class Composite(
        override val id: UUID,
        override val conversationId: QualifiedId,
        override val sender: QualifiedId? = null,
        val items: List<Item>
    ) : WireMessage {
        @JvmRecord
        data class Button @JvmOverloads constructor(
            val text: String,
            val id: String = UUID.randomUUID().toString()
        ) : Item

        companion object {
            /**
             * Creates a Composite message with a single text first, followed by a list of buttons.
             *
             * Usage from Kotlin:
             * ```kotlin
             * val message = Composite.create(conversationId, "Hello world", buttonList)
             * ```
             *
             * Usage from Java:
             * ```java
             * Composite composite =
             *      Composite.Companion.create(conversationId, "Hello world", buttonList);
             * ```
             *
             * @param conversationId The qualified ID of the conversation
             * @param text The text content of the message
             * @param buttonList The list of buttons to be selected
             * @return A new Composite message
             */
            @JvmStatic
            fun create(
                conversationId: QualifiedId,
                text: String,
                buttonList: List<Button>
            ): Composite {
                val textItem = Text.create(
                    conversationId = conversationId,
                    text = text
                )

                return Composite(
                    id = UUID.randomUUID(),
                    conversationId = conversationId,
                    items = listOf(textItem) + buttonList
                )
            }
        }
    }

    /**
     * Notifies the author of a [Composite] message that a user has
     * selected one of its buttons.
     * @see Composite
     * @see ButtonActionConfirmation
     */
    @JvmRecord
    data class ButtonAction @JvmOverloads constructor(
        override val id: UUID,
        override val conversationId: QualifiedId,
        override val sender: QualifiedId? = null,
        /**
         * The ID of the original composite message.
         */
        val referencedMessageId: String,
        /**
         * ID of the button that was selected.
         */
        val buttonId: String
    ) : WireMessage

    /**
     * Message sent by the author of a [Composite] to
     * notify which button should be marked as selected.
     * For example, after we send [ButtonAction], the author might reply
     * with [ButtonActionConfirmation] to confirm that the button event was processed.
     * @see ButtonAction
     * @see Composite
     */
    @JvmRecord
    data class ButtonActionConfirmation @JvmOverloads constructor(
        override val id: UUID,
        override val conversationId: QualifiedId,
        override val sender: QualifiedId? = null,
        /**
         * ID fo the original composite message
         */
        val referencedMessageId: String,
        /**
         * ID of the selected button. Null if no button should be marked as selected.
         */
        val buttonId: String?
    ) : WireMessage

    @JvmRecord
    data class Knock @JvmOverloads constructor(
        override val id: UUID,
        override val conversationId: QualifiedId,
        override val sender: QualifiedId? = null,
        val hotKnock: Boolean
    ) : WireMessage

    @JvmRecord
    data class Location @JvmOverloads constructor(
        override val id: UUID,
        override val conversationId: QualifiedId,
        override val sender: QualifiedId? = null,
        val latitude: Float,
        val longitude: Float,
        val name: String? = null,
        val zoom: Int = 0
    ) : WireMessage

    data object Unknown : WireMessage {
        override val id: UUID
            get() = throw WireException.InvalidParameter("Unknown message, no ID")
        override val conversationId: QualifiedId
            get() = throw WireException.InvalidParameter("Unknown message, no conversation")
        override val sender: QualifiedId?
            get() = throw WireException.InvalidParameter("Unknown message, no sender")
    }
}
