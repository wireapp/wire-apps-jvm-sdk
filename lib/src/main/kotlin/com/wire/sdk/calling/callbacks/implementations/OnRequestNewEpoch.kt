/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.sdk.calling.callbacks.implementations

import com.sun.jna.Pointer
import com.wire.sdk.calling.callbacks.RequestNewEpochHandler
import com.wire.sdk.calling.CallingEpochInfoObserver
import com.wire.sdk.calling.CallingAvsClient
import com.wire.sdk.calling.types.Handle
import com.wire.sdk.utils.toQualifiedId

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch

import org.slf4j.LoggerFactory

internal class OnRequestNewEpoch(
    private val epochInfoObserver: CallingEpochInfoObserver,
    private val callingScope: CoroutineScope
) : RequestNewEpochHandler {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onRequestNewEpoch(inst: Handle, conversationId: String, arg: Pointer?) {
        callingScope.launch {
            logger.info("[OnRequestNewEpoch] - ConversationId: ${conversationId}")
          
            val qualifiedConversationId = conversationId.toQualifiedId()

            // Update AVS with current EpochInfo for MLS calls when new epoch is requested
            epochInfoObserver.updateEpoch(qualifiedConversationId)
        }
    }
}
