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
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * This example demonstrates how to send an ephemeral message that will be automatically deleted after a specified duration.
 * When the app receives a text message containing "send me the password",
 * it responds with an ephemeral message that includes a password and expires after 10 seconds.
 */
public class SendEphemeralMessageExample extends WireEventsHandlerDefault {
    private static final Logger logger = LoggerFactory.getLogger(SendEphemeralMessageExample.class);

    @Override
    public void onTextMessageReceived(@NotNull WireMessage.Text wireMessage) {
        if (wireMessage.text().toLowerCase().contains("send me the password")) {
            sendEphemeralTextMessage(wireMessage.conversationId());
        }
    }

    private void sendEphemeralTextMessage(QualifiedId conversationId) {
        final WireMessage message = WireMessage.Text.create(
                conversationId,
                "My password is: 1234_5678. This message will be deleted in 10 seconds!!",
                List.of(),
                List.of(),
                10_000L); // Expires after 10 seconds

        getManager().sendMessage(message);
        logger.info("Ephemeral message sent. conversationId: {}", conversationId);
    }
}
