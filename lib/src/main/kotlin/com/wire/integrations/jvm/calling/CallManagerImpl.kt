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

package com.wire.integrations.jvm.calling

import com.sun.jna.Pointer
import com.wire.integrations.jvm.calling.callbacks.ReadyHandler
import com.wire.integrations.jvm.calling.types.Handle
import com.wire.integrations.jvm.client.BackendClientDemo.Companion.DEMO_USER_CLIENT
import com.wire.integrations.jvm.client.BackendClientDemo.Companion.DEMO_USER_ID
import com.wire.integrations.jvm.model.QualifiedId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.Collections

@Suppress("LongParameterList", "TooManyFunctions")
class CallManagerImpl internal constructor(private val calling: CallingClient) : CallManager {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)
    private val deferredHandle: Deferred<Handle> = startHandleAsync()

    private fun startHandleAsync(): Deferred<Handle> {
        return scope.async(start = CoroutineStart.LAZY) {
            logger.info("$TAG: Creating Handle")
            val selfUserId = DEMO_USER_ID
            val selfClientId = DEMO_USER_CLIENT

            val waitInitializationJob = Job()

            val handle = calling.wcall_create(
                userId = selfUserId,
                clientId = selfClientId,
                readyHandler = ReadyHandler { handle, arg -> Unit },
                // TODO(refactor): inject all of these CallbackHandlers in class constructor
                sendHandler = OnSendOTR(
                    qualifiedIdMapper = qualifiedIdMapper,
                    selfUserId = selfUserId,
                    selfClientId = selfClientId,
                    callMapper = callMapper,
                    callingMessageSender = callingMessageSender,
                ),
                sftRequestHandler = OnSFTRequest(deferredHandle, calling, callRepository, scope)
                    .keepingStrongReference(),
                incomingCallHandler = OnIncomingCall(callRepository, callMapper, qualifiedIdMapper, scope, kaliumConfigs)
                    .keepingStrongReference(),
                missedCallHandler = OnMissedCall,
                answeredCallHandler = OnAnsweredCall(callRepository, scope, qualifiedIdMapper)
                    .keepingStrongReference(),
                establishedCallHandler = OnEstablishedCall(callRepository, scope, qualifiedIdMapper)
                    .keepingStrongReference(),
                closeCallHandler = OnCloseCall(
                    callRepository = callRepository,
                    networkStateObserver = networkStateObserver,
                    scope = scope,
                    qualifiedIdMapper = qualifiedIdMapper,
                    createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata
                ).keepingStrongReference(),
                metricsHandler = metricsHandler,
                callConfigRequestHandler = OnConfigRequest(calling, callRepository, scope)
                    .keepingStrongReference(),
                constantBitRateStateChangeHandler = constantBitRateStateChangeHandler,
                videoReceiveStateHandler = OnParticipantsVideoStateChanged().keepingStrongReference(),
                arg = null
            )
            logger.info("$TAG - wcall_create() called")
            waitInitializationJob.join()
            handle
        }
    }

    private suspend fun <T> withCalling(action: suspend CallingClient.(handle: Handle) -> T): T {
        val handle = deferredHandle.await()
        return calling.action(handle)
    }

    override suspend fun endCall(conversationId: QualifiedId) = withCalling {
        logger.info("[$TAG][endCall] -> ConversationId: $conversationId")

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

    companion object {
        const val TAG = "CallManager"
    }
}
