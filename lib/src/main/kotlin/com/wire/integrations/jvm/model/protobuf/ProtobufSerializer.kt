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

import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.ButtonAction
import com.wire.integrations.protobuf.messages.Messages.ButtonActionConfirmation
import com.wire.integrations.protobuf.messages.Messages.Composite
import com.wire.integrations.protobuf.messages.Messages.Confirmation
import com.wire.integrations.protobuf.messages.Messages.Ephemeral
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import com.wire.integrations.protobuf.messages.Messages.InCallEmoji
import com.wire.integrations.protobuf.messages.Messages.InCallHandRaise
import com.wire.integrations.protobuf.messages.Messages.Knock
import com.wire.integrations.protobuf.messages.Messages.Location
import com.wire.integrations.protobuf.messages.Messages.MessageDelete
import com.wire.integrations.protobuf.messages.Messages.MessageEdit
import com.wire.integrations.protobuf.messages.Messages.Reaction

/**
 * Object class mapper for mapping [WireMessage] to [GenericMessage] (Protobuf) returning
 * a ByteArray.
 *
 * To be used when sending a message from [WireApplicationManager]
 * before reaching [CoreCryptoClient]
 */
@Suppress("TooManyFunctions", "CyclomaticComplexMethod")
object ProtobufSerializer {
    fun toGenericMessageByteArray(wireMessage: WireMessage): ByteArray {
        val genericMessage = GenericMessage
            .newBuilder()
            .setMessageId(wireMessage.id.toString())

        val builtMessage = when (wireMessage) {
            is WireMessage.Text -> packTextMessage(wireMessage, genericMessage)
            is WireMessage.Asset -> packAsset(wireMessage, genericMessage)
            is WireMessage.Composite -> packComposite(wireMessage, genericMessage)
            is WireMessage.ButtonAction -> packButtonAction(wireMessage, genericMessage)
            is WireMessage.ButtonActionConfirmation ->
                packButtonActionConfirmation(wireMessage, genericMessage)
            is WireMessage.Knock -> packKnock(wireMessage, genericMessage)
            is WireMessage.Location -> packLocation(wireMessage, genericMessage)
            is WireMessage.Deleted -> packDeleted(wireMessage, genericMessage)
            is WireMessage.Receipt -> packReceipt(wireMessage, genericMessage)
            is WireMessage.TextEdited -> packTextEdited(wireMessage, genericMessage)
            is WireMessage.CompositeEdited -> packCompositeEdited(wireMessage, genericMessage)
            is WireMessage.Reaction -> packReaction(wireMessage, genericMessage)
            is WireMessage.InCallEmoji -> packInCallEmoji(wireMessage, genericMessage)
            is WireMessage.InCallHandRaise -> packInCallHandRaise(wireMessage, genericMessage)

            is WireMessage.Ignored,
            is WireMessage.Unknown -> throw WireException.CryptographicSystemError(
                "Unexpected message content type: $wireMessage"
            )
        }

        return builtMessage
            .build()
            .toByteArray()
    }

    private fun packText(wireMessage: WireMessage.Text) =
        Messages.Text.newBuilder()
            .setContent(wireMessage.text)
            .apply {
                if (
                    wireMessage.quotedMessageId != null && wireMessage.quotedMessageSha256 != null
                ) {
                    setQuote(
                        Messages.Quote.newBuilder()
                            .setQuotedMessageId(wireMessage.quotedMessageId.toString())
                            .setQuotedMessageSha256(
                                ByteString.copyFrom(wireMessage.quotedMessageSha256)
                            )
                            .build()
                    )
                }
            }
            .addAllMentions(wireMessage.mentions.map { MessageMentionMapper.toProtobuf(it) })
            .addAllLinkPreview(
                wireMessage.linkPreviews.map { MessageLinkPreviewMapper.toProtobuf(it) }
            )
            .setExpectsReadConfirmation(false)
            .setLegalHoldStatus(Messages.LegalHoldStatus.DISABLED)
            .build()

