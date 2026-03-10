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

package com.wire.sdk.sample.examples.standalone;

import com.wire.sdk.WireAppSdk;
import com.wire.sdk.model.QualifiedId;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

/**
 * This example collects all user IDs from the conversations that the app is involved.
 * And then creates a new group conversation with those users as members.
 */
public class CreateGroupConversationExample {
    final static UUID MY_APPLICATION_ID = UUID.fromString(System.getenv("WIRE_SDK_APPLICATION_ID"));
    final static String WIRE_API_TOKEN = System.getenv("WIRE_SDK_API_TOKEN");
    final static String WIRE_API_HOST = System.getenv("WIRE_SDK_API_HOST");

    private static WireAppSdk app;

    public static void main(String[] args) {
        new CreateGroupConversationExample().initApp();
    }

    private void initApp() {
        byte[] secureKey = new byte[32];
        Arrays.fill(secureKey, (byte) 1);
        app = new WireAppSdk(MY_APPLICATION_ID, WIRE_API_TOKEN, WIRE_API_HOST,
                secureKey,
                new NoOpWireEventsHandler()); // We use a no-op event handler since we don't need to react to any events in this example
        app.startListening();

        createGroupWithAll();
    }

    /**
     * This method creates a new group conversation with all users that are together with you in the same conversations.
     */
    private void createGroupWithAll() {
        app.getApplicationManager().createGroupConversation(
                "groupWithAll_" + System.currentTimeMillis(),
                getAllUsersInMyConversations().stream().toList());
    }

    /**
     * This method collects all user IDs from the conversations stored in the application.
     */
    private Set<QualifiedId> getAllUsersInMyConversations() {
        final Set<QualifiedId> userIds = new java.util.HashSet<>();

        // Iterate all stored conversations and collect all member user IDs
        for (var conversation : app.getApplicationManager().getStoredConversations()) {
            var members = app.getApplicationManager().getStoredConversationMembers(conversation.id());
            for (var member : members) {
                userIds.add(member.userId());
            }
        }

        return userIds;
    }

}
