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

package com.wire.integrations.jvm.exception

import io.ktor.client.plugins.ClientRequestException

fun Exception.mapToWireException() =
    when (this) {
        is WireException -> this
        is InterruptedException -> WireException.InternalSystemError(throwable = this)
        is ClientRequestException -> WireException.ClientError(throwable = this)
        else -> WireException.UnknownError(throwable = this)
    }

@Throws(WireException::class)
internal inline fun <T> runWithWireException(block: () -> T): T {
    return try {
        block()
    } catch (exception: Exception) {
        throw exception.mapToWireException()
    }
}
