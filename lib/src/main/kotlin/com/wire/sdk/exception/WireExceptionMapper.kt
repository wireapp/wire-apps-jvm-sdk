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

package com.wire.sdk.exception

import com.wire.sdk.model.StandardError
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.serialization.JsonConvertException
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ExceptionMapper")

suspend fun ResponseException.mapToWireException(): Nothing {
    logger.warn("Error occurred", this)
    throw try {
        val errorResponse = this.response.body<StandardError>()
        when (this) {
            is ClientRequestException -> {
                WireException.ClientError(errorResponse, this)
            }
            is ServerResponseException -> {
                WireException.ServerError(errorResponse, this)
            }
            else -> WireException.UnknownError(
                message = this.message,
                throwable = this
            )
        }
    } catch (e: JsonConvertException) {
        logger.error(
            "Error response does not contain StandardError fields. ${e.message}"
        )
        WireException.UnknownError(e.cause?.message, e)
    } catch (e: NoTransformationFoundException) {
        logger.error(
            "Error response is not in serializable mime type. ${e.message}"
        )
        WireException.UnknownError(e.cause?.message, e)
    }
}