    private fun packTextMessage(
        wireMessage: WireMessage.Text,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .apply {
                val text = packText(wireMessage)

                wireMessage.expiresAfterMillis?.let {
                    setEphemeral(
                        Ephemeral
                            .newBuilder()
                            .setText(text)
                            .setExpireAfterMillis(it)
                            .build()
                    )
                } ?: setText(text)
            }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun packAsset(
        wireMessage: WireMessage.Asset,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .apply {
                val asset = Messages.Asset.newBuilder()
                    .setOriginal(
                        Messages.Asset.Original.newBuilder()
                            .setMimeType(wireMessage.mimeType)
                            .setSize(wireMessage.sizeInBytes)
                            .setName(wireMessage.name)
                            .apply {
                                when (wireMessage.metadata) {
                                    is WireMessage.Asset.AssetMetadata.Image -> setImage(
                                        Messages.Asset.ImageMetaData.newBuilder()
                                            .setWidth(wireMessage.metadata.width)
                                            .setHeight(wireMessage.metadata.height)
                                            .build()
                                    )
                                    is WireMessage.Asset.AssetMetadata.Audio -> setAudio(
                                        Messages.Asset.AudioMetaData.newBuilder()
                                            .apply {
                                                wireMessage.metadata.durationMs?.let {
                                                    setDurationInMillis(it)
                                                }
                                                wireMessage.metadata.normalizedLoudness?.let {
                                                    setNormalizedLoudness(it.toByteString())
                                                }
                                            }
                                            .build()
                                    )
                                    is WireMessage.Asset.AssetMetadata.Video -> setVideo(
                                        Messages.Asset.VideoMetaData.newBuilder()
                                            .apply {
                                                wireMessage.metadata.width?.let {
                                                    setWidth(it)
                                                }
                                                wireMessage.metadata.height?.let {
                                                    setHeight(it)
                                                }
                                                wireMessage.metadata.durationMs?.let {
                                                    setDurationInMillis(it)
                                                }
                                            }
                                            .build()
                                    )
                                    else -> {}
                                }
                            }
                            .build()
                    )
                    .setUploaded(
                        Messages.Asset.RemoteData.newBuilder()
                            .setAssetId(wireMessage.remoteData?.assetId)
                            .setAssetDomain(wireMessage.remoteData?.assetDomain)
                            .setAssetToken(wireMessage.remoteData?.assetToken)
                            .setOtrKey(ByteString.copyFrom(wireMessage.remoteData?.otrKey))
                            .setSha256(ByteString.copyFrom(wireMessage.remoteData?.sha256))
                    )
                    .build()

                wireMessage.expiresAfterMillis?.let {
                    setEphemeral(
                        Ephemeral
                            .newBuilder()
                            .setAsset(asset)
                            .setExpireAfterMillis(it)
                            .build()
                    )
                } ?: setAsset(asset)
            }

    private fun packItemsList(itemsList: List<WireMessage.Item>): List<Composite.Item> =
        itemsList.map {
            when (it) {
                is WireMessage.Button -> {
                    val button = Composite.Item.newBuilder().buttonBuilder
                    button.id = it.id
                    button.text = it.text

                    Composite.Item.newBuilder().setButton(button).build()
                }

                is WireMessage.Text -> {
                    val text = packText(it)

                    Composite.Item.newBuilder().setText(text).build()
                }
            }
        }

    private fun packComposite(
        wireMessage: WireMessage.Composite,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder {
        val items: MutableList<Composite.Item> = mutableListOf()

        items.addAll(
            packItemsList(
                itemsList = wireMessage.items
            )
        )

        return genericMessage
            .setComposite(
                Composite
                    .newBuilder()
                    .addAllItems(items)
                    .build()
            )
    }

    private fun packButtonAction(
        wireMessage: WireMessage.ButtonAction,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setButtonAction(
                ButtonAction
                    .newBuilder()
                    .setButtonId(wireMessage.buttonId)
                    .setReferenceMessageId(wireMessage.referencedMessageId)
                    .build()
            )

    private fun packButtonActionConfirmation(
        wireMessage: WireMessage.ButtonActionConfirmation,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setButtonActionConfirmation(
                ButtonActionConfirmation
                    .newBuilder()
                    .setButtonId(wireMessage.buttonId)
                    .setReferenceMessageId(wireMessage.referencedMessageId)
                    .build()
            )

    private fun packKnock(
        wireMessage: WireMessage.Knock,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .apply {
                val knock = Knock
                    .newBuilder()
                    .setHotKnock(wireMessage.hotKnock)
                    .build()

                wireMessage.expiresAfterMillis?.let {
                    setEphemeral(
                        Ephemeral
                            .newBuilder()
                            .setKnock(knock)
                            .setExpireAfterMillis(it)
                            .build()
                    )
                } ?: setKnock(knock)
            }

    private fun packLocation(
        wireMessage: WireMessage.Location,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .apply {
                val location = Location
                    .newBuilder()
                    .setLatitude(wireMessage.latitude)
                    .setLongitude(wireMessage.longitude)
                    .setName(wireMessage.name)
                    .setZoom(wireMessage.zoom)
                    .build()

                wireMessage.expiresAfterMillis?.let {
                    setEphemeral(
                        Ephemeral
                            .newBuilder()
                            .setLocation(location)
                            .setExpireAfterMillis(it)
                            .build()
                    )
                } ?: setLocation(location)
            }

    private fun packDeleted(
        wireMessage: WireMessage.Deleted,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setDeleted(
                MessageDelete
                    .newBuilder()
                    .setMessageId(wireMessage.messageId)
                    .build()
            )

    private fun packReceipt(
        wireMessage: WireMessage.Receipt,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setConfirmation(
                Confirmation
                    .newBuilder()
                    .setType(
                        when (wireMessage.type) {
                            WireMessage.Receipt.Type.DELIVERED -> Confirmation.Type.DELIVERED
                            WireMessage.Receipt.Type.READ -> Confirmation.Type.READ
                        }
                    )
                    .setFirstMessageId(wireMessage.messageIds.first())
                    .addAllMoreMessageIds(wireMessage.messageIds.drop(1))
                    .build()
            )

    private fun packTextEdited(
        wireMessage: WireMessage.TextEdited,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setEdited(
                MessageEdit
                    .newBuilder()
                    .setReplacingMessageId(wireMessage.replacingMessageId.toString())
                    .setText(
                        packText(
                            wireMessage = WireMessage.Text.create(
                                conversationId = wireMessage.conversationId,
                                text = wireMessage.newContent,
                                mentions = wireMessage.newMentions
                            )
                        )
                    )
                    .build()
            )

    private fun packCompositeEdited(
        wireMessage: WireMessage.CompositeEdited,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder {
        val items: MutableList<Composite.Item> = mutableListOf()

        items.addAll(
            packItemsList(
                itemsList = wireMessage.newItems
            )
        )
        return genericMessage
            .setEdited(
                MessageEdit
                    .newBuilder()
                    .setReplacingMessageId(wireMessage.replacingMessageId.toString())
                    .setComposite(
                        Composite
                            .newBuilder()
                            .addAllItems(items)
                            .build()
                    )
                    .build()
            )
    }

    private fun packReaction(
        wireMessage: WireMessage.Reaction,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setReaction(
                Reaction.newBuilder()
                    .setEmoji(
                        wireMessage.emojiSet.map { it.trim() }.filter { it.isNotBlank() }
                            .joinToString(separator = ",") { it }
                    )
                    .setMessageId(wireMessage.messageId)
                    .build()
            )

    private fun packInCallEmoji(
        wireMessage: WireMessage.InCallEmoji,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setInCallEmoji(InCallEmoji.newBuilder().putAllEmojis(wireMessage.emojis))

    private fun packInCallHandRaise(
        wireMessage: WireMessage.InCallHandRaise,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setInCallHandRaise(InCallHandRaise.newBuilder().setIsHandUp(wireMessage.isHandUp))
}
