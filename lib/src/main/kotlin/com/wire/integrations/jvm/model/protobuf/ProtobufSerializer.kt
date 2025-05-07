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
import com.wire.integrations.protobuf.messages.Messages.GenericMessage

/**
 * Object class mapper for mapping [WireMessage] to [GenericMessage] (Protobuf) returning
 * a ByteArray.
 *
 * To be used when sending a message from [WireApplicationManager]
 * before reaching [CoreCryptoClient]
 */
object ProtobufSerializer {
    fun toGenericMessageByteArray(wireMessage: WireMessage): ByteArray =
        when (wireMessage) {
            is WireMessage.Text -> packText(wireMessage)
            is WireMessage.Asset -> packAsset(wireMessage)
            is WireMessage.Composite -> packComposite(wireMessage)
            is WireMessage.ButtonAction -> packButtonAction(wireMessage)
            is WireMessage.ButtonActionConfirmation -> packButtonActionConfirmation(wireMessage)
            is WireMessage.Unknown -> throw WireException.CryptographicSystemError(
                "Unexpected message content type: $wireMessage"
            )
        }

    private fun packText(wireMessage: WireMessage.Text): ByteArray =
        GenericMessage
            .newBuilder()
            .setMessageId(wireMessage.id.toString())
            .setText(
                Messages.Text.newBuilder()
                    .setContent(wireMessage.text)
                    .setExpectsReadConfirmation(false)
                    .setLegalHoldStatus(Messages.LegalHoldStatus.DISABLED)
                    .build()
            )
            .build()
            .toByteArray()

    private fun packAsset(wireMessage: WireMessage.Asset): ByteArray {
        return GenericMessage
            .newBuilder()
            .setMessageId(wireMessage.id.toString())
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
            .build()
            .toByteArray()
    }

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
                    val text = Messages.Text.newBuilder()
                        .setContent(it.text)
                        .setExpectsReadConfirmation(false)
                        .setLegalHoldStatus(Messages.LegalHoldStatus.DISABLED)
                        .build()

                    Composite.Item.newBuilder().setText(text).build()
                }
            }
        }

    private fun packComposite(wireMessage: WireMessage.Composite): ByteArray {
        val items: MutableList<Composite.Item> = mutableListOf()

        items.addAll(
            packItemsList(
                itemsList = wireMessage.items
            )
        )

        return GenericMessage
            .newBuilder()
            .setMessageId(wireMessage.id.toString())
            .setComposite(
                Composite
                    .newBuilder()
                    .addAllItems(items)
                    .build()
            )
            .build()
            .toByteArray()
    }

    private fun packButtonAction(wireMessage: WireMessage.ButtonAction): ByteArray =
        GenericMessage
            .newBuilder()
            .setMessageId(wireMessage.id.toString())
            .setButtonAction(
                ButtonAction
                    .newBuilder()
                    .setButtonId(wireMessage.buttonId)
                    .setReferenceMessageId(wireMessage.referencedMessageId)
                    .build()
            )
            .build()
            .toByteArray()

    private fun packButtonActionConfirmation(
        wireMessage: WireMessage.ButtonActionConfirmation
    ): ByteArray =
        GenericMessage
            .newBuilder()
            .setMessageId(wireMessage.id.toString())
            .setButtonActionConfirmation(
                ButtonActionConfirmation
                    .newBuilder()
                    .setButtonId(wireMessage.buttonId)
                    .setReferenceMessageId(wireMessage.referencedMessageId)
                    .build()
            )
            .build()
            .toByteArray()
}
