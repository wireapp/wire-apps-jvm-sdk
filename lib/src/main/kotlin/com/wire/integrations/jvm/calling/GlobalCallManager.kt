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
import com.wire.integrations.jvm.calling.callbacks.LogHandler
import com.wire.integrations.jvm.model.QualifiedId
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class GlobalCallManager() {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val callManagerHolder: ConcurrentMap<QualifiedId, CallManager> by lazy {
        ConcurrentHashMap()
    }

    val calling by lazy {
        CallingClient.INSTANCE.apply {
//            if (CurrentPlatform().type == PlatformType.ANDROID)
//                wcall_init(env = ENVIRONMENT_DEFAULT)
//            else {
                wcall_setup()
                wcall_run()
//            }
            wcall_set_log_handler(
                logHandler = CallingLogHandler,
                arg = null
            )
            logger.info("GlobalCallManager -> wcall_init")
        }
    }

//    /**
//     * Get a [CallManager] for a session, shouldn't be instantiated more than one CallManager for a single session.
//     */
//    @Suppress("LongParameterList")
//    internal fun getCallManagerForClient(
//        userId: QualifiedId,
//        callRepository: CallRepository,
//        currentClientIdProvider: CurrentClientIdProvider,
//        selfConversationIdProvider: SelfConversationIdProvider,
//        conversationRepository: ConversationRepository,
//        userConfigRepository: UserConfigRepository,
//        messageSender: MessageSender,
//        callMapper: CallMapper,
//        federatedIdMapper: FederatedIdMapper,
//        qualifiedIdMapper: QualifiedIdMapper,
//        videoStateChecker: VideoStateChecker,
//        conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
//        getCallConversationType: GetCallConversationTypeProvider,
//        networkStateObserver: NetworkStateObserver,
//        kaliumConfigs: KaliumConfigs,
//        createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase
//    ): CallManager {
//        return callManagerHolder.computeIfAbsent(userId) {
//            CallManagerImpl(
//                calling = calling,
//                callRepository = callRepository,
//                selfUserId = userId,
//                currentClientIdProvider = currentClientIdProvider,
//                selfConversationIdProvider = selfConversationIdProvider,
//                callMapper = callMapper,
//                messageSender = messageSender,
//                conversationRepository = conversationRepository,
//                federatedIdMapper = federatedIdMapper,
//                qualifiedIdMapper = qualifiedIdMapper,
//                videoStateChecker = videoStateChecker,
//                conversationClientsInCallUpdater = conversationClientsInCallUpdater,
//                getCallConversationType = getCallConversationType,
//                networkStateObserver = networkStateObserver,
//                mediaManagerService = mediaManager,
//                flowManagerService = flowManager,
//                userConfigRepository = userConfigRepository,
//                kaliumConfigs = kaliumConfigs,
//                createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata
//            )
//        }
//    }
//
//    suspend fun removeInMemoryCallingManagerForUser(userId: UserId) {
//        callManagerHolder[userId]?.cancelJobs()
//        callManagerHolder.remove(userId)
//    }

}

object CallingLogHandler : LogHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)
    override fun onLog(level: Int, message: String, arg: Pointer?) {
        when (level) {
            0 -> logger.debug(message)
            1 -> logger.info(message)
            2 -> logger.warn(message)
            3 -> logger.error(message)
            else -> logger.info(message)
        }
    }
}
