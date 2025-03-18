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

package com.wire.integrations.jvm.utils.network

import com.wire.integrations.jvm.utils.toJsonElement
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charsets
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job

internal class AppsHttpLogger(
    private val level: LogLevel,
    private val logger: Logger,
    private val appsLogger: org.slf4j.Logger
) {
    private val requestLog = mutableMapOf<String, Any>()
    private val responseLog = mutableMapOf<String, Any>()
    private val requestLoggedMonitor = Job()
    private val responseHeaderMonitor = Job()

    private val requestLogged = atomic(false)
    private val responseLogged = atomic(false)

    private var responseLogLevel: AppsLogLevel = AppsLogLevel.ERROR

    fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        requestLog["method"] = request.method.value
        requestLog["endpoint"] = obfuscatePath(Url(request.url))

        val content = request.body as OutgoingContent

        when {
            level.info -> {
                val obfuscatedHeaders =
                    obfuscatedHeaders(
                        request.headers.entries().map {
                            it.key to it.value
                        }
                    ).toMutableMap()
                content.contentLength
                    ?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType
                    ?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders
                    .putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                requestLog["headers"] =
                    obfuscatedJsonMessage(obfuscatedHeaders.toJsonElement().toString())
            }

            level.headers -> {
                val obfuscatedHeaders =
                    obfuscatedHeaders(
                        request.headers.entries().map {
                            it.key to it.value
                        }
                    ).toMutableMap()
                content.contentLength
                    ?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType
                    ?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders
                    .putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                requestLog["headers"] =
                    obfuscatedJsonMessage(obfuscatedHeaders.toJsonElement().toString())
            }

            level.body -> {
                val obfuscatedHeaders =
                    obfuscatedHeaders(
                        request.headers.entries().map {
                            it.key to it.value
                        }
                    ).toMutableMap()
                content.contentLength
                    ?.let { obfuscatedHeaders[HttpHeaders.ContentLength] = it.toString() }
                content.contentType
                    ?.let { obfuscatedHeaders[HttpHeaders.ContentType] = it.toString() }
                obfuscatedHeaders
                    .putAll(obfuscatedHeaders(content.headers.entries().map { it.key to it.value }))

                requestLog["headers"] =
                    obfuscatedJsonMessage(obfuscatedHeaders.toJsonElement().toString())
            }
        }

        return null
    }

    fun logResponse(response: HttpResponse) {
        responseLog["method"] = response.call.request.method.value
        responseLog["endpoint"] = obfuscatePath(response.call.request.url)
        responseLog["status"] = response.status.value

        when {
            level.info -> {
                // Intentionally left empty
            }

            level.headers -> {
                val obfuscatedHeaders =
                    obfuscatedHeaders(
                        response.headers.entries().map {
                            it.key to it.value
                        }
                    ).toMutableMap()
                responseLog["headers"] = obfuscatedHeaders.toMap()
            }
        }

        responseLogLevel = if (response.status.value < HttpStatusCode.BadRequest.value) {
            AppsLogLevel.VERBOSE
        } else if (response.status.value < HttpStatusCode.InternalServerError.value) {
            AppsLogLevel.WARN
        } else {
            AppsLogLevel.ERROR
        }

        responseHeaderMonitor.complete()
    }

    suspend fun logResponseException(
        request: HttpRequest,
        cause: Throwable
    ) {
        requestLoggedMonitor.join()
        if (level.info) {
            appsLogger.info(
                """RESPONSE FAILURE:
                            |{"endpoint":"${obfuscatePath(request.url)}\",
                            | "method":"${request.method.value}",
                            |  "cause":"$cause"}
                            |  """
                    .trimMargin()
            )
        }
    }

    suspend fun logResponseBody(
        contentType: ContentType?,
        content: ByteReadChannel
    ): Unit =
        with(logger) {
            responseHeaderMonitor.join()

            val text =
                content.tryReadText(contentType?.charset() ?: Charsets.UTF_8)
                    ?: "\"response body omitted\""
            responseLog["Content-Type"] = contentType?.charset() ?: Charsets.UTF_8
            responseLog["Content"] = obfuscatedJsonMessage(text)
        }

    fun closeRequestLog() {
        if (!requestLogged.compareAndSet(false, true)) return

        try {
            val jsonElement = requestLog.toJsonElement()
            appsLogger.info("REQUEST: $jsonElement")
        } finally {
            requestLoggedMonitor.complete()
        }
    }

    suspend fun closeResponseLog() {
        if (!responseLogged.compareAndSet(false, true)) return

        requestLoggedMonitor.join()
        val jsonElement = responseLog.toJsonElement()
        val logString = "RESPONSE: $jsonElement"

        when (responseLogLevel) {
            AppsLogLevel.VERBOSE -> appsLogger.info(logString)
            AppsLogLevel.WARN -> appsLogger.warn(logString)
            else -> appsLogger.error(logString)
        }
    }

    private fun obfuscatedHeaders(headers: List<Pair<String, List<String>>>): Map<String, String> =
        headers.associate {
            it.first to it.second.joinToString(",")
        }
}

enum class AppsLogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    DISABLED
}
