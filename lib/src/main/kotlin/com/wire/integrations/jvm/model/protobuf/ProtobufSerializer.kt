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
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.ButtonAction
import com.wire.integrations.protobuf.messages.Messages.ButtonActionConfirmation
import com.wire.integrations.protobuf.messages.Messages.Composite
import com.wire.integrations.protobuf.messages.Messages.Confirmation
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import com.wire.integrations.protobuf.messages.Messages.Knock
import com.wire.integrations.protobuf.messages.Messages.Location
import com.wire.integrations.protobuf.messages.Messages.MessageDelete

/**
 * Object class mapper for mapping [WireMessage] to [GenericMessage] (Protobuf) returning
 * a ByteArray.
 *
 * To be used when sending a message from [WireApplicationManager]
 * before reaching [CoreCryptoClient]
 */
@Suppress("TooManyFunctions")
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
            .setExpectsReadConfirmation(false)
            .setLegalHoldStatus(Messages.LegalHoldStatus.DISABLED)
            .build()

    private fun packTextMessage(
        wireMessage: WireMessage.Text,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setText(
                packText(wireMessage)
            )

    private fun packAsset(
        wireMessage: WireMessage.Asset,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setAsset(
                Messages.Asset.newBuilder()
                    .setUploaded(
                        Messages.Asset.RemoteData.newBuilder()
                            .setAssetId(wireMessage.remoteData?.assetId)
                            .setAssetDomain(wireMessage.remoteData?.assetDomain)
                            .setAssetToken(wireMessage.remoteData?.assetToken)
                            .setOtrKey(ByteString.copyFrom(wireMessage.remoteData?.otrKey))
                            .setSha256(ByteString.copyFrom(wireMessage.remoteData?.sha256))
                    )
                    .build()
            )

    private fun packItemsList(itemsList: List<WireMessage.Item>): List<Composite.Item> =
        itemsList.map {
            when (it) {
                is WireMessage.Composite.Button -> {
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
            .setKnock(
                Knock
                    .newBuilder()
                    .setHotKnock(wireMessage.hotKnock)
                    .build()
            )

    private fun packLocation(
        wireMessage: WireMessage.Location,
        genericMessage: GenericMessage.Builder
    ): GenericMessage.Builder =
        genericMessage
            .setLocation(
                Location
                    .newBuilder()
                    .setLatitude(wireMessage.latitude)
                    .setLongitude(wireMessage.longitude)
                    .setName(wireMessage.name)
                    .setZoom(wireMessage.zoom)
                    .build()
            )

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
}
