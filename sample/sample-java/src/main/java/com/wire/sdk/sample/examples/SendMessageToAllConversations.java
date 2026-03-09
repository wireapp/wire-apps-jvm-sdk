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

import com.wire.sdk.WireAppSdk;
import com.wire.sdk.model.WireMessage;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * This example demonstrates how to send a broadcast announcement message to all stored conversations
 * every 5 seconds, for a total of 10 times.
 */
public class SendMessageToAllConversations {
    final static UUID MY_APPLICATION_ID = UUID.fromString(System.getenv("WIRE_SDK_APPLICATION_ID"));
    final static String WIRE_API_TOKEN = System.getenv("WIRE_SDK_API_TOKEN");
    final static String WIRE_API_HOST = System.getenv("WIRE_SDK_API_HOST");

    private static WireAppSdk app;

    public static void main(String[] args) {
        new SendMessageToAllConversations().initApp();
    }

    private void initApp() {
        byte[] secureKey = new byte[32];
        Arrays.fill(secureKey, (byte) 1);
        app = new WireAppSdk(MY_APPLICATION_ID, WIRE_API_TOKEN, WIRE_API_HOST,
                secureKey,
                new NoOpWireEventsHandler()); // We use a no-op event handler since we don't need to react to any events in this example
        app.startListening();

        broadcastToAllConversations();
    }

    /**
     * This method sends a broadcast announcement message
     * to all stored conversations every 5 seconds, for a total of 10 times.
     */
    private void broadcastToAllConversations() {
        for (int i = 0; i < 10; i++) {
            for (var conversation : app.getApplicationManager().getStoredConversations()) {
                var message = WireMessage.Text.create(
                        conversation.id(),
                        "📣 **[Broadcast announcement]**" +
                                "\n🚧 Maintenance is scheduled for 09.10.2035 from 01:00 to 03:00 UTC. " +
                                "During this time, the service may be unavailable.",
                        List.of(),
                        List.of(),
                        null);
                app.getApplicationManager().sendMessage(message);
            }

            try {
                Thread.sleep(5000); // Wait for 5 seconds before sending the next announcement
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

    }

}
