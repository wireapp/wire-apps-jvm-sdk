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

package com.wire.sdk

import com.wire.sdk.model.ConversationData
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import org.slf4j.LoggerFactory

/**
 * Create a subclass overriding the methods in this class, and pass it
 * during SDK initialization to handle Wire events.
 *
 * Suspending version, for Kotlin clients using coroutines.
 */
@Suppress("TooManyFunctions")
abstract class WireEventsHandlerSuspending : WireEventsHandler() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    open suspend fun onMessage(wireMessage: WireMessage.Text) {
        logger.info("Received event: onMessage")
    }

    /**
     * The app has been added to a conversation.
     *
     * @param conversation the conversation id with some extra data
     * @param members the members that got added, including the app and possibly other users
     */
    open suspend fun onConversationJoin(
        conversation: ConversationData,
        members: List<ConversationMember>
    ) {
        logger.info("Received event: onConversationJoin")
    }

    /**
     * A user deleted a conversation accessible by the Wire App.
     */
    open suspend fun onConversationDelete(conversationId: QualifiedId) {
        logger.info("Received event: onConversationDelete")
    }

    open suspend fun onAsset(wireMessage: WireMessage.Asset) {
        logger.info("Received event: onAsset")
    }

    open suspend fun onComposite(wireMessage: WireMessage.Composite) {
        logger.info("Received event: onComposite")
    }

    open suspend fun onButtonAction(wireMessage: WireMessage.ButtonAction) {
        logger.info("Received event: onButtonAction")
    }

    open suspend fun onButtonActionConfirmation(wireMessage: WireMessage.ButtonActionConfirmation) {
        logger.info("Received event: onButtonActionConfirmation: $wireMessage")
    }

    /**
     * A user has sent a knock (also called "ping") event to a conversation.
     *
     * @param wireMessage the message received
     */
    open suspend fun onKnock(wireMessage: WireMessage.Knock) {
        logger.info("Received event: onKnock: $wireMessage")
    }

    open suspend fun onLocation(wireMessage: WireMessage.Location) {
        logger.info("Received event: onLocation: $wireMessage")
    }

    open suspend fun onDeletedMessage(wireMessage: WireMessage.Deleted) {
        logger.info("Received event: onDeletedMessage: $wireMessage")
    }

    open suspend fun onReceiptConfirmation(wireMessage: WireMessage) {
        logger.info("Received event: onReceiptConfirmation: $wireMessage")
    }

    open suspend fun onTextEdited(wireMessage: WireMessage.TextEdited) {
        logger.info("Received event: onTextEdited: $wireMessage")
    }

    open suspend fun onCompositeEdited(wireMessage: WireMessage.CompositeEdited) {
        logger.info("Received event: onCompositeEdited: $wireMessage")
    }

    open suspend fun onReaction(wireMessage: WireMessage.Reaction) {
        logger.info("Received event: onReaction: $wireMessage")
    }

    open suspend fun onInCallEmoji(wireMessage: WireMessage.InCallEmoji) {
        logger.info("Received event: onInCallEmoji: $wireMessage")
    }

    open suspend fun onInCallHandRaise(wireMessage: WireMessage.InCallHandRaise) {
        logger.info("Received event: onInCallHandRaise: $wireMessage")
    }

    /**
     * One or more users have joined a conversation accessible by the Wire App.
     * This event is triggered when the App is already in the conversation and new users joins.
     */
    open suspend fun onMemberJoin(
        conversationId: QualifiedId,
        members: List<ConversationMember>
    ) {
        logger.info("Received event: onMemberJoin")
    }

    /**
     * One or more users have left a conversation accessible by the Wire App.
     */
    open suspend fun onMemberLeave(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        logger.info("Received event: onMemberLeave")
    }
}
