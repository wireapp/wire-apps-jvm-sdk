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
import com.wire.crypto.ClientId
import com.wire.sdk.calling.callbacks.LogHandler
import com.wire.sdk.calling.callbacks.ReadyHandler
import com.wire.sdk.calling.callbacks.implementations.OnAnsweredCall
import com.wire.sdk.calling.callbacks.implementations.OnCloseCall
import com.wire.sdk.calling.callbacks.implementations.OnConfigRequest
import com.wire.sdk.calling.callbacks.implementations.OnEstablishedCall
import com.wire.sdk.calling.callbacks.implementations.OnIncomingCall
import com.wire.sdk.calling.callbacks.implementations.OnMissedCall
import com.wire.sdk.calling.callbacks.implementations.OnParticipantsVideoStateChanged
import com.wire.sdk.calling.callbacks.implementations.OnSFTRequest
import com.wire.sdk.calling.callbacks.implementations.OnSendOTR
import com.wire.sdk.calling.types.Handle
import com.wire.sdk.calling.types.Uint32Native
import com.wire.sdk.client.BackendClient
import com.wire.sdk.client.BackendClientDemo.Companion.DEMO_ENVIRONMENT
import com.wire.sdk.client.BackendClientDemo.Companion.DEMO_USER_ID
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.utils.obfuscateId
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory
import java.util.Base64
import kotlin.time.Clock

@Suppress("LongParameterList", "TooManyFunctions")
class CallManagerImpl internal constructor(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient,
    private val appStorage: AppStorage
) : CallManager {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)

    private val callingAvsClient by lazy {
        CallingAvsClient.INSTANCE.apply {
            wcall_setup()
            wcall_run()
            wcall_set_log_handler(
                logHandler = CallingLogHandler,
                arg = null
            )
            logger.info("AVS setup complete")
        }
    }

    private val deferredHandle: Deferred<Handle> = startHandleAsync()

    private fun startHandleAsync(): Deferred<Handle> {
        logger.info("startHandleAsync is called")
        return scope.async(start = CoroutineStart.LAZY) {
            logger.info("Creating Handle")
            val selfUserId = DEMO_USER_ID
            val selfUserDomain = DEMO_ENVIRONMENT
            val selfUser = QualifiedId(selfUserId, selfUserDomain)
            val selfClientId = appStorage.getDeviceId()!!

            val waitInitializationJob = Job()

            val handle = callingAvsClient.wcall_create(
                userId = selfUser.toFederatedId(),
                clientId = selfClientId,
                readyHandler = ReadyHandler { version: Int, arg: Pointer? ->
                    logger.info("readyHandler version=$version; arg=$arg")
                    waitInitializationJob.complete()
                    Unit
                },
                sendHandler = OnSendOTR(),
                sftRequestHandler = OnSFTRequest(
                    deferredHandle,
                    callingAvsClient,
                    backendClient,
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
                    backendClient,
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
        logger.info("withCalling is called with action: {}", action)
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
                inst = it,
                msg = msg,
                len = msg.size,
                curr_time = Uint32Native(value = Clock.System.now().epochSeconds),
                msg_time = Uint32Native(value = message.timestamp.epochSeconds),
                convId = message.conversationId.toFederatedId(),
                userId = message.sender.toFederatedId(),
                clientId = Base64.getEncoder().encodeToString(senderClient.value),
                // Hard coding 3 as for "Conference MLS"
                convType = 3
            )
            logger.
            info("wcall_recv_msg() called")
        }
    }

    override suspend fun endCall(conversationId: QualifiedId) =
        withCalling {
            logger.info("endCall -> ConversationId: $conversationId")

            wcall_end(
                inst = it,
                conversationId = conversationId.toFederatedId()
            )
        }

    override suspend fun reportProcessNotifications(isStarted: Boolean) {
        withCalling {
            wcall_process_notifications(it, isStarted)
        }
    }

    override fun cancelJobs() {
        deferredHandle.cancel()
        scope.cancel()
        job.cancel()
    }
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
