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

import com.wire.integrations.jvm.exception.NetworkErrorLabel.MLS_STALE_MESSAGE
import com.wire.integrations.jvm.exception.NetworkErrorLabel.TOO_MANY_CLIENTS
import com.wire.integrations.jvm.model.ErrorResponse

/**
 * Class containing all Wire Error Exceptions that are going to be thrown to the developer
 * who uses the SDK.
 */
sealed class WireException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null
) : Exception(message, throwable) {
    /**
     * Authorization Error
     */
    class Unauthorized(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    class Forbidden(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Arguments / Parameter Error
     */
    class MissingParameter(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    class InvalidParameter(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Database Error
     */
    class DatabaseError(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    class EntityNotFound(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Client Error
     */
    data class ClientError(
        val errorResponse: ErrorResponse,
        val throwable: Throwable?
    ) : WireException(errorResponse.message) {
        fun isMlsStaleMessage(): Boolean = errorResponse.label == MLS_STALE_MESSAGE

        /**
         * Currently not used as we are not tackling yet the recovering for TooManyClients exception
         */
        fun isTooManyClients(): Boolean = errorResponse.label == TOO_MANY_CLIENTS
    }

    /**
     * Internal Error
     */
    data class InternalSystemError(
        val errorResponse: ErrorResponse,
        val throwable: Throwable?
    ) : WireException(errorResponse.message)

    /**
     * Cryptographic Error
     */
    class CryptographicSystemError(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Unknown Error
     */
    class UnknownError(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)
}
