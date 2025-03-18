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

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingConfig
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.client.statement.content
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import org.slf4j.LoggerFactory

internal val AppsHttpCustomLogger = AttributeKey<AppsHttpLogger>("AppsHttpLogger")
val DisableLogging = AttributeKey<Unit>("DisableLogging")

class AppsKtorCustomLogging private constructor(
    val logger: Logger,
    val appsLogger: org.slf4j.Logger,
    var level: LogLevel,
    var filters: List<(HttpRequestBuilder) -> Boolean> = emptyList()
) {
    /**
     * [Logging] plugin configuration
     */
    class Config {
        /**
         * filters
         */
        internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()

        private var _logger: Logger? = null

        /**
         * [Logger] instance to use
         */
        var logger: Logger
            get() = _logger ?: Logger.DEFAULT
            set(value) {
                _logger = value
            }

        /**
         * log [LogLevel]
         */
        var level: LogLevel = LogLevel.HEADERS

        /**
         * [org.slf4j.Logger] instance to use
         */
        var appsLogger: org.slf4j.Logger = LoggerFactory.getLogger(this::class.java)

        /**
         * Log messages for calls matching a [predicate]
         */
        fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
            filters.add(predicate)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun setupRequestLogging(client: HttpClient) {
        client.sendPipeline.intercept(HttpSendPipeline.Monitoring) {
            if (!shouldBeLogged(context)) {
                context.attributes.put(DisableLogging, Unit)
                return@intercept
            }

            val response = try {
                logRequest(context)
            } catch (_: Throwable) {
                null
            }

            try {
                proceedWith(response ?: subject)
            } catch (cause: Throwable) {
                logRequestException(context, cause)
                throw cause
            }
        }
    }

    @OptIn(InternalAPI::class)
    @Suppress("TooGenericExceptionCaught")
    private fun setupResponseLogging(client: HttpClient) {
        client.receivePipeline.intercept(HttpReceivePipeline.State) { response ->
            if (level == LogLevel.NONE || response.call.attributes.contains(DisableLogging)) {
                return@intercept
            }

            val logger = response.call.attributes[AppsHttpCustomLogger]

            var failed = false
            try {
                logger.logResponse(response.call.response)
                proceedWith(subject)
            } catch (cause: Throwable) {
                logger.logResponseException(response.call.request, cause)
                failed = true
                throw cause
            } finally {
                if (failed || !level.body) logger.closeResponseLog()
            }
        }

        client.responsePipeline.intercept(HttpResponsePipeline.Receive) {
            if (level == LogLevel.NONE || context.attributes.contains(DisableLogging)) {
                return@intercept
            }

            try {
                proceed()
            } catch (cause: Throwable) {
                val logger = context.attributes[AppsHttpCustomLogger]
                logger.logResponseException(context.request, cause)
                logger.closeResponseLog()
                throw cause
            }
        }

        if (!level.body) return
    }

    private fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
        val logger = AppsHttpLogger(level, logger, appsLogger)
        request.attributes.put(AppsHttpCustomLogger, logger)

        logger.logRequest(request)

        logger.closeRequestLog()

        return null
    }

    companion object : HttpClientPlugin<Config, AppsKtorCustomLogging> {
        override val key: AttributeKey<AppsKtorCustomLogging> = AttributeKey("ClientLogging")

        override fun prepare(block: Config.() -> Unit): AppsKtorCustomLogging {
            val config = Config().apply(block)
            return AppsKtorCustomLogging(
                config.logger,
                config.appsLogger,
                config.level,
                config.filters
            )
        }

        override fun install(
            plugin: AppsKtorCustomLogging,
            scope: HttpClient
        ) {
            plugin.setupRequestLogging(scope)
            plugin.setupResponseLogging(scope)
        }
    }

    private fun shouldBeLogged(request: HttpRequestBuilder): Boolean =
        filters.isEmpty() || filters.any { it(request) }

    private fun logRequestException(
        context: HttpRequestBuilder,
        cause: Throwable
    ) {
        if (level.info) {
            appsLogger.info(
                """REQUEST FAILURE: {
                        |"endpoint":"${obfuscatePath(Url(context.url))}",
                        | "method":"${context.method.value}",
                        |  "cause":"$cause"}
                        |  """
                    .trimMargin()
            )
        }
    }
}

/**
 * Configure and install [Logging] in [HttpClient].
 */
@Suppress("FunctionNaming")
fun HttpClientConfig<*>.Logging(loggingConfig: LoggingConfig) {
    install(Logging) {
        level = loggingConfig.level
        logger = loggingConfig.logger
        format = loggingConfig.format
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? =
    try {
        readRemaining().readText(charset = charset)
    } catch (cause: Throwable) {
        null
    }
