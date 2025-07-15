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

import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.toByteArray

/**
 * A ContentConverter which does nothing, it simply passes byte arrays through as they are.
 * This is useful if you want to register your own custom binary content type
 * with the Ktor ContentNegotiation plugin.
 */
class ByteArrayConverter : ContentConverter {
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel
    ) = content.toByteArray()

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?
    ) = ByteArrayContent(value as ByteArray, contentType)
}

val Mls: ContentType
    get() = ContentType("message", "mls")
val XProtoBuf: ContentType
    get() = ContentType("application", "x-protobuf")

fun Configuration.mls(contentType: ContentType = Mls) {
    register(contentType, ByteArrayConverter())
}

fun Configuration.xprotobuf(contentType: ContentType = XProtoBuf) {
    register(contentType, ByteArrayConverter())
}
