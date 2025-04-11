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
import com.wire.integrations.protobuf.messages.Messages.GenericMessage

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
}
