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
import com.wire.sdk.calling.callbacks.SendHandler
import com.wire.sdk.calling.types.Handle
import com.wire.sdk.utils.obfuscateId
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
internal class OnSendOTR : SendHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Suppress("TooGenericExceptionCaught", "NestedBlockDepth")
    override fun onSend(
        context: Pointer?,
        _remoteConversationId: String,
        _remoteSelfUserId: String,
        _remoteClientIdSelf: String,
        _targetRecipientsJson: String?,
        _clientIdDestination: String?,
        data: Pointer?,
        length: Handle,
        isTransient: Boolean,
        myClientsOnly: Boolean,
        arg: Pointer?
    ): Int {

        val remoteConversationId = _remoteConversationId.toString()
        val remoteSelfUserId = _remoteSelfUserId.toString()
        val remoteClientIdSelf = _remoteClientIdSelf.toString()
        val targetRecipientsJson = _targetRecipientsJson?.toString()
        val clientIdDestination = _clientIdDestination?.toString()

        logger.info(
            "[Calling] OnSendOTR: ${remoteConversationId.obfuscateId()} - " +
                "${remoteSelfUserId.obfuscateId()} - ${remoteClientIdSelf.obfuscateId()} - " +
                "$targetRecipientsJson - ${clientIdDestination?.obfuscateId()}"
        )
        return 0
    }
}
