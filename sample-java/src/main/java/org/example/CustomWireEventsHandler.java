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

package org.example;

import com.wire.integrations.jvm.WireEventsHandlerDefault;
import com.wire.integrations.jvm.model.QualifiedId;
import com.wire.integrations.jvm.model.WireMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CustomWireEventsHandler extends WireEventsHandlerDefault {

    private static final Logger logger = LoggerFactory.getLogger(CustomWireEventsHandler.class);

    @Override
    public void onMessage(@NotNull WireMessage.Text wireMessage) {
        logger.info("Message received. conversationId:{}", wireMessage.conversationId());

        final WireMessage reply = WireMessage.Text.createReply(
                wireMessage.conversationId(),
                wireMessage.text() + " -- Sent from the Sample-Java App",
                wireMessage.mentions(),
                wireMessage.linkPreviews(),
                wireMessage,
                null);

        getManager().sendMessage(reply);
        logger.info("Reply sent. conversationId:{}", wireMessage.conversationId());
    }
}
