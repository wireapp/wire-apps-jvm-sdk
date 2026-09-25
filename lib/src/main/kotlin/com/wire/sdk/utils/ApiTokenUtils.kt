/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.sdk.utils

import com.wire.sdk.exception.WireException
import java.util.UUID

object ApiTokenUtils {
    private val userIdRegex = Regex(
        """(?:^|\.)u=([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:\.|$)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Extracts the user ID from an API token.
     *
     * @throws WireException.InvalidParameter if the token contains no valid user ID.
     */
    fun extractUserId(token: String): UUID =
        extractUserId(listOf(token), "Received API token doesn't contain a valid userId.")

    /** Extracts the first valid user ID, trying credentials in the supplied order. */
    internal fun extractUserId(
        tokens: List<String>,
        errorMessage: String
    ): UUID =
        tokens.firstNotNullOfOrNull { token ->
            userIdRegex.find(token)
                ?.groupValues?.get(1)
                ?.let { UUID.fromString(it) }
        } ?: throw WireException.InvalidParameter(errorMessage)
}
