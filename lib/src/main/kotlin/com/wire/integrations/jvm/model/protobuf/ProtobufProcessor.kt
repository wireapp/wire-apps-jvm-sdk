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
    @Suppress("ReturnCount", "LongMethod")
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

        if (genericMessage.hasAsset()) {
            val asset = genericMessage.asset
            val original = asset.original

            val metadata: WireMessage.Asset.AssetMetadata? = when {
                original?.hasImage() == true -> {
                    val image = original.image
                    WireMessage.Asset.AssetMetadata.Image(
                        width = image.width,
                        height = image.height
                    )
                }
                original?.hasAudio() == true -> {
                    val audio = original.audio
                    WireMessage.Asset.AssetMetadata.Audio(
                        durationMs = audio.durationInMillis,
                        normalizedLoudness = audio.normalizedLoudness?.toByteArray()
                    )
                }
                original?.hasVideo() == true -> {
                    val video = original.video
                    WireMessage.Asset.AssetMetadata.Video(
                        width = video.width,
                        height = video.height,
                        durationMs = video.durationInMillis
                    )
                }

                else -> null
            }

            val remoteData = if (asset.hasUploaded()) {
                val uploadedAsset = asset.uploaded

                WireMessage.Asset.AssetMetadata.RemoteData(
                    otrKey = uploadedAsset.otrKey.toByteArray(),
                    sha256 = uploadedAsset.sha256.toByteArray(),
                    assetId = uploadedAsset.assetId ?: "",
                    assetDomain = uploadedAsset.assetDomain,
                    assetToken = uploadedAsset.assetToken,
                    encryptionAlgorithm = EncryptionAlgorithmMapper.fromProtobufModel(
                        encryptionAlgorithm = uploadedAsset.encryption
                    )
                )
            } else {
                null
            }

            return WireMessage.Asset(
                conversationId = conversationId,
                sizeInBytes = original?.size ?: 0,
                name = original?.name,
                mimeType = original?.mimeType ?: "*/*",
                metadata = metadata,
                remoteData = remoteData
            )
        }

        return WireMessage.Unknown
    }
}
