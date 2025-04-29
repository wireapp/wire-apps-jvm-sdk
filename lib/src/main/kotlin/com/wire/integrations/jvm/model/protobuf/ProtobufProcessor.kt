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
import com.wire.integrations.protobuf.messages.Messages.Composite
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.util.UUID

object ProtobufProcessor {
    @Suppress("ReturnCount", "LongMethod")
    fun processGenericMessage(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage =
        when {
            genericMessage.hasText() -> unpackText(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasAsset() -> unpackAsset(
                genericMessage = genericMessage,
                conversationId = conversationId
            )

            genericMessage.hasComposite() -> unpackComposite(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasButtonAction() -> unpackButtonAction(
                genericMessage = genericMessage
            )

            genericMessage.hasButtonActionConfirmation() -> unpackButtonActionConfirmation(
                genericMessage = genericMessage
            )

            else -> WireMessage.Unknown
        }

    private fun unpackText(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.Text {
        val text = genericMessage.text

        return WireMessage.Text(
            conversationId = conversationId,
            sender = sender,
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

    private fun unpackAsset(
        genericMessage: GenericMessage,
        conversationId: QualifiedId
    ): WireMessage.Asset {
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
                assetId = uploadedAsset.assetId,
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

    private fun unpackComposite(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.Composite {
        val items = genericMessage.composite.itemsList
        val text = items.firstNotNullOfOrNull { item ->
            item.text
        }?.let {
            unpackText(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )
        }

        val buttonList = unpackButtonList(items)

        return WireMessage.Composite(
            textContent = text,
            buttonList = buttonList
        )
    }

    private fun unpackButtonList(
        compositeItemList: List<Composite.Item>
    ): List<WireMessage.Composite.Button> =
        compositeItemList.mapNotNull {
            it.button?.let { button ->
                WireMessage.Composite.Button(
                    text = button.text,
                    id = button.id,
                    isSelected = false
                )
            }
        }

    private fun unpackButtonAction(genericMessage: GenericMessage): WireMessage =
        WireMessage.ButtonAction(
            referencedMessageId = genericMessage.buttonAction.referenceMessageId,
            buttonId = genericMessage.buttonAction.buttonId
        )

    private fun unpackButtonActionConfirmation(genericMessage: GenericMessage): WireMessage =
        WireMessage.ButtonActionConfirmation(
            referencedMessageId = genericMessage.buttonActionConfirmation.referenceMessageId,
            buttonId = genericMessage.buttonActionConfirmation.buttonId
        )
}
