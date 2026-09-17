/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.sdk.sample

import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.calling.SubconversationEpochInfo

/**
 * An app supplies the engine adapter and registers this handler with WireAppSdk.
 * The adapter owns AVS and must consume or copy epoch keys before updateEpoch returns.
 */
class CallingExample(private val engine: Engine) : WireEventsHandlerSuspending() {
    interface Engine {
        fun receive(message: WireMessage.Calling)
        fun updateEpoch(info: SubconversationEpochInfo)
        fun left(conversationId: QualifiedId)
    }

    override suspend fun onCallingMessageReceived(message: WireMessage.Calling) {
        engine.receive(message)
    }

    override suspend fun onSubconversationEpochChanged(info: SubconversationEpochInfo) {
        info.use { engine.updateEpoch(it) }
    }

    override suspend fun onSubconversationLeft(conversationId: QualifiedId) {
        engine.left(conversationId)
    }

    /** Join an incoming call's existing conference before starting media in the engine. */
    suspend fun joinCall(conversationId: QualifiedId) {
        manager.joinConferenceSuspending(conversationId).use { engine.updateEpoch(it) }
    }

    /** Use from the engine's outbound signaling callback. */
    suspend fun sendSignaling(conversationId: QualifiedId, content: String) {
        manager.sendMessageSuspending(WireMessage.Calling.create(conversationId, content))
    }

    suspend fun leaveCall(conversationId: QualifiedId) {
        manager.leaveConferenceSuspending(conversationId)
    }
}
