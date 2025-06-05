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

/**
 * Utility object responsible for encoding different types of messages into a byteArray.
 * Supported message types:
 * - Text
 * - Asset (via asset ID)
 * - Location (latitude and longitude)
 */
internal object MessageContentEncoder {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Encodes a message into an [EncodedMessageContent], based on its type.
     *
     * @param message The SDK message to encode. Supported types:
     * - [WireMessage.Text]
     * - [WireMessage.Asset]
     * - [WireMessage.Location]
     *
     * @return The encoded message as [EncodedMessageContent], or `null` if the message type
     * is not supported.
     */
    fun encodeMessageContent(message: WireMessage): EncodedMessageContent? =
        when (message) {
            is WireMessage.Asset ->
                message.remoteData?.assetId?.let { assetId ->
                    encodeMessageAsset(
                        messageTimeStampInMillis = message.timestamp.toEpochMilliseconds(),
                        assetId = assetId
                    )
                }

            is WireMessage.Text ->
                encodeMessageTextBody(
                    messageTimeStampInMillis = message.timestamp.toEpochMilliseconds(),
                    messageTextBody = message.text
                )

            is WireMessage.Location -> {
                encodeLocationCoordinates(
                    latitude = message.latitude,
                    longitude = message.longitude,
                    messageTimeStampInMillis = message.timestamp.toEpochMilliseconds()
                )
            }

            else -> {
                logger.warn("Attempting to encode message with unsupported content type")
                null
            }
        }

    /**
     * Encodes an asset message by combining the asset ID and message timestamp into a byteArray
     *
     * @param messageTimeStampInMillis Timestamp of the message in milliseconds.
     * @param assetId ID of the referenced asset.
     * @return [EncodedMessageContent] Encoded message content.
     */
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

    /**
     * Encodes a text message with its content and timestamp.
     *
     * @param messageTimeStampInMillis Timestamp of the message in milliseconds.
     * @param messageTextBody Text content of the message.
     * @return [EncodedMessageContent] Encoded message content.
     */
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

    /**
     * Converts a timestamp from milliseconds to seconds and encodes it as a byte array.
     *
     * @param messageTimeStampInMillis Timestamp in milliseconds.
     * @return [ByteArray] representing the timestamp in seconds.
     */
    private fun encodeMessageTimeStampInMillis(messageTimeStampInMillis: Long): ByteArray {
        val messageTimeStampInSec = messageTimeStampInMillis / MILLIS_IN_SEC

        return messageTimeStampInSec.toByteArray()
    }

    /**
     * Encodes a location message with latitude, longitude, and timestamp.
     * Coordinates are rounded and converted to byteArray before being combined.
     *
     * @param latitude Latitude in decimal degrees.
     * @param longitude Longitude in decimal degrees.
     * @param messageTimeStampInMillis Timestamp in milliseconds.
     * @return [EncodedMessageContent] Encoded message content.
     */
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

    /**
     * Wraps the encoded timestamp and message body into a final byteArray,
     * prepending a UTF-16BE BOM header.
     *
     * @param messageTimeStampByteArray Timestamp in byte array form.
     * @param messageTextBodyUTF16BE UTF-16BE encoded text or asset ID.
     * @return [EncodedMessageContent] Encoded message content.
     */
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

/**
 * A container class representing an encoded message.
 *
 * @property byteArray Raw encoded byte content of the message.
 * @property asHexString Hexadecimal string representation of the byteArray.
 * @property sha256Digest SHA-256 hash of the byteArray.
 */
internal class EncodedMessageContent(val byteArray: ByteArray) {
    val asHexString = byteArray.toInternalHexString()
    val sha256Digest = AESEncrypt.calculateSha256Hash(byteArray)
}
