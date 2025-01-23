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

package com.wire.integrations.jvm.util

import io.ktor.client.plugins.ClientRequestException
import kotlin.jvm.Throws

fun Exception.mapToWireErrorException() = when (this) {
    is WireErrorException -> this
    is InterruptedException -> WireErrorException.InternalSystemError(throwable = this)
    is ClientRequestException -> WireErrorException.ClientError(throwable = this)
    else -> WireErrorException.UnknownError(throwable = this)
}

@Throws(WireErrorException::class)
internal inline fun <T> runWithWireErrorException(block: () -> T): T {
    return try {
        block()
    } catch (exception: Exception) {
        throw exception.mapToWireErrorException()
    }
}
