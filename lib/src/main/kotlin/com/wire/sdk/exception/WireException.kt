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
        message: String? = DEFAULT_MESSAGE,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable) {
        companion object {
            const val DEFAULT_MESSAGE = "User does not have permission to perform this action"

            fun userIsNotAdmin() = Forbidden("User is not an admin")
        }
    }

    /**
     * Arguments / Parameter Error
     */
    class MissingParameter(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    class InvalidParameter(
        message: String? = DEFAULT_MESSAGE,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable) {
        companion object {
            const val DEFAULT_MESSAGE = "One or more parameters are invalid."
        }
    }

    /**
     * Database Error
     */
    class DatabaseError(
        message: String? = null,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable)

    class EntityNotFound(
        message: String? = DEFAULT_MESSAGE,
        throwable: Throwable? = null
    ) : WireException(message ?: throwable?.localizedMessage, throwable) {
        companion object {
            const val DEFAULT_MESSAGE = "Entity not found."
        }
    }

    /**
     * Client Error
     */
    data class ClientError(
        val response: StandardError,
        val throwable: Throwable?
    ) : WireException(response.message, throwable)

    /**
     * Server Error
     */
    data class ServerError(
        val response: StandardError,
        val throwable: Throwable?
    ) : WireException(response.message, throwable)

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
