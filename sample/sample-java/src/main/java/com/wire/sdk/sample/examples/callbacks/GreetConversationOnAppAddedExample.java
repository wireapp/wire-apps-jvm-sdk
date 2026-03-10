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

package com.wire.sdk.sample.examples.callbacks;

import com.wire.sdk.WireEventsHandlerDefault;
import com.wire.sdk.model.Conversation;
import com.wire.sdk.model.ConversationMember;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This example demonstrates how to greet a conversation when the app is added to it.
 * The app listens for the event of being added to a conversation and sends a greeting message in response.
 */
public class GreetConversationOnAppAddedExample extends WireEventsHandlerDefault {
    private static final Logger logger = LoggerFactory.getLogger(GreetConversationOnAppAddedExample.class);

    @Override
    public void onAppAddedToConversation(@NotNull Conversation conversation, @NotNull List<ConversationMember> members) {
        logger.info("App added to conversation. conversationId: {}, membersCount: {}", conversation.id(), members.size());
        sendGreetingsToConversation(conversation.id());
    }

    private void sendGreetingsToConversation(QualifiedId conversationId) {
        final var text = "**Hey! Thanks for adding me in your conversation** 🙂";
        final WireMessage wireMessage = WireMessage.Text.create(
                conversationId,
                text,
                List.of(),
                List.of(),
                null);

        getManager().sendMessage(wireMessage);
        logger.info("Welcome message sent. conversationId: {}", conversationId);
    }
}
