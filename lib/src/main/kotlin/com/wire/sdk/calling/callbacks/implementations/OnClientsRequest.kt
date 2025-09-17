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
import com.wire.sdk.calling.callbacks.ClientsRequestHandler
import com.wire.sdk.calling.types.Handle
import com.wire.sdk.utils.obfuscateId
import org.slf4j.LoggerFactory

internal class OnClientsRequest : ClientsRequestHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onClientsRequest(
        inst: Handle,
        conversationId: String,
        arg: Pointer?
    ) {
        logger.info("[Calling] OnClientsRequest: ${conversationId.obfuscateId()}")
    }
}
