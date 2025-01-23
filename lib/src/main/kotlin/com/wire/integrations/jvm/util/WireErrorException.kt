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

/**
 * Interface defined for error exceptions thrown to SDK end user.
 */
sealed class WireErrorException @JvmOverloads constructor(
    message: String? = null,
    throwable: Throwable? = null
) : Exception(message, throwable) {
    private fun effectiveMessage() = message ?: cause?.localizedMessage

    /**
     * Authorization Error
     */
    class Unauthorized(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    class Forbidden(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Arguments / Parameter Error
     */
    class MissingParameter(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    class InvalidParameter(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Database Error
     */
    class DatabaseError(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    class EntityNotFound(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Client Error
     */
    class ClientError(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Internal Error
     */
    class InternalSystemError(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)

    /**
     * Unknown Error
     */
    class UnknownError(message: String? = null, throwable: Throwable? = null) :
        WireErrorException(message ?: throwable?.localizedMessage, throwable)
}
