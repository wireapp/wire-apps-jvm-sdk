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
import com.wire.sdk.model.TeamId
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

    open suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        logger.debug("Received event: TextMessageReceived")
    }

    /**
     * The app has been added to a conversation.
     *
     * @param conversation the conversation id with some extra data
     * @param members the participants of the conversation, excluding the App
     */
    open suspend fun onAppAddedToConversation(
        conversation: Conversation,
        members: List<ConversationMember>
    ) {
        logger.debug("Received event: AppAddedToConversation")
    }

    /**
     * A user deleted a conversation accessible by the Wire App.
     */
    open suspend fun onConversationDeleted(conversationId: QualifiedId) {
        logger.debug("Received event: ConversationDeleted")
    }

    open suspend fun onAssetMessageReceived(assetMessage: WireMessage.Asset) {
        logger.debug("Received event: AssetMessageReceived")
    }

    open suspend fun onButtonClicked(buttonAction: WireMessage.ButtonAction) {
        logger.debug("Received event: ButtonClicked")
    }

    /**
     * A user has sent a ping event to a conversation.
     *
     * @param pingMessage the message received
     */
    open suspend fun onPingReceived(pingMessage: WireMessage.Ping) {
        logger.debug("Received event: PingReceived: {}", pingMessage)
    }

    open suspend fun onLocationMessageReceived(locationMessage: WireMessage.Location) {
        logger.debug("Received event: LocationMessageReceived: {}", locationMessage)
    }

    open suspend fun onMessageDeleted(deletedMessage: WireMessage.Deleted) {
        logger.debug("Received event: MessageDeleted: {}", deletedMessage)
    }

    open suspend fun onMessageDelivered(deliveredMessage: WireMessage.Receipt) {
        logger.debug("Received event: MessageDelivered: {}", deliveredMessage)
    }

    open suspend fun onTextMessageEdited(editedTextMessage: WireMessage.TextEdited) {
        logger.debug("Received event: TextMessageEdited: {}", editedTextMessage)
    }

    open suspend fun onMessageReactionReceived(reactionMessage: WireMessage.Reaction) {
        logger.debug("Received event: MessageReactionReceived: {}", reactionMessage)
    }

    open suspend fun onInCallReactionReceived(inCallEmoji: WireMessage.InCallEmoji) {
        logger.debug("Received event: InCallReactionReceived: {}", inCallEmoji)
    }

    open suspend fun onInCallHandRaiseReceived(inCallHandRaise: WireMessage.InCallHandRaise) {
        logger.debug("Received event: InCallHandRaiseReceived: {}", inCallHandRaise)
    }

    /**
     * One or more users have joined a conversation accessible by the Wire App.
     * This event is triggered when the App is already in the conversation and new users joins.
     */
    open suspend fun onUserJoinedConversation(
        conversationId: QualifiedId,
        members: List<ConversationMember>
    ) {
        logger.debug("Received event: UserJoinedConversation")
    }

    /**
     * One or more users have left a conversation accessible by the Wire App.
     */
    open suspend fun onUserLeftConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        logger.debug("Received event: UserLeftConversation")
    }

    /**
     * A user has joined the team.
     *
     * @param userId the user ID of the user who joined
     * @param teamId the ID of the team
     */
    open suspend fun onTeamMemberJoined(
        userId: QualifiedId,
        teamId: TeamId
    ) {
        logger.debug("Received event: TeamMemberJoined")
    }
}
