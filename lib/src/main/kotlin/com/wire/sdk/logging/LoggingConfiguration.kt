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

import org.zalando.logbook.Logbook
import org.zalando.logbook.core.DefaultHttpLogWriter
import org.zalando.logbook.core.DefaultSink
import org.zalando.logbook.core.HeaderFilters
import org.zalando.logbook.core.QueryFilters
import org.zalando.logbook.json.JsonBodyFilters
import org.zalando.logbook.json.JsonHttpLogFormatter

internal object LoggingConfiguration {
    private const val OBFUSCATION_VALUE = "****"

    private val sensitiveHeaderParams = setOf(
        "authorization",
        "cookie",
        "set-cookie",
        "location",
        "x-amz-meta-user",
        "sec-websocket-key",
        "sec-websocket-accept",
        "sec-websocket-version"
    )

    private val sensitiveQueryParams = setOf(
        "password",
        "access_token",
        "conversation",
        "id",
        "user",
        "team"
    )

    private val sensitiveBodyParams = setOf(
        "id",
        "user",
        "team",
        "access_token",
        "creator_client",
        "qualified_id",
        "qualified_ids",
        "qualified_users",
        "content",
        "payload"
    )

    val logbook: Logbook = Logbook.builder()
        .condition { true }
        .headerFilter(
            HeaderFilters.replaceHeaders(
                sensitiveHeaderParams,
                OBFUSCATION_VALUE
            )
        )
        .queryFilter(
            QueryFilters.replaceQuery({ key -> key in sensitiveQueryParams }, OBFUSCATION_VALUE)
        )
        .bodyFilter(
            JsonBodyFilters.replaceJsonStringProperty(
                sensitiveBodyParams,
                OBFUSCATION_VALUE
            )
        )
        .sink(
            DefaultSink(
                JsonHttpLogFormatter(),
                DefaultHttpLogWriter()
            )
        )
        .build()
}
