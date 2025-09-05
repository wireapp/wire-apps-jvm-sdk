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

@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.integrations.jvm.calling.callbacks.implementations

import com.sun.jna.Pointer
import com.wire.integrations.jvm.calling.callbacks.CloseCallHandler
import com.wire.integrations.jvm.calling.types.Uint32_t
import com.wire.integrations.jvm.utils.obfuscateId
import com.wire.integrations.jvm.utils.toQualifiedId
import com.wire.kalium.logic.data.call.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

@Suppress("LongParameterList")
class OnCloseCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
) : CloseCallHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onClosedCall(
        reason: Int,
        conversationId: String,
        messageTime: Uint32_t,
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
            callRepository.leaveMlsConference(conversationIdWithDomain)
            logger.info(
                "[OnCloseCall] -> Left MLS conference" +
                    "ConversationId: ${conversationId.obfuscateId()}"
            )
            withCalling {
                wcall_end()
            }
        }
    }

}
