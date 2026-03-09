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

package com.wire.sdk.sample.usecase;

import com.wire.sdk.WireEventsHandlerDefault;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendPing extends WireEventsHandlerDefault {
    private static final Logger logger = LoggerFactory.getLogger(SendPing.class);

    @Override
    public void onTextMessageReceived(@NotNull WireMessage.Text wireMessage) {
        if (wireMessage.text().toLowerCase().contains("ping me")) {
            sendPing(wireMessage.conversationId());
        }
    }

    private void sendPing(QualifiedId conversationId) {
        final WireMessage ping = WireMessage.Ping.create(
                conversationId,
                null
        );

        getManager().sendMessage(ping);
        logger.info("Ping sent. conversationId: {}", conversationId);
    }
}
