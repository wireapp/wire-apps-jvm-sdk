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
        val conversationId: QualifiedId,
        val sender: QualifiedId? = null,
        val id: UUID,
        val text: String? = null,
        val quotedMessageId: UUID? = null,
        val quotedMessageSha256: ByteArray? = null,
        val mentions: List<Mention> = emptyList(),
        val linkPreviews: List<LinkPreview> = emptyList()
    ) : WireMessage {
        @JvmRecord
        data class Mention(
            val userId: QualifiedId? = null,
            val offset: Int = 0,
            val length: Int = 0
        )

        @JvmRecord
        data class LinkPreview(
            val summary: String? = null,
            val title: String? = null,
            val url: String? = null,
            val urlOffset: Int = 0,
            val mimeType: String? = null,
            val name: String? = null,
            val size: Long = 0
        )

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Text

            if (text != other.text) return false
            if (quotedMessageId != other.quotedMessageId) return false
            if (quotedMessageSha256 != null) {
                if (other.quotedMessageSha256 == null) return false
                if (!quotedMessageSha256.contentEquals(other.quotedMessageSha256)) return false
            } else if (other.quotedMessageSha256 != null) {
                return false
            }
            if (mentions != other.mentions) return false
            if (linkPreviews != other.linkPreviews) return false

            return true
        }

        override fun hashCode(): Int {
            var result = text?.hashCode() ?: 0
            result = 31 * result + (quotedMessageId?.hashCode() ?: 0)
            result = 31 * result + (quotedMessageSha256?.contentHashCode() ?: 0)
            result = 31 * result + mentions.hashCode()
            result = 31 * result + linkPreviews.hashCode()
            return result
        }

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
                    conversationId = conversationId,
                    id = UUID.randomUUID(),
                    text = text
                )
            }
        }
    }

    @JvmRecord
    data class Asset(
        val conversationId: QualifiedId,
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
            ) : AssetMetadata() {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other == null || this::class != other::class) return false

                    other as Audio

                    if (durationMs != other.durationMs) return false
                    if (normalizedLoudness != null) {
                        if (other.normalizedLoudness == null) {
                            return false
                        }
                        if (!normalizedLoudness.contentEquals(other.normalizedLoudness)) {
                            return false
                        }
                    } else if (other.normalizedLoudness != null) {
                        return false
                    }

                    return true
                }

                override fun hashCode(): Int {
                    var result = durationMs?.hashCode() ?: 0
                    result = 31 * result + (normalizedLoudness?.contentHashCode() ?: 0)
                    return result
                }
            }

            enum class MessageEncryptionAlgorithm { AES_CBC, AES_GCM }

            data class RemoteData(
                val otrKey: ByteArray,
                val sha256: ByteArray,
                val assetId: String,
                val assetToken: String?,
                val assetDomain: String,
                val encryptionAlgorithm: MessageEncryptionAlgorithm?
            ) {
                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other == null || this::class != other::class) return false

                    other as RemoteData

                    if (!otrKey.contentEquals(other.otrKey)) return false
                    if (!sha256.contentEquals(other.sha256)) return false
                    if (assetId != other.assetId) return false
                    if (assetToken != other.assetToken) return false
                    if (assetDomain != other.assetDomain) return false
                    if (encryptionAlgorithm != other.encryptionAlgorithm) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = otrKey.contentHashCode()
                    result = 31 * result + sha256.contentHashCode()
                    result = 31 * result + assetId.hashCode()
                    result = 31 * result + (assetToken?.hashCode() ?: 0)
                    result = 31 * result + assetDomain.hashCode()
                    result = 31 * result + (encryptionAlgorithm?.hashCode() ?: 0)
                    return result
                }
            }
        }
    }

    @JvmRecord
    data class Composite(
        val textContent: Text?,
        val buttonList: List<Button>
    ) : WireMessage {
        data class Button(
            val text: String,
            val id: String,
            val isSelected: Boolean
        ) {
            companion object {
                /**
                 * Creates a Composite Button message with minimal required parameters.
                 *
                 * Usage from Kotlin:
                 * ```kotlin
                 * val button = Composite.Button.create(conversationId, "Hello world")
                 * ```
                 *
                 * Usage from Java:
                 * ```java
                 * Composite.Button button =
                 *      Composite.Button.Companion.create(conversationId, "Hello world");
                 * ```
                 *
                 * @param text The text content of the message
                 * @param isSelected Whether the button is selected.
                 * @param id Random generated UUID or received ID value.
                 * @return A new Composite.Button message with a random UUID
                 */
                @JvmStatic
                fun create(
                    text: String,
                    isSelected: Boolean,
                    id: String? = null
                ): Button {
                    return Button(
                        text = text,
                        isSelected = isSelected,
                        id = id ?: UUID.randomUUID().toString()
                    )
                }
            }
        }

        companion object {
            /**
             * Creates a Composite message.
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
                return Composite(
                    textContent = Text.create(
                        conversationId = conversationId,
                        text = text
                    ),
                    buttonList = buttonList
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
    data class ButtonAction(
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
    data class ButtonActionConfirmation(
        /**
         * ID fo the original composite message
         */
        val referencedMessageId: String,
        /**
         * ID of the selected button. Null if no button should be marked as selected.
         */
        val buttonId: String?
    ) : WireMessage

    data object Unknown : WireMessage
}
