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

package com.wire.sdk.sample.examples;

import com.wire.sdk.WireEventsHandlerDefault;
import com.wire.sdk.model.ConversationMember;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This example demonstrates how to create a one-to-one conversation with a user who
 * just joined a conversation and send them a welcome message in that one-to-one conversation.
 */
public class CreateOneToOneConversationWithNewJoiner extends WireEventsHandlerDefault {
    private static final Logger logger = LoggerFactory.getLogger(CreateOneToOneConversationWithNewJoiner.class);

    @Override
    public void onUserJoinedConversation(@NotNull QualifiedId conversationId, @NotNull List<ConversationMember> members) {
        logger.info("User(s) joined conversation. conversationId: {}, membersCount: {}", conversationId, members.size());

        members.forEach(member -> {
            final QualifiedId oneToOneConversationId = createOneToOneConversation(member.userId());
            sendMessageToOneToOneConversation(oneToOneConversationId);
        });
    }

    private QualifiedId createOneToOneConversation(QualifiedId userId) {
        return getManager().createOneToOneConversation(userId);
    }

    private void sendMessageToOneToOneConversation(QualifiedId oneToOneConversationId) {
        getManager().sendMessage(WireMessage.Text.create(
                oneToOneConversationId,
                "👋 Hey! I created this 1-1 conversation to welcome you! Feel free to ask me anything here.",
                List.of(),
                List.of(),
                null));
    }
}
