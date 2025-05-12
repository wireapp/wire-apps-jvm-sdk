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

package com.wire.integrations.jvm

import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.ConversationMember
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
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

    open fun onMessage(wireMessage: WireMessage.Text) {
        logger.info("Received event: onMessage")
    }

    /**
     * The app has been added to a conversation.
     *
     * @param conversation the conversation id with some extra data
     * @param members the members that got added, including the app and possibly other users
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

    open fun onAsset(wireMessage: WireMessage.Asset) {
        logger.info("Received event: onAsset")
    }

    open fun onComposite(wireMessage: WireMessage.Composite) {
        logger.info("Received event: onComposite")
    }

    open fun onButtonAction(wireMessage: WireMessage.ButtonAction) {
        logger.info("Received event: onButtonAction")
    }

    open fun onButtonActionConfirmation(wireMessage: WireMessage.ButtonActionConfirmation) {
        logger.info("Received event: onButtonActionConfirmation")
    }

    open fun onKnock(wireMessage: WireMessage.Knock) {
        logger.info("Received event: onKnock: $wireMessage")
    }

    open fun onLocation(wireMessage: WireMessage.Location) {
        logger.info("Received event: onLocation: $wireMessage")
    }

    /**
     * One or more users have joined a conversation accessible by the Wire App.
     * This event is triggered when the App is already in the conversation and new users joins.
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
