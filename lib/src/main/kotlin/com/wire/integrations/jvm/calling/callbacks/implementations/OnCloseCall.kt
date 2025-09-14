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

package com.wire.integrations.jvm.calling.callbacks.implementations

import com.sun.jna.Pointer
import com.wire.integrations.jvm.calling.CallingAvsClient
import com.wire.integrations.jvm.calling.callbacks.CloseCallHandler
import com.wire.integrations.jvm.calling.types.Handle
import com.wire.integrations.jvm.calling.types.Uint32Native
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.utils.obfuscateId
import com.wire.integrations.jvm.utils.toQualifiedId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class OnCloseCall(
    private val backendClient: BackendClient,
    private val callingAvsClient: CallingAvsClient,
    private val handle: Deferred<Handle>,
    private val scope: CoroutineScope
) : CloseCallHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onClosedCall(
        reason: Int,
        conversationId: String,
        messageTime: Uint32Native,
        userId: String,
        clientId: String?,
        arg: Pointer?
    ) {
        logger.info(
            "[OnCloseCall] -> ConversationId: ${conversationId.obfuscateId()} |" +
                " UserId: ${userId.obfuscateId()} | Reason: $reason"
        )
        val conversationIdWithDomain = conversationId.toQualifiedId()

        scope.launch {
            backendClient.leaveSubConversation(conversationIdWithDomain)
            logger.info(
                "[OnCloseCall] -> Left MLS conference" +
                    "ConversationId: ${conversationId.obfuscateId()}"
            )
            callingAvsClient.wcall_end(
                inst = handle.await(),
                conversationId = conversationId
            )
        }
    }
}
