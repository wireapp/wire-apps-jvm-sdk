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
import com.wire.integrations.protobuf.messages.Messages.Confirmation
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.util.UUID
import org.slf4j.LoggerFactory

/**
 * Object class mapper for mapping [GenericMessage] (Protobuf) to [WireMessage] returning
 * a WireMessage.
 *
 * To be used when receiving a message from [EventsRouter]
 */
@Suppress("TooManyFunctions")
object ProtobufDeserializer {
    private val logger = LoggerFactory.getLogger(this::class.java)

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
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasComposite() -> unpackComposite(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasButtonAction() -> unpackButtonAction(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasButtonActionConfirmation() -> unpackButtonActionConfirmation(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasKnock() -> unpackKnock(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasLocation() -> unpackLocation(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasDeleted() -> unpackDeletedMessage(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasConfirmation() -> unpackReceipt(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
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
        conversationId: QualifiedId,
        sender: QualifiedId
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
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
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

        val itemList = unpackItemList(conversationId, items)

        return WireMessage.Composite(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            items = itemList
        )
    }

    private fun unpackItemList(
        conversationId: QualifiedId,
        compositeItemList: List<Composite.Item>
    ): List<WireMessage.Item> =
        compositeItemList.mapNotNull {
            if (it.hasText()) {
                it.text?.let { text ->
                    WireMessage.Text.create(conversationId, text.content)
                }
            } else {
                it.button?.let { button ->
                    WireMessage.Composite.Button(
                        text = button.text,
                        id = button.id
                    )
                }
            }
        }

    private fun unpackButtonAction(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.ButtonAction =
        WireMessage.ButtonAction(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            referencedMessageId = genericMessage.buttonAction.referenceMessageId,
            buttonId = genericMessage.buttonAction.buttonId
        )

    private fun unpackButtonActionConfirmation(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.ButtonActionConfirmation =
        WireMessage.ButtonActionConfirmation(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            referencedMessageId = genericMessage.buttonActionConfirmation.referenceMessageId,
            buttonId = genericMessage.buttonActionConfirmation.buttonId
        )

    private fun unpackKnock(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.Knock =
        WireMessage.Knock(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            hotKnock = genericMessage.knock.hotKnock
        )

    private fun unpackLocation(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.Location =
        WireMessage.Location(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            latitude = genericMessage.location.latitude,
            longitude = genericMessage.location.longitude,
            name = genericMessage.location.name,
            zoom = genericMessage.location.zoom
        )

    private fun unpackDeletedMessage(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.Deleted =
        WireMessage.Deleted(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            messageId = genericMessage.deleted.messageId
        )

    private fun unpackReceipt(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage =
        when (val type = genericMessage.confirmation.type) {
            Confirmation.Type.DELIVERED -> WireMessage.Receipt.Type.DELIVERED
            Confirmation.Type.READ -> WireMessage.Receipt.Type.READ
            else -> {
                logger.warn("Unrecognised receipt type received = ${type.number}:${type.name}")
                null
            }
        }?.let { type ->
            WireMessage.Receipt(
                id = UUID.fromString(genericMessage.messageId),
                conversationId = conversationId,
                sender = sender,
                type = type,
                messageIds =
                    listOf(genericMessage.confirmation.firstMessageId) +
                        genericMessage.confirmation.moreMessageIdsList
            )
        } ?: WireMessage.Ignored
}
