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

package com.wire.sdk.calling.callbacks.implementations

import com.sun.jna.Pointer
import com.wire.sdk.calling.CallingHttpClient
import com.wire.sdk.calling.CallingAvsClient
import com.wire.sdk.calling.callbacks.CallConfigRequestHandler
import com.wire.sdk.calling.types.AvsCallBackError
import com.wire.sdk.calling.types.Handle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class OnConfigRequest(
    private val calling: CallingAvsClient,
    private val callingHttpClient: CallingHttpClient,
    private val callingScope: CoroutineScope
) : CallConfigRequestHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onConfigRequest(
        inst: Handle,
        arg: Pointer?
    ): Int {
        logger.info("[OnConfigRequest] - STARTED")
        callingScope.launch {
            val config = callingHttpClient.getCallConfig(limit = null)
            calling.wcall_config_update(
                inst = inst,
                error = 0,
                jsonString = config
            )
            logger.info("[OnConfigRequest] - wcall_config_update()")
        }

        return AvsCallBackError.NONE.value
    }
}
