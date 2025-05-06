/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.integrations.jvm

import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.ConversationMember
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.service.WireApplicationManager
import org.slf4j.LoggerFactory

/**
 * Abstract class exposed by the SDK, clients can override the methods in this class and pass it
 * during SDK initialization to handle Wire events.
 */
@Suppress("TooManyFunctions")
abstract class WireEventsHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * The [WireApplicationManager] is used to manage the Wire application lifecycle and
     * communication with the backend.
     * NOTE: Do not use manager in the constructor of this class, as it will be null at that time.
     * Use it only inside the event handling methods.
     */
    protected val manager: WireApplicationManager by lazy {
        IsolatedKoinContext.koinApp.koin.get<WireApplicationManager>()
    }

    open fun onNewMessage(wireMessage: WireMessage.Text) {
        logger.info("Received event: onNewMessage")
    }

    open suspend fun onNewMessageSuspending(wireMessage: WireMessage.Text) {
        logger.info("Received event: onNewMessageSuspending")
    }

    /**
     * One or more users have joined a conversation accessible by the Wire App.
     * This can be new users or your Wire App itself just joining a conversation
     */
    open fun onConversationJoin(
        conversation: ConversationData,
        members: List<ConversationMember>
    ) {
        logger.info("Received event: onConversationJoin")
    }

    /**
     * A user deleted a conversation accessible by the Wire App.
     */
    open fun onConversationDelete(conversationId: QualifiedId) {
        logger.info("Received event: onConversationDelete")
    }

    open fun onNewAsset(wireMessage: WireMessage.Asset) {
        logger.info("Received event: onNewAsset")
    }

    open suspend fun onNewAssetSuspending(wireMessage: WireMessage.Asset) {
        logger.info("Received event: onNewAssetSuspending")
    }

    open fun onNewComposite(wireMessage: WireMessage.Composite) {
        logger.info("Received event: onNewComposite")
    }

    open suspend fun onNewCompositeSuspending(wireMessage: WireMessage.Composite) {
        logger.info("Received event: onNewCompositeSuspending")
    }

    open suspend fun onNewButtonAction(wireMessage: WireMessage.ButtonAction) {
        logger.info("Received event: onNewButtonAction")
    }

    open suspend fun onNewButtonActionSuspending(wireMessage: WireMessage.ButtonAction) {
        logger.info("Received event: onNewButtonActionSuspending")
    }

    open suspend fun onNewButtonActionConfirmation(
        wireMessage: WireMessage.ButtonActionConfirmation
    ) {
        logger.info("Received event: onNewButtonActionConfirmation")
    }

    open suspend fun onNewButtonActionConfirmationSuspending(
        wireMessage: WireMessage.ButtonActionConfirmation
    ) {
        logger.info("Received event: onNewButtonActionConfirmationSuspending: $wireMessage")
    }

    /**
     * One or more users have joined a conversation accessible by the Wire App.
     * This event is triggered when the App is already in the conversation and new users joins,
     * or when other users join the conversation at the same time as the App.
     */
    open fun onMemberJoin(
        conversationId: QualifiedId,
        members: List<ConversationMember>
    ) {
        logger.info("Received event: onMemberJoin")
    }

    /**
     * One or more users have left a conversation accessible by the Wire App.
     */
    open fun onMemberLeave(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        logger.info("Received event: onMemberLeave")
    }
}
