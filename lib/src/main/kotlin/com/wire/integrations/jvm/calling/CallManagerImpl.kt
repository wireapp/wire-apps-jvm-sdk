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

import com.wire.integrations.jvm.calling.callbacks.ReadyHandler
import com.wire.integrations.jvm.calling.callbacks.implementations.OnAnsweredCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnCloseCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnConfigRequest
import com.wire.integrations.jvm.calling.callbacks.implementations.OnEstablishedCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnMissedCall
import com.wire.integrations.jvm.calling.callbacks.implementations.OnParticipantsVideoStateChanged
import com.wire.integrations.jvm.calling.callbacks.implementations.OnSFTRequest
import com.wire.integrations.jvm.calling.callbacks.implementations.OnSendOTR
import com.wire.integrations.jvm.calling.types.Handle
import com.wire.integrations.jvm.client.BackendClientDemo.Companion.DEMO_ENVIRONMENT
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
import org.slf4j.LoggerFactory

@Suppress("LongParameterList", "TooManyFunctions")
class CallManagerImpl internal constructor(
    private val callingAvsClient: CallingAvsClient,
    private val callingHttpClient: CallingHttpClient
) : CallManager {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job)
    private val deferredHandle: Deferred<Handle> = startHandleAsync()

    private fun startHandleAsync(): Deferred<Handle> {
        return scope.async(start = CoroutineStart.LAZY) {
            logger.info("$TAG: Creating Handle")
            val selfUserId = DEMO_USER_ID
            val selfUserDomain = DEMO_ENVIRONMENT
            val selfClientId = DEMO_USER_CLIENT

            val waitInitializationJob = Job()

            val handle = callingAvsClient.wcall_create(
                userId = "$selfUserId@$selfUserDomain",
                clientId = selfClientId,
                readyHandler = ReadyHandler { _, _ -> },
                sendHandler = OnSendOTR(),
                sftRequestHandler = OnSFTRequest(deferredHandle, callingAvsClient, callingHttpClient, scope)
                incomingCallHandler = OnIncomingCall(callRepository, callMapper, qualifiedIdMapper, scope, kaliumConfigs),
                missedCallHandler = OnMissedCall(),
                answeredCallHandler = OnAnsweredCall(),
                establishedCallHandler = OnEstablishedCall(),
                closeCallHandler = OnCloseCall(
                    callRepository = callRepository,
                    networkStateObserver = networkStateObserver,
                    scope = scope,
                    qualifiedIdMapper = qualifiedIdMapper,
                    createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata
                ),
                metricsHandler = metricsHandler,
                callConfigRequestHandler = OnConfigRequest(callingAvsClient, callingHttpClient, scope),
                constantBitRateStateChangeHandler = constantBitRateStateChangeHandler,
                videoReceiveStateHandler = OnParticipantsVideoStateChanged()
            )
            logger.info("$TAG - wcall_create() called")
            waitInitializationJob.join()
            handle
        }
    }

    private suspend fun <T> withCalling(action: suspend CallingAvsClient.(handle: Handle) -> T): T {
        val handle = deferredHandle.await()
        return callingAvsClient.action(handle)
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
