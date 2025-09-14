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

package com.wire.integrations.jvm.calling

import com.sun.jna.Pointer
import com.wire.crypto.ClientId
import com.wire.integrations.jvm.calling.callbacks.implementations.OnAnsweredCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnCloseCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnConfigRequest
import com.wire.integrations.jvm.calling.callbacks.implementations.OnEstablishedCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnIncomingCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnMissedCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnParticipantsVideoStateChanged
import com.wire.integrations.jvm.calling.callbacks.implementations.OnSFTRequest
import com.wire.integrations.jvm.calling.callbacks.implementations.OnSendOTR
import com.wire.integrations.jvm.calling.types.Handle
import com.wire.integrations.jvm.calling.types.Uint32Native
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.client.BackendClientDemo.Companion.DEMO_ENVIRONMENT
import com.wire.integrations.jvm.client.BackendClientDemo.Companion.DEMO_USER_CLIENT
import com.wire.integrations.jvm.client.BackendClientDemo.Companion.DEMO_USER_ID
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.utils.obfuscateId
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "TooManyFunctions")
class CallManagerImpl internal constructor(
    private val callingAvsClient: CallingAvsClient,
    private val callingHttpClient: CallingHttpClient,
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient
) : CallManager {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)
    private val deferredHandle: Deferred<Handle> = startHandleAsync()

    private fun startHandleAsync(): Deferred<Handle> {
        return scope.async(start = CoroutineStart.LAZY) {
            logger.info("Creating Handle")
            val selfUserId = DEMO_USER_ID
            val selfUserDomain = DEMO_ENVIRONMENT
            val selfUser = QualifiedId(selfUserId, selfUserDomain)
            val selfClientId = DEMO_USER_CLIENT

            val waitInitializationJob = Job()

            val handle = callingAvsClient.wcall_create(
                userId = selfUser.toFederatedId(),
                clientId = selfClientId,
                readyHandler = { _, _ -> logger.info("Calling ready") },
                sendHandler = OnSendOTR(),
                sftRequestHandler = OnSFTRequest(
                    deferredHandle,
                    callingAvsClient,
                    callingHttpClient,
                    scope
                ),
                incomingCallHandler = OnIncomingCall(
                    backendClient,
                    cryptoClient,
                    deferredHandle,
                    callingAvsClient,
                    scope
                ),
                missedCallHandler = OnMissedCall(),
                answeredCallHandler = OnAnsweredCall(),
                establishedCallHandler = OnEstablishedCall(),
                closeCallHandler = OnCloseCall(
                    backendClient = backendClient,
                    callingAvsClient = callingAvsClient,
                    handle = deferredHandle,
                    scope = scope
                ),
                metricsHandler =
                    { conversationId: String, metricsJson: String, _: Pointer? ->
                        logger.info("Calling metrics on conversation $conversationId: $metricsJson")
                    },
                callConfigRequestHandler = OnConfigRequest(
                    callingAvsClient,
                    callingHttpClient,
                    scope
                ),
                constantBitRateStateChangeHandler =
                    { userId: String, clientId: String, isEnabled: Boolean, _: Pointer? ->
                        logger.info(
                            "ConstantBitRate changed for userId: ${userId.obfuscateId()} " +
                                "clientId: ${clientId.obfuscateId()}  isCbrEnabled: $isEnabled"
                        )
                    },
                videoReceiveStateHandler = OnParticipantsVideoStateChanged(),
                arg = null
            )
            logger.info("wcall_create() called")
            waitInitializationJob.join()
            handle
        }
    }

    private suspend fun <T> withCalling(action: suspend CallingAvsClient.(handle: Handle) -> T): T {
        val handle = deferredHandle.await()
        return callingAvsClient.action(handle)
    }

    override suspend fun onCallingMessageReceived(
        message: WireMessage.Calling,
        senderClient: ClientId
    ) = withCalling {
        logger.info("onCallingMessageReceived called: ${message.content}")

        if (!message.content.contains("REMOTEMUTE")) {
            val msg = message.content.toByteArray()

            wcall_recv_msg(
                inst = deferredHandle.await(),
                msg = msg,
                len = msg.size,
                curr_time = Uint32Native(value = Clock.System.now().epochSeconds),
                msg_time = Uint32Native(value = message.timestamp.epochSeconds),
                convId = message.conversationId.toFederatedId(),
                userId = message.sender.toFederatedId(),
                clientId = senderClient.value,
                // Hard coding 3 as for "Conference MLS"
                convType = 3
            )
            logger.info("wcall_recv_msg() called")
        }
    }

    override suspend fun endCall(conversationId: QualifiedId) =
        withCalling {
            logger.info("endCall -> ConversationId: $conversationId")

            wcall_end(
                inst = deferredHandle.await(),
                conversationId = "${conversationId.id}@${conversationId.domain}"
            )
        }

    override suspend fun reportProcessNotifications(isStarted: Boolean) {
        withCalling {
            wcall_process_notifications(it, isStarted)
        }
    }

    override suspend fun cancelJobs() {
        deferredHandle.cancel()
        scope.cancel()
        job.cancel()
    }
}
