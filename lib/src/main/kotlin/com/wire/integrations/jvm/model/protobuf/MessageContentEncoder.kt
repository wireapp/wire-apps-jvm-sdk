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

import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.utils.AESEncrypt
import com.wire.integrations.jvm.utils.toByteArray
import com.wire.integrations.jvm.utils.toInternalHexString
import com.wire.integrations.jvm.utils.toUTF16BEByteArray
import kotlin.math.roundToLong
import org.slf4j.LoggerFactory

object MessageContentEncoder {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun encodeMessageContent(message: WireMessage): EncodedMessageContent? =
        when (message) {
            is WireMessage.Asset ->
                message.remoteData?.assetId?.let { assetId ->
                    encodeMessageAsset(
                        messageTimeStampInMillis = message.instant.toEpochMilliseconds(),
                        assetId = assetId
                    )
                }

            is WireMessage.Text ->
                encodeMessageTextBody(
                    messageTimeStampInMillis = message.instant.toEpochMilliseconds(),
                    messageTextBody = message.text
                )

            is WireMessage.Location -> {
                encodeLocationCoordinates(
                    latitude = message.latitude,
                    longitude = message.longitude,
                    messageTimeStampInMillis = message.instant.toEpochMilliseconds()
                )
            }

            else -> {
                logger.warn("Attempting to encode message with unsupported content type")
                null
            }
        }

    private fun encodeMessageAsset(
        messageTimeStampInMillis: Long,
        assetId: String
    ): EncodedMessageContent =
        wrapIntoResult(
            messageTimeStampByteArray = encodeMessageTimeStampInMillis(
                messageTimeStampInMillis = messageTimeStampInMillis
            ),
            messageTextBodyUTF16BE = assetId.toUTF16BEByteArray()
        )

    private fun encodeMessageTextBody(
        messageTimeStampInMillis: Long,
        messageTextBody: String
    ): EncodedMessageContent =
        wrapIntoResult(
            messageTimeStampByteArray = encodeMessageTimeStampInMillis(
                messageTimeStampInMillis = messageTimeStampInMillis
            ),
            messageTextBodyUTF16BE = messageTextBody.toUTF16BEByteArray()
        )

    private fun encodeMessageTimeStampInMillis(messageTimeStampInMillis: Long): ByteArray {
        val messageTimeStampInSec = messageTimeStampInMillis / MILLIS_IN_SEC

        return messageTimeStampInSec.toByteArray()
    }

    private fun encodeLocationCoordinates(
        latitude: Float,
        longitude: Float,
        messageTimeStampInMillis: Long
    ): EncodedMessageContent {
        val latitudeBEBytes = (latitude * COORDINATES_ROUNDING).roundToLong().toByteArray()
        val longitudeBEBytes = (longitude * COORDINATES_ROUNDING).roundToLong().toByteArray()

        return EncodedMessageContent(
            byteArray = latitudeBEBytes + longitudeBEBytes + encodeMessageTimeStampInMillis(
                messageTimeStampInMillis = messageTimeStampInMillis
            )
        )
    }

    private fun wrapIntoResult(
        messageTimeStampByteArray: ByteArray,
        messageTextBodyUTF16BE: ByteArray
    ): EncodedMessageContent {
        return EncodedMessageContent(
            byteArray = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
                messageTextBodyUTF16BE +
                messageTimeStampByteArray
        )
    }

    private const val MILLIS_IN_SEC = 1000
    private const val COORDINATES_ROUNDING = 1000
}

class EncodedMessageContent(val byteArray: ByteArray) {
    val asHexString = byteArray.toInternalHexString()
    val sha256Digest = AESEncrypt.calculateSha256Hash(byteArray)
}
