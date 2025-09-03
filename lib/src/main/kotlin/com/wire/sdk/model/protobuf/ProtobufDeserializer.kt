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

package com.wire.sdk.model.protobuf

import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.utils.obfuscateId
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.Composite
import com.wire.integrations.protobuf.messages.Messages.Confirmation
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.util.UUID
import kotlin.time.Instant
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

    @Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod")
    fun processGenericMessage(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId,
        timestamp: Instant
    ): WireMessage =
        when {
            genericMessage.hasText() -> unpackText(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender,
                timestamp = timestamp
            )

            genericMessage.hasAsset() -> unpackAsset(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender,
                timestamp = timestamp
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
                sender = sender,
                timestamp = timestamp
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

            genericMessage.hasEdited() -> unpackEdited(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasEphemeral() -> unpackEphemeral(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender,
                timestamp = timestamp
            )

            genericMessage.hasReaction() -> unpackReaction(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasInCallEmoji() -> unpackInCallEmoji(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            genericMessage.hasInCallHandRaise() -> unpackInCallHandRaise(
                genericMessage = genericMessage,
                conversationId = conversationId,
                sender = sender
            )

            else -> WireMessage.Unknown
        }

    private fun unpackText(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId,
        timestamp: Instant,
        expiresAfterMillis: Long? = null
    ): WireMessage.Text {
        val text = genericMessage.text

        return WireMessage.Text(
            conversationId = conversationId,
            sender = sender,
            id = UUID.fromString(genericMessage.messageId),
            text = text.content,
            timestamp = timestamp,
            quotedMessageId =
                if (text.hasQuote()) UUID.fromString(text.quote.quotedMessageId) else null,
            quotedMessageSha256 =
                if (text.hasQuote()) text.quote.quotedMessageSha256.toByteArray() else null,
            linkPreviews = text
                .linkPreviewList
                .mapNotNull {
                    MessageLinkPreviewMapper.fromProtobuf(it)
                },
            mentions = text
                .mentionsList
                .mapNotNull {
                    MessageMentionMapper.fromProtobuf(it)
                },
            expiresAfterMillis = expiresAfterMillis
        )
    }

    private fun unpackAsset(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId,
        timestamp: Instant,
        expiresAfterMillis: Long? = null
    ): WireMessage.Asset {
        val asset = genericMessage.asset
        val original = if (asset.hasOriginal()) asset.original else null

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

            WireMessage.Asset.RemoteData(
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
            remoteData = remoteData,
            timestamp = timestamp,
            expiresAfterMillis = expiresAfterMillis
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
                    WireMessage.Button(
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
        sender: QualifiedId,
        expiresAfterMillis: Long? = null
    ): WireMessage.Knock =
        WireMessage.Knock(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            hotKnock = genericMessage.knock.hotKnock,
            expiresAfterMillis = expiresAfterMillis
        )

    private fun unpackLocation(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId,
        timestamp: Instant,
        expiresAfterMillis: Long? = null
    ): WireMessage.Location =
        WireMessage.Location(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            latitude = genericMessage.location.latitude,
            longitude = genericMessage.location.longitude,
            name = genericMessage.location.name,
            zoom = genericMessage.location.zoom,
            timestamp = timestamp,
            expiresAfterMillis = expiresAfterMillis
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

    private fun unpackEdited(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage {
        val replacingMessageId = genericMessage.edited.replacingMessageId

        return when {
            genericMessage.edited.hasText() -> {
                val mentions = genericMessage.edited.text.mentionsList.mapNotNull {
                    MessageMentionMapper.fromProtobuf(it)
                }
                WireMessage.TextEdited(
                    id = UUID.fromString(genericMessage.messageId),
                    replacingMessageId = UUID.fromString(replacingMessageId),
                    conversationId = conversationId,
                    sender = sender,
                    newContent = genericMessage.text.content,
                    newMentions = mentions
                )
            }

            genericMessage.edited.hasComposite() -> {
                WireMessage.CompositeEdited(
                    id = UUID.fromString(genericMessage.messageId),
                    replacingMessageId = UUID.fromString(replacingMessageId),
                    conversationId = conversationId,
                    sender = sender,
                    newItems = unpackItemList(
                        conversationId = conversationId,
                        compositeItemList = genericMessage.edited.composite.itemsList
                    )
                )
            }

            else -> {
                logger.warn(
                    "Edit content is unexpected. " +
                        "Message UUID = ${genericMessage.messageId.obfuscateId()}"
                )
                WireMessage.Ignored
            }
        }
    }

    @Suppress("LongMethod")
    private fun unpackEphemeral(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId,
        timestamp: Instant
    ): WireMessage {
        val ephemeralMessage = genericMessage.ephemeral

        val builtMessage = GenericMessage
            .newBuilder()
            .setMessageId(genericMessage.messageId)

        return when {
            ephemeralMessage.hasText() -> {
                val textContent = ephemeralMessage.text
                val textMessage = Messages.Text.newBuilder()
                    .setContent(textContent.content)
                    .addAllMentions(textContent.mentionsList)
                    .addAllLinkPreview(textContent.linkPreviewList)
                    .setExpectsReadConfirmation(textContent.expectsReadConfirmation)
                    .setLegalHoldStatus(textContent.legalHoldStatus)
                    .build()

                unpackText(
                    genericMessage = builtMessage
                        .setText(textMessage)
                        .build(),
                    conversationId = conversationId,
                    sender = sender,
                    timestamp = timestamp,
                    expiresAfterMillis = ephemeralMessage.expireAfterMillis
                )
            }

            ephemeralMessage.hasAsset() -> {
                val assetContent = ephemeralMessage.asset
                val assetMessage = Messages.Asset.newBuilder()
                    .setOriginal(assetContent.original)
                    .setUploaded(assetContent.uploaded)
                    .setNotUploaded(assetContent.notUploaded)
                    .setExpectsReadConfirmation(assetContent.expectsReadConfirmation)

                unpackAsset(
                    genericMessage = builtMessage
                        .setAsset(assetMessage)
                        .build(),
                    conversationId = conversationId,
                    sender = sender,
                    timestamp = timestamp,
                    expiresAfterMillis = ephemeralMessage.expireAfterMillis
                )
            }

            ephemeralMessage.hasKnock() -> {
                WireMessage.Knock.create(
                    conversationId = conversationId,
                    hotKnock = ephemeralMessage.knock.hotKnock,
                    expiresAfterMillis = ephemeralMessage.expireAfterMillis
                )
            }

            ephemeralMessage.hasLocation() -> {
                val locationContent = ephemeralMessage.location
                val locationMessage = Messages.Location.newBuilder()
                    .setLatitude(locationContent.latitude)
                    .setLongitude(locationContent.longitude)
                    .setName(locationContent.name)
                    .setZoom(locationContent.zoom)
                    .build()

                unpackLocation(
                    genericMessage = builtMessage
                        .setLocation(locationMessage)
                        .build(),
                    conversationId = conversationId,
                    sender = sender,
                    timestamp = timestamp,
                    expiresAfterMillis = ephemeralMessage.expireAfterMillis
                )
            }

            ephemeralMessage.hasImage() -> WireMessage.Ignored
            else -> WireMessage.Ignored
        }
    }

    private fun unpackReaction(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage {
        val emoji = genericMessage.reaction.emoji
        val emojiSet = emoji?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        return WireMessage.Reaction(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            messageId = genericMessage.reaction.messageId,
            emojiSet = emojiSet
        )
    }

    private fun unpackInCallEmoji(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.InCallEmoji =
        WireMessage.InCallEmoji(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            // Map of emoji to senderId
            emojis = genericMessage.inCallEmoji.emojisMap
                .mapNotNull {
                    val key = it.key ?: return@mapNotNull null
                    val value = it.value ?: return@mapNotNull null
                    key to value
                }
                .associateBy({ it.first }, { it.second })
        )

    private fun unpackInCallHandRaise(
        genericMessage: GenericMessage,
        conversationId: QualifiedId,
        sender: QualifiedId
    ): WireMessage.InCallHandRaise =
        WireMessage.InCallHandRaise(
            id = UUID.fromString(genericMessage.messageId),
            conversationId = conversationId,
            sender = sender,
            isHandUp = genericMessage.inCallHandRaise.isHandUp
        )
}
