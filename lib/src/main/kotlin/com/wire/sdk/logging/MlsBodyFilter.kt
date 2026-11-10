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

package com.wire.sdk.logging

import org.zalando.logbook.BodyFilter
import com.wire.sdk.utils.Mls

/**
 * Creates a Logbook [BodyFilter] that replaces the body of mls
 * messages with a placeholder.
 */
fun mlsBodyFilter(obfuscationValue: String): BodyFilter {
    val mlsTypeStr = Mls.toString()
    return BodyFilter { contentType, body ->
        body.takeIf { contentType?.contains(mlsTypeStr, ignoreCase = true) != true }
            ?: obfuscationValue
    }
}
