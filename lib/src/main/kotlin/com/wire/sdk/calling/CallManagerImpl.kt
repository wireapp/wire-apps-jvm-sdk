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
import com.wire.sdk.calling.callbacks.implementations.OnParticipantListChanged
import com.wire.sdk.calling.callbacks.implementations.OnParticipantsVideoStateChanged
import com.wire.sdk.calling.callbacks.implementations.OnRequestNewEpoch
import com.wire.sdk.calling.callbacks.implementations.OnSFTRequest
import com.wire.sdk.calling.callbacks.implementations.OnSendOTR
import com.wire.sdk.calling.types.EpochInfo
import com.wire.sdk.calling.types.Handle
import com.wire.sdk.calling.types.Uint32Native
import com.wire.sdk.client.BackendClient
import com.wire.sdk.service.conversation.ConversationService
import com.wire.sdk.config.IsolatedKoinContext
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
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.Base64
import java.util.Collections
import kotlin.time.Clock

@Suppress("LongParameterList", "TooManyFunctions")
class CallManagerImpl internal constructor(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient,
    private val conversationService: ConversationService,
    private val appStorage: AppStorage
) : CallManager {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)
    private val epochInfoObserver = CallingEpochInfoObserver(
        cryptoClient = cryptoClient,
        scope = scope,
        updateEpochInfo = ::updateEpochInfo
    )

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

    private val strongReferences = Collections.synchronizedList(mutableListOf<Any>())
    private fun <T : Any> T.keepingStrongReference(): T {
        strongReferences.add(this)
        return this
    }

    @Suppress("LongMethod")
    private fun startHandleAsync(): Deferred<Handle> {
        logger.info("startHandleAsync is called")
        return scope.async(start = CoroutineStart.LAZY) {
            logger.info("Creating Handle")
            val selfUser = IsolatedKoinContext.getApplicationUser()
            val selfClientId = checkNotNull(appStorage.getDeviceId()) {
                "Cannot start AVS calling before the SDK client is registered"
            }

            val waitInitializationJob = Job()

            val handle = callingAvsClient.wcall_create(
                userId = selfUser.toFederatedId(),
                clientId = selfClientId,
                readyHandler = ReadyHandler { version: Int, arg: Pointer? ->
                    logger.info("readyHandler version=$version; arg=$arg")
                    onCallingReady()
                    waitInitializationJob.complete()
                    Unit
                }.keepingStrongReference(),
                sendHandler = OnSendOTR(),
                sftRequestHandler = OnSFTRequest(
                    deferredHandle,
                    callingAvsClient,
                    backendClient,
                    scope
                ).keepingStrongReference(),
                incomingCallHandler = OnIncomingCall(
                    backendClient,
                    cryptoClient,
                    conversationService,
                    epochInfoObserver,
                    deferredHandle,
                    callingAvsClient,
                    scope
                ).keepingStrongReference(),
                missedCallHandler = OnMissedCall(),
                answeredCallHandler = OnAnsweredCall(),
                establishedCallHandler = OnEstablishedCall(),
                closeCallHandler = OnCloseCall(
                    backendClient = backendClient,
                    callingAvsClient = callingAvsClient,
                    stopEpochInfoObservation = epochInfoObserver::stopObserving,
                    handle = deferredHandle,
                    scope = scope
                ).keepingStrongReference(),
                metricsHandler =
                    { conversationId: String, metricsJson: String, _: Pointer? ->
                        logger.info("Calling metrics on conversation $conversationId: $metricsJson")
                    },
                callConfigRequestHandler = OnConfigRequest(
                    callingAvsClient,
                    backendClient,
                    scope
                ).keepingStrongReference(),
                constantBitRateStateChangeHandler =
                    { userId: String, clientId: String, isEnabled: Boolean, _: Pointer? ->
                        logger.info(
                            "ConstantBitRate changed for userId: ${userId.obfuscateId()} " +
                                "clientId: ${clientId.obfuscateId()}  isCbrEnabled: $isEnabled"
                        )
                    },
                videoReceiveStateHandler = OnParticipantsVideoStateChanged().keepingStrongReference(),
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
                clientId = Base64.getEncoder().encodeToString(senderClient.copyBytes()),
                // Hard coding 3 as for "Conference MLS"
                convType = 3
            )
            logger.info("wcall_recv_msg() called")
        }
    }

    override suspend fun updateEpochInfo(
        conversationId: QualifiedId,
        epochInfo: EpochInfo
    ) {
        withCalling {
            wcall_set_epoch_info(
                it,
                conversationId.toFederatedId(),
                Uint32Native(epochInfo.epoch.toLong()),
                epochInfo.members.toJsonString(),
                kotlin.io.encoding.Base64.encode(epochInfo.sharedSecret)
            )
        }
    }

    override suspend fun endCall(conversationId: QualifiedId) {
        try {
            withCalling {
                logger.info("endCall -> ConversationId: $conversationId")

                wcall_end(
                    inst = it,
                    conversationId = conversationId.toFederatedId()
                )
            }
        } finally {
            epochInfoObserver.stopObserving(conversationId)
        }
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

    private fun onCallingReady() {
        initParticipantsHandler()
        initRequestNewEpochHandler()
    }

    private fun initParticipantsHandler() {
        scope.launch {
            withCalling {
                val participantListChangedHandler = OnParticipantListChanged().keepingStrongReference()
                wcall_set_participant_changed_handler(
                    inst = deferredHandle.await(),
                    participantListChanedHandler = participantListChangedHandler,
                    arg = null
                )
                logger.info("[Participants Changed]")
                // Here e can have custom logic for informing pstn user / leaving earls
                // instead of waiting 90 sec avs timeout
            }
        }
    }

    private fun initRequestNewEpochHandler() {
        scope.launch {
            withCalling {
                val requestNewEpochHandler = OnRequestNewEpoch(
                    epochInfoObserver = epochInfoObserver,
                    callingScope = scope
                ).keepingStrongReference()

                wcall_set_req_new_epoch_handler(
                    inst = deferredHandle.await(),
                    requestNewEpochHandler = requestNewEpochHandler
                )

                logger.info("wcall_set_req_new_epoch_handler() called")
            }
        }
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
