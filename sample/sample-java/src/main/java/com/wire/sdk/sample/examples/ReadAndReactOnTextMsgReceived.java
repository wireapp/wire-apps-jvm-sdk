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
import com.wire.sdk.model.WireMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class ReadAndReactOnTextMsgReceived extends WireEventsHandlerDefault {
    private static final Logger logger = LoggerFactory.getLogger(ReadAndReactOnTextMsgReceived.class);

    @Override
    public void onTextMessageReceived(@NotNull WireMessage.Text wireMessage) {
        // Step 1: Send a read receipt so the sender knows the message was seen
        sendReadReceipt(wireMessage);

        // Step 2: React to the message with emojis
        reactToMessage(wireMessage);
    }

    private void sendReadReceipt(WireMessage.Text wireMessage) {
        final WireMessage readReceipt = WireMessage.Receipt.create(
                wireMessage.conversationId(),
                WireMessage.Receipt.Type.READ,
                List.of(wireMessage.id().toString())
        );

        getManager().sendMessage(readReceipt);
    }

    private void reactToMessage(WireMessage.Text wireMessage) {
        final WireMessage reaction = WireMessage.Reaction.create(
                wireMessage.conversationId(),
                wireMessage.id().toString(),
                Set.of("🎉", "🙂", "🧩")
        );

        getManager().sendMessage(reaction);
    }
}
