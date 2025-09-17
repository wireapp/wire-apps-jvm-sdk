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

package com.wire.sdk.calling

import com.sun.jna.Pointer
import com.wire.sdk.calling.callbacks.LogHandler
import com.wire.sdk.client.BackendClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.persistence.AppStorage
import org.slf4j.LoggerFactory

internal class GlobalCallManager(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient,
    private val appStorage: AppStorage
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    val callingAvsClient by lazy {
        CallingAvsClient.INSTANCE.apply {
            wcall_setup()
            wcall_run()
            wcall_set_log_handler(
                logHandler = CallingLogHandler,
                arg = null
            )
            logger.info("GlobalCallManager -> wcall_init")
        }
    }

    /**
     * Get a [CallManager] for a session, shouldn't be instantiated more than one
     * CallManager for a single session.
     */
    internal fun startCallManagerForClient(): CallManager =
        CallManagerImpl(
            callingAvsClient = callingAvsClient,
            backendClient = backendClient,
            cryptoClient = cryptoClient,
            appStorage = appStorage
        )
}

object CallingLogHandler : LogHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private const val LOG_LEVEL_DEBUG = 0
    private const val LOG_LEVEL_INFO = 1
    private const val LOG_LEVEL_WARN = 2
    private const val LOG_LEVEL_ERROR = 3

    override fun onLog(
        level: Int,
        message: String,
        arg: Pointer?
    ) {
        when (level) {
            LOG_LEVEL_DEBUG -> logger.debug(message)
            LOG_LEVEL_INFO -> logger.info(message)
            LOG_LEVEL_WARN -> logger.warn(message)
            LOG_LEVEL_ERROR -> logger.error(message)
            else -> logger.info(message)
        }
    }
}
