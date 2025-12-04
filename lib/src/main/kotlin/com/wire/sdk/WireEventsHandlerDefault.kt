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

import com.wire.sdk.model.Conversation
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import org.slf4j.LoggerFactory

/**
 * Create a subclass overriding the methods in this class, and pass it
 * during SDK initialization to handle Wire events.
 *
 * Default version, blocking functions for Java or non-suspending Kotlin code.
 */
@Suppress("TooManyFunctions")
abstract class WireEventsHandlerDefault : WireEventsHandler() {
    private val logger = LoggerFactory.getLogger(this::class.java)

    open fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        logger.debug("Received event: TextMessageReceived")
    }

    /**
     * The app has been added to a conversation.
     *
     * @param conversation the conversation id with some extra data
     * @param members the participants of the conversation, excluding the App
     */
    open fun onAppAddedToConversation(
        conversation: Conversation,
        members: List<ConversationMember>
    ) {
        logger.debug("Received event: AppAddedToConversation")
    }

    /**
     * A user deleted a conversation accessible by the Wire App.
     */
    open fun onConversationDeleted(conversationId: QualifiedId) {
        logger.debug("Received event: ConversationDeleted")
    }

    open fun onAssetMessageReceived(wireMessage: WireMessage.Asset) {
        logger.debug("Received event: AssetMessageReceived")
    }

    open fun onCompositeMessageReceived(wireMessage: WireMessage.Composite) {
        logger.debug("Received event: CompositeMessageReceived")
    }

    open fun onButtonClicked(wireMessage: WireMessage.ButtonAction) {
        logger.debug("Received event: ButtonClicked")
    }

    open fun onButtonClickConfirmed(wireMessage: WireMessage.ButtonActionConfirmation) {
        logger.debug("Received event: ButtonClickConfirmed")
    }

    open fun onPingReceived(wireMessage: WireMessage.Ping) {
        logger.debug("Received event: PingReceived: {}", wireMessage)
    }

    open fun onLocationMessageReceived(wireMessage: WireMessage.Location) {
        logger.debug("Received event: LocationMessageReceived: {}", wireMessage)
    }

    open fun onMessageDeleted(wireMessage: WireMessage.Deleted) {
        logger.debug("Received event: MessageDeleted: {}", wireMessage)
    }

    open fun onMessageDelivered(wireMessage: WireMessage) {
        logger.debug("Received event: MessageDelivered: {}", wireMessage)
    }

    open fun onTextMessageEdited(wireMessage: WireMessage.TextEdited) {
        logger.debug("Received event: TextMessageEdited: {}", wireMessage)
    }

    open fun onCompositeMessageEdited(wireMessage: WireMessage.CompositeEdited) {
        logger.debug("Received event: CompositeMessageEdited: {}", wireMessage)
    }

    open fun onMessageReactionReceived(wireMessage: WireMessage.Reaction) {
        logger.debug("Received event: MessageReactionReceived: {}", wireMessage)
    }

    open fun onInCallReactionReceived(wireMessage: WireMessage.InCallEmoji) {
        logger.debug("Received event: InCallReactionReceived: {}", wireMessage)
    }

    open fun onInCallHandRaiseReceived(wireMessage: WireMessage.InCallHandRaise) {
        logger.debug("Received event: InCallHandRaiseReceived: {}", wireMessage)
    }

    /**
     * One or more users have joined a conversation accessible by the Wire App.
     * This event is triggered when the App is already in the conversation and new users joins.
     */
    open fun onUserJoinedConversation(
        conversationId: QualifiedId,
        members: List<ConversationMember>
    ) {
        logger.debug("Received event: UserJoinedConversation")
    }

    /**
     * One or more users have left a conversation accessible by the Wire App.
     */
    open fun onUserLeftConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        logger.debug("Received event: UserLeftConversation")
    }
}
