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

import com.wire.crypto.toByteArray
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.protobuf.messages.Messages
import com.wire.integrations.protobuf.messages.Messages.ButtonAction
import com.wire.integrations.protobuf.messages.Messages.ButtonActionConfirmation
import com.wire.integrations.protobuf.messages.Messages.Composite
import com.wire.integrations.protobuf.messages.Messages.GenericMessage
import java.util.UUID

/**
 * Object class mapper for mapping [WireMessage] to [GenericMessage] returning
 * a ByteArray.
 *
 * To be used when sending a message from [WireApplicationManager]
 * before reaching [CoreCryptoClient]
 */
object ProtobufMapper {
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

    private fun packAsset(wireMessage: WireMessage.Asset): ByteArray =
        wireMessage.name?.toByteArray() ?: byteArrayOf()

    private fun packButtonList(
        buttonList: List<WireMessage.Composite.Button>
    ): List<Composite.Item> =
        buttonList.map {
            val button = Composite.Item.newBuilder().buttonBuilder
            button.id = it.id
            button.text = it.text

            Composite.Item.newBuilder().setButton(button).build()
        }

    private fun packComposite(wireMessage: WireMessage.Composite): ByteArray {
        val items: MutableList<Composite.Item> = mutableListOf()

        wireMessage.textContent?.let {
            val text = Messages.Text.newBuilder()
                .setContent(it.text)
                .setExpectsReadConfirmation(false)
                .setLegalHoldStatus(Messages.LegalHoldStatus.DISABLED)
                .build()
            items.add(Composite.Item.newBuilder().setText(text).build())
        }

        items.addAll(
            packButtonList(
                buttonList = wireMessage.buttonList
            )
        )

        return GenericMessage
            .newBuilder()
            .setMessageId(UUID.randomUUID().toString())
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
            .setMessageId(UUID.randomUUID().toString())
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
            .setMessageId(UUID.randomUUID().toString())
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
