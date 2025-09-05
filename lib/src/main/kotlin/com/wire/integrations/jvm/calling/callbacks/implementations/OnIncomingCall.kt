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
import com.wire.integrations.jvm.calling.callbacks.IncomingCallHandler
import com.wire.integrations.jvm.calling.types.Handle
import com.wire.integrations.jvm.calling.types.Uint32_t
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.utils.obfuscateId
import com.wire.integrations.jvm.utils.toQualifiedId
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.ConversationTypeForCall
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class OnIncomingCall(
    private val callRepository: CallRepository,
    private val handle: Deferred<Handle>,
    private val callingAvsClient: CallingAvsClient,
    private val scope: CoroutineScope,
) : IncomingCallHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onIncomingCall(
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String,
        isVideoCall: Boolean,
        shouldRing: Boolean,
        conversationType: Int,
        arg: Pointer?
    ) {
        logger.info(
            "[OnIncomingCall] -> ConversationId: ${conversationId.obfuscateId()}" +
                    " | UserId: ${userId.obfuscateId()} | shouldRing: $shouldRing | type: $conversationType"
        )
        val qualifiedConversationId = conversationId.toQualifiedId()
        scope.launch {
            answerCall(
                conversationId = qualifiedConversationId,
                isAudioCbr = false,
                isVideoCall = isVideoCall
            )
        }
    }

    private suspend fun answerCall(
        conversationId: QualifiedId,
        isAudioCbr: Boolean,
        isVideoCall: Boolean
    ) {
        logger.info(
            "[OnIncomingCall] -> answering call for conversation = $conversationId"
        )
        val callType = if (isVideoCall) CallTypeCalling.VIDEO else CallTypeCalling.AUDIO

        callRepository.joinMlsConference(
            conversationId = conversationId,
            onJoined = {
                callingAvsClient.wcall_answer(
                    inst = handle.await(),
                    conversationId = federatedIdMapper.parseToFederatedId(conversationId),
                    callType = callType.avsValue,
                    cbrEnabled = isAudioCbr
                )
                logger.info(
                    "[OnIncomingCall] - wcall_answer() called -> Incoming call for conversation = " +
                            "$conversationId answered"
                )
            },
            onEpochChange = { conversationId, epochInfo ->
                updateEpochInfo(conversationId, epochInfo)
            }
        )
    }
}
