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

import com.wire.integrations.jvm.model.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.serialization.JsonConvertException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ExceptionMapper")

suspend fun Throwable.mapToWireException() {
    if (this !is ResponseException) return
    logger.warn("Error occurred", this)
    val wireException = try {
        val errorResponse = this.response.body<ErrorResponse>()
        when (this) {
            is ClientRequestException -> {
                WireException.ClientError(errorResponse, this)
            }

            is ServerResponseException -> {
                WireException.InternalSystemError(errorResponse, this)
            }

            else -> WireException.UnknownError(
                message = this.message,
                throwable = this
            )
        }
    } catch (_: JsonConvertException) {
        WireException.UnknownError(throwable = this)
    }
    throw wireException
}
