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
import kotlin.time.Clock;

/**
 * Use Case: Reply with a location message when triggered by a keyword.
 *
 * <p>This sample demonstrates how to send a {@link WireMessage.Location} message
 * in response to an incoming text message containing a specific keyword.
 *
 * <p><b>Triggered by:</b> {@link #onTextMessageReceived(WireMessage.Text)}
 * when the message contains {@code "where is wire?"}
 *
 * <p><b>Actions performed:</b>
 * <ul>
 *   <li>Sends the location of Wire GmbH headquarters (Berlin, Germany)</li>
 * </ul>
 *
 * @see WireEventsHandlerDefault
 * @see WireMessage.Location
 */
public class SendWireLocationInfo extends WireEventsHandlerDefault {

    @Override
    public void onTextMessageReceived(WireMessage.Text wireMessage) {
        if (wireMessage.text().toLowerCase().contains("where is wire?")) {
            sendLocationInfo(wireMessage.conversationId());
        }
    }

    private void sendLocationInfo(QualifiedId conversationId) {
        final var locationInfoMessage = WireMessage.Location.create(
                conversationId,
                52.52401159f,
                13.40240811f,
                "Wire GmbH!",
                0,
                Clock.System.INSTANCE.now(),
                null
        );

        getManager().sendMessage(locationInfoMessage);
    }
}
