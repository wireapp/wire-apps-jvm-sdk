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
import com.wire.crypto.toGroupInfo
import com.wire.sdk.calling.CallingAvsClient
import com.wire.sdk.calling.callbacks.IncomingCallHandler
import com.wire.sdk.calling.callbacks.implementations.CallTypeCalling.AUDIO
import com.wire.sdk.calling.callbacks.implementations.CallTypeCalling.VIDEO
import com.wire.sdk.calling.types.Handle
import com.wire.sdk.calling.types.Uint32Native
import com.wire.sdk.client.BackendClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.utils.obfuscateId
import com.wire.sdk.utils.toQualifiedId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Callback for incoming call event.
 * Answers the call immediately and starts audio recording.
 */
internal class OnIncomingCall(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient,
    private val handle: Deferred<Handle>,
    private val callingAvsClient: CallingAvsClient,
    private val scope: CoroutineScope
) : IncomingCallHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onIncomingCall(
        conversationId: String,
        messageTime: Uint32Native,
        userId: String,
        clientId: String,
        isVideoCall: Boolean,
        shouldRing: Boolean,
        conversationType: Int,
        arg: Pointer?
    ) {
        logger.info(
            "[OnIncomingCall] -> ConversationId: ${conversationId.obfuscateId()}" +
                " | UserId: ${userId.obfuscateId()} | shouldRing: $shouldRing" +
                " | type: $conversationType"
        )

        scope.launch {
            answerCall(
                conversationId = conversationId,
                userId = userId,
                isAudioCbr = false,
                isVideoCall = isVideoCall
            )
        }
    }

    private suspend fun answerCall(
        conversationId: String,
        userId: String,
        isAudioCbr: Boolean,
        isVideoCall: Boolean
    ) {
        logger.info(
            "[OnIncomingCall] -> answering call for conversation = $conversationId"
        )
        val callId = UUID.randomUUID()
        val callType = if (isVideoCall) VIDEO else AUDIO
        val qualifiedConversationId = conversationId.toQualifiedId()

        // Assume convo is MLS and that subconversation is already established
        val subConversationGroupInfo = backendClient.getSubConversationGroupInfo(
            qualifiedConversationId
        )
        cryptoClient.joinMlsConversationRequest(subConversationGroupInfo.toGroupInfo())

        callingAvsClient.wcall_audio_record(
            inst = handle.await(),
            userId = userId,
            filePath = "/tmp/wire_call_recording_${qualifiedConversationId.id}_$callId.wav"
        )

        callingAvsClient.wcall_answer(
            inst = handle.await(),
            conversationId = conversationId,
            callType = callType.avsValue,
            cbrEnabled = isAudioCbr
        )
        logger.info(
            "[OnIncomingCall] - wcall_answer() called -> Incoming call for conversation = " +
                "$qualifiedConversationId answered"
        )

        // AVS will close the call when the last participant is alone for more than 30s
        // TODO listen epoch changes in subconversation and let AVS know with wcall_set_epoch_info
    }
}

/**
 * [AUDIO] for audio call
 * [VIDEO] for video cal
 */
enum class CallTypeCalling(val avsValue: Int) {
    AUDIO(avsValue = 0),
    VIDEO(avsValue = 1)
}
