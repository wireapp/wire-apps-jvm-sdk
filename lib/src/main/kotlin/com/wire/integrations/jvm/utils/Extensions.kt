/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.integrations.jvm.utils

import java.util.UUID

private const val START_INDEX = 0
private const val END_INDEX_ID = 7
private const val END_INDEX_CLIENT_ID = 3

fun UUID.obfuscateId(): String {
    return this.toString().obfuscateId(END_INDEX_ID)
}

fun String.obfuscateId(): String {
    return obfuscateId(END_INDEX_ID)
}

fun String.obfuscateClientId(): String {
    return obfuscateId(END_INDEX_CLIENT_ID)
}

private fun String.obfuscateId(lastChar: Int): String =
    if (this.length < END_INDEX_ID) this else this.substring(START_INDEX, lastChar) + "***"

internal fun String.toUTF16BEByteArray(): ByteArray = toByteArray(charset = Charsets.UTF_16BE)

internal fun ByteArray.toStringFromUtf16BE(): String = toString(charset = Charsets.UTF_16BE)

/**
 * Converts a Long into a Byte Array Big Endian.
 */
@Suppress("MagicNumber")
internal fun Long.toByteArray(): ByteArray {
    val result = ByteArray(Long.SIZE_BYTES)

    var longConvertedToByteArray = this

    for (i in Long.SIZE_BYTES - 1 downTo 0) {
        result[i] = (longConvertedToByteArray and 0xFFL).toByte()
        longConvertedToByteArray = longConvertedToByteArray shr Long.SIZE_BYTES
    }

    return result
}

@Suppress("MagicNumber")
internal fun ByteArray.toInternalHexString(): String {
    return joinToString("") {
        (0xFF and it.toInt()).toString(16).padStart(2, '0')
    }
}
