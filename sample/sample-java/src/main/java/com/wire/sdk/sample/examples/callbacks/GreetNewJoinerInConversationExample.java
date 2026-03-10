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
import com.wire.sdk.exception.WireException;
import com.wire.sdk.model.ConversationMember;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This example demonstrates how to greet new joiners in a conversation by sending a welcome message when they join.
 * It listens for the event of users joining a conversation and sends a personalized greeting message to each new member.
 */
public class GreetNewJoinerInConversationExample extends WireEventsHandlerDefault {
    private static final Logger logger = LoggerFactory.getLogger(GreetNewJoinerInConversationExample.class);

    @Override
    public void onUserJoinedConversation(@NotNull QualifiedId conversationId, @NotNull List<ConversationMember> members) {
        logger.info("User(s) joined conversation. conversationId: {}, membersCount: {}", conversationId, members.size());
        members.forEach(member -> {
            try {
                final var name = getManager().getUser(member.userId()).getName();
                welcomeTheNewJoiner(conversationId, name);
            } catch (WireException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void welcomeTheNewJoiner(QualifiedId conversationId, String name) {
        final WireMessage message = WireMessage.Text.create(
                conversationId,
                "👋Hey " + name + "! Great to see you here!",
                List.of(),
                List.of(),
                null);

        getManager().sendMessage(message);
        logger.info("Welcome message sent to new joiner. conversationId: {}", conversationId);
    }
}
