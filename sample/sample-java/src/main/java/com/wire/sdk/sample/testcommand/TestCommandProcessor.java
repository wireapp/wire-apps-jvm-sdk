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

package com.wire.sdk.sample.testcommand;

import com.wire.sdk.exception.WireException;
import com.wire.sdk.model.AssetResource;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import com.wire.sdk.model.WireUser;
import com.wire.sdk.model.asset.AssetRetention;
import com.wire.sdk.model.http.conversation.ConversationRole;
import com.wire.sdk.service.WireApplicationManager;

import kotlin.time.Clock;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

class TestCommandProcessor {

    private static final long EPHEMERAL_MSG_EXPIRE_MILLIS = 10_000L;
    private static final long MESSAGE_UPDATE_DELAY_MILLIS = 3_000L;

    private final WireApplicationManager manager;

    TestCommandProcessor(WireApplicationManager applicationManager) {
        this.manager = applicationManager;
    }

    private void sendText(QualifiedId conversationId, String text) {
        this.manager.sendMessage(WireMessage.Text.create(
                conversationId,
                text,
                List.of(), List.of(), null));
    }

    private void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    void process(TestCommand testCommand, WireMessage.Text wireMessage) {
        switch (testCommand) {
            case CREATE_ONE_TO_ONE_CONVERSATION -> processCreateOneToOneConversation(wireMessage);
            case CREATE_GROUP_CONVERSATION -> processCreateGroupConversation(wireMessage);
            case LEAVE_GROUP_CONVERSATION -> processLeaveGroupConversation(wireMessage);
            case DELETE_GROUP_CONVERSATION -> processDeleteGroupConversation(wireMessage);
            case CREATE_CHANNEL_CONVERSATION -> processCreateChannelConversation(wireMessage);
            case ADD_MEMBER_IN_CONVERSATION -> processAddMemberInConversation(wireMessage);
            case REMOVE_MEMBER_FROM_CONVERSATION ->
                    processRemoveMemberFromConversation(wireMessage);
            case UPDATE_MEMBER_ROLE -> processUpdateMemberRole(wireMessage);
            case GET_USER_DATA -> processGetUserData(wireMessage);
            case GET_USERS -> processGetUsers(wireMessage);
            case GET_CONVERSATIONS -> processGetConversations(wireMessage);
            case GET_CONVERSATION_MEMBERS -> processGetConversationMembers(wireMessage);
            case SEND_ASSET_IMAGE -> processAssetImage(wireMessage);
            case SEND_ASSET_AUDIO -> processAssetAudio(wireMessage);
            case SEND_ASSET_VIDEO -> processAssetVideo(wireMessage);
            case ASSET_PDF_DOCUMENT -> replyWithSamplePDFDocument(wireMessage);
            case SEARCH_USER -> processSearchUser(wireMessage);
            case TEST_DELETED_MESSAGE -> processTestDeletedMessage(wireMessage);
            case TEST_EDIT_TEXT -> processTestEditText(wireMessage);
            case TEST_EDIT_COMPOSITE -> processTestEditComposite(wireMessage);
            case SEND_EPHEMERAL_TEXT -> processSendEphemeralText(wireMessage);
            case SEND_EPHEMERAL_PING -> processSendEphemeralPing(wireMessage);
            case SEND_COMPOSITE_MESSAGE -> processSendCompositeMessage(wireMessage);
            case SEND_LOCATION_MESSAGE -> processSendLocationMessage(wireMessage);
            case SEND_EPHEMERAL_LOCATION_MESSAGE ->
                    processSendEphemeralLocationMessage(wireMessage);
        }
    }

    private void processCreateOneToOneConversation(WireMessage.Text wireMessage) {
        // Expected message: `create-onetoone-conversation [USER_ID] [DOMAIN]
        final var split = wireMessage.text().split(" ");
        final var userId = new QualifiedId(UUID.fromString(split[1]), split[2]);
        this.manager.createOneToOneConversation(userId);
    }

    private void processCreateGroupConversation(WireMessage.Text wireMessage) {
        // Expected message: `create-group-conversation [NAME] [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        final var split = wireMessage.text().split(" ");

        final var name = split[1];
        final var userIds = new ArrayList<QualifiedId>();

        for (int i = 2; i + 1 < split.length; i += 2) {
            userIds.add(new QualifiedId(UUID.fromString(split[i]), split[i + 1]));
        }

        this.manager.createGroupConversation(name, userIds);
    }

    private void processLeaveGroupConversation(WireMessage.Text wireMessage) {
        // Expected message: `leave-group-conversation`
        this.manager.leaveConversation(wireMessage.conversationId());
    }

    private void processDeleteGroupConversation(WireMessage.Text wireMessage) {
        // Expected message: `delete-group-conversation`
        this.manager.deleteConversation(wireMessage.conversationId());
    }

    private void processCreateChannelConversation(WireMessage.Text wireMessage) {
        // Expected message: `create-channel-conversation [NAME] [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        final var split = wireMessage.text().split(" ");

        final var name = split[1];
        final var userIds = new ArrayList<QualifiedId>();

        for (int i = 2; i + 1 < split.length; i += 2) {
            userIds.add(new QualifiedId(UUID.fromString(split[i]), split[i + 1]));
        }

        this.manager.createChannelConversation(name, userIds);
    }

    private void processAddMemberInConversation(WireMessage.Text wireMessage) {
        // Expected message: `add-members-to-conversation [USER_ID] [DOMAIN]
        final var split = wireMessage.text().split(" ");
        final var members = List.of(new QualifiedId(UUID.fromString(split[1]), split[2]));
        this.manager.addMembersToConversation(
                wireMessage.conversationId(),
                members
        );
    }

    private void processRemoveMemberFromConversation(WireMessage.Text wireMessage) {
        // Expected message: `remove-members-from-conversation [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        final String[] split = wireMessage.text().split(" ");
        final List<QualifiedId> members = new ArrayList<>();

        // Start at index 1, increment by 2 to capture pairs of UUID and Domain
        for (int i = 1; i < split.length; i += 2) {
            // Basic check to ensure we have a Domain for the current UUID
            if (i + 1 < split.length) {
                var userId = UUID.fromString(split[i]);
                var domain = split[i + 1];
                members.add(new QualifiedId(userId, domain));
            }
        }

        if (!members.isEmpty()) {
            this.manager.removeMembersFromConversation(
                    wireMessage.conversationId(),
                    members
            );
        }
    }

    private void processUpdateMemberRole(WireMessage.Text wireMessage) {
        // Expected message: `update-member-role [USER_ID] [DOMAIN] [ROLE]`
        final var split = wireMessage.text().split(" ");
        if (split.length != 4) {
            sendText(wireMessage.conversationId(),
                    "⚠️ Usage: update-member-role [USER_ID] [DOMAIN] [ROLE]");
            return;
        }

        final var newRole = ConversationRole.Companion.fromApi(split[3]);
        if (newRole == ConversationRole.UNKNOWN) {
            sendText(wireMessage.conversationId(),
                    "⚠️ Unknown role '" + split[3] + "'. Expected 'wire_admin' or 'wire_member'.");
            return;
        }

        final var userId = new QualifiedId(UUID.fromString(split[1]), split[2]);
        this.manager.updateConversationMemberRole(
                wireMessage.conversationId(),
                userId,
                newRole
        );
    }

    private void processGetUserData(WireMessage.Text wireMessage) {
        // Expected message: `get-user-data [USER_ID] [DOMAIN]`
        final var split = wireMessage.text().split(" ");
        if (split.length != 3) {
            sendText(wireMessage.conversationId(),
                    "⚠️ Usage: get-user-data [USER_ID] [DOMAIN]");
            return;
        }

        final var userId = new QualifiedId(UUID.fromString(split[1]), split[2]);

        try {
            sendText(wireMessage.conversationId(), formatWireUser(this.manager.getUser(userId)));
        } catch (WireException e) {
            sendText(wireMessage.conversationId(),
                    "❌ Could not get the user data: " + e.getMessage());
        }
    }

    private void processGetUsers(WireMessage.Text wireMessage) {
        // Expected message: `get-users [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        final var split = wireMessage.text().split(" ");
        if (split.length < 3 || split.length % 2 == 0) {
            sendText(wireMessage.conversationId(),
                    "⚠️ Usage: get-users [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...");
            return;
        }

        final var userDataList = new ArrayList<String>();

        // Start at index 1, increment by 2 to capture pairs of UUID and Domain
        for (int i = 1; i + 1 < split.length; i += 2) {
            final var userId = new QualifiedId(UUID.fromString(split[i]), split[i + 1]);
            try {
                userDataList.add(formatWireUser(this.manager.getUser(userId)));
            } catch (WireException e) {
                userDataList.add("❌ Could not get the data of " + userId.toFullString()
                        + ": " + e.getMessage());
            }
        }

        sendText(wireMessage.conversationId(), String.join("\n\n", userDataList));
    }

    private String formatWireUser(WireUser user) {
        return "👉 User data for " + user.id().id() + "@" + user.id().domain() + ":"
                + "\n        Name: " + user.name()
                + "\n        Email: " + (user.email() == null ? "N/A" : user.email())
                + "\n        Handle: " + (user.handle() == null ? "N/A" : user.handle())
                + "\n        Team: " + (user.teamId() == null ? "N/A" : user.teamId())
                + "\n        Deleted: " + (user.deleted() != null && user.deleted());
    }

    private void processGetConversations(WireMessage.Text wireMessage) {
        // Expected message: `get-conversations`
        final var conversations = this.manager.getConversations();

        final var conversationList = conversations.stream()
                .map(conversation -> "- "
                        + (conversation.name() == null ? "Unnamed" : conversation.name())
                        + " (" + conversation.id().id() + "@" + conversation.id().domain() + ")")
                .collect(Collectors.joining("\n"));

        sendText(wireMessage.conversationId(),
                "Conversations (" + conversations.size() + "):\n" + conversationList);
    }

    private void processGetConversationMembers(WireMessage.Text wireMessage) {
        // Expected message: `get-conversation-members [CONVERSATION_ID] [DOMAIN]`
        final var split = wireMessage.text().split(" ");
        if (split.length != 3) {
            sendText(wireMessage.conversationId(),
                    "⚠️ Usage: get-conversation-members [CONVERSATION_ID] [DOMAIN]");
            return;
        }

        final var conversationId = new QualifiedId(UUID.fromString(split[1]), split[2]);
        final var members = this.manager.getConversationMembers(conversationId);

        final var memberList = members.stream()
                .map(member -> "- " + member.userId().id() + "@" + member.userId().domain()
                        + " (" + member.role() + ")")
                .collect(Collectors.joining("\n"));

        sendText(wireMessage.conversationId(),
                "Members in conversation " + conversationId.toFullString()
                        + " (" + members.size() + "):\n" + memberList);
    }

    private void processAssetImage(WireMessage.Text wireMessage) {
        try {
            final var fileName = "celebrate-icon.png";
            final URL resourcePath = this.getClass().getClassLoader().getResource(fileName);
            if (resourcePath == null) {
                throw new IllegalStateException("Test resource " + fileName + " not found");
            }

            final File asset = new File(resourcePath.getPath());
            byte[] originalData = null;
            originalData = Files.readAllBytes(asset.toPath());
            this.manager.sendAsset(
                    wireMessage.conversationId(),
                    new AssetResource(originalData),
                    null,
                    asset.getName(),
                    "image/png",
                    AssetRetention.VOLATILE
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void processAssetAudio(WireMessage.Text wireMessage) {
        try {
            final var fileName = "sample_audio_6s.mp3";
            final URL resourcePath = this.getClass().getClassLoader().getResource(fileName);
            if (resourcePath == null) {
                throw new IllegalStateException("Test resource " + fileName + " not found");
            }

            final File asset = new File(resourcePath.getPath());
            byte[] originalData = Files.readAllBytes(asset.toPath());

            this.manager.sendAsset(
                    wireMessage.conversationId(),
                    new AssetResource(originalData),
                    getSampleAudioMetadata(),
                    asset.getName(),
                    "audio/mp3",
                    AssetRetention.VOLATILE
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private WireMessage.Asset.AssetMetadata.Audio getSampleAudioMetadata() {
        final var base64Loudness = "/////////////////////////////////////8u+iP///8TCo///////l//////7" +
                "q3x6cXWAhIGOfn6KjouUi4SQlZGdkIeSm5OenoWFioqJnYZ/hIqOlJOIjZOanJSNkp2jqf///////" +
                "///////////////////////////////i3v///+ytIf/////1rfp/////8CWiHuDhYubk4SKi5GgnZ" +
                "COjJOlmpiQjJKmop6Jio2Pjp+MiYqKjpuQhIOFi5KUfoKKkJX/";

        return new WireMessage.Asset.AssetMetadata.Audio(
                6000L,
                java.util.Base64.getDecoder().decode(base64Loudness)
        );
    }

    private void processAssetVideo(WireMessage.Text wireMessage) {
        try {
            final var fileName = "sample_video_5s.mp4";
            final URL resourcePath = this.getClass().getClassLoader().getResource(fileName);
            if (resourcePath == null) {
                throw new IllegalStateException("Test resource " + fileName + " not found");
            }

            final File asset = new File(resourcePath.getPath());
            byte[] originalData = Files.readAllBytes(asset.toPath());

            this.manager.sendAsset(
                    wireMessage.conversationId(),
                    new AssetResource(originalData),
                    getSampleVideoMetadata(),
                    asset.getName(),
                    "video/mp4",
                    AssetRetention.VOLATILE
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private WireMessage.Asset.AssetMetadata.Video getSampleVideoMetadata() {
        return new WireMessage.Asset.AssetMetadata.Video(
                1920,
                1080,
                5000L
        );
    }

    private void replyWithSamplePDFDocument(WireMessage.Text wireMessage) {
        try {
            final var fileName = "sample-pdf-1.pdf";
            final URL resourcePath = this.getClass().getClassLoader().getResource(fileName);
            if (resourcePath == null) {
                throw new IllegalStateException("Test resource " + fileName + " not found");
            }

            final File asset = new File(resourcePath.getPath());
            byte[] originalData = Files.readAllBytes(asset.toPath());

            this.manager.sendAsset(
                    wireMessage.conversationId(),
                    new AssetResource(originalData),
                    null,
                    asset.getName(),
                    "application/pdf",
                    AssetRetention.VOLATILE
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void processSearchUser(WireMessage.Text wireMessage) {
        // Expected message: `search-user [queryString]`
        final var split = wireMessage.text().split(" ", 2);
        if (split.length < 2 || split[1].isBlank()) {
            this.manager.sendMessage(WireMessage.Text.create(
                    wireMessage.conversationId(),
                    "⚠️ Usage: search-user [queryString]  (e.g. search-user alex)",
                    List.of(), List.of(), null)
            );
            return;
        }

        final var query = split[1].trim();
        final var users = this.manager.searchUsers(query, wireMessage.sender().domain(), 100);

        final var sb = new StringBuilder();
        sb.append("🔍 Search results for \"").append(query).append("\" ")
                .append("(").append(users.size()).append(" found):\n\n");

        if (users.isEmpty()) {
            sb.append("No users found.");
        } else {
            for (final var user : users) {
                sb.append("👤 ").append(user.name());

                if (user.handle() != null) {
                    sb.append(" (@").append(user.handle()).append(")");
                }

                sb.append("\n   ID: ").append(user.id().id())
                        .append(" @ ").append(user.id().domain());

                if (user.teamId() != null) {
                    sb.append("\n   Team: ").append(user.teamId());
                }

                if (user.email() != null) {
                    sb.append("\n   Email: ").append(user.email());
                }

                sb.append("\n\n");
            }
        }

        this.manager.sendMessage(WireMessage.Text.create(
                wireMessage.conversationId(),
                sb.toString(),
                List.of(), List.of(), null));
    }

    private void processTestDeletedMessage(WireMessage.Text wireMessage) {
        // Expected message: `test-deleted-message`
        // Sends a text message and then deletes it after 3 seconds.
        final var message = WireMessage.Text.create(
                wireMessage.conversationId(),
                "This message will be deleted in 3 seconds",
                List.of(), List.of(), null);

        final var messageId = this.manager.sendMessage(message);

        sleep(MESSAGE_UPDATE_DELAY_MILLIS);

        this.manager.sendMessage(WireMessage.Deleted.create(
                wireMessage.conversationId(),
                messageId));
    }

    private void processTestEditText(WireMessage.Text wireMessage) {
        // Expected message: `test-edit-text`
        // Sends a text message and then edits it after 3 seconds.
        final var messageId = this.manager.sendMessage(WireMessage.Text.create(
                wireMessage.conversationId(),
                "This message will be edited in 3 seconds",
                List.of(), List.of(), null));

        sleep(MESSAGE_UPDATE_DELAY_MILLIS);

        this.manager.sendMessage(WireMessage.TextEdited.create(
                messageId,
                wireMessage.conversationId(),
                "This message got edited",
                List.of()));
    }

    private void processTestEditComposite(WireMessage.Text wireMessage) {
        // Expected message: `test-edit-composite`
        // Sends a composite message and then removes its buttons one by one, every 3 seconds.
        final var buttons = new ArrayList<>(List.of(
                new WireMessage.Button("Button item that will be removed in 6 seconds"),
                new WireMessage.Button("Button item that will be removed in 3 seconds")
        ));

        var latestMessageId = this.manager.sendMessage(WireMessage.Composite.create(
                wireMessage.conversationId(),
                "Composite Title",
                List.copyOf(buttons)));

        while (!buttons.isEmpty()) {
            sleep(MESSAGE_UPDATE_DELAY_MILLIS);
            buttons.remove(buttons.size() - 1);

            latestMessageId = this.manager.sendMessage(WireMessage.CompositeEdited.create(
                    latestMessageId,
                    wireMessage.conversationId(),
                    "Composite Title",
                    List.copyOf(buttons)));
        }
    }

    private void processSendCompositeMessage(WireMessage.Text wireMessage) {
        // Expected message: `send-composite-message`
        this.manager.sendMessage(WireMessage.Composite.create(
                wireMessage.conversationId(),
                "Composite Title",
                List.of(
                        new WireMessage.Button("Button-001"),
                        new WireMessage.Button("Button-002")
                )));
    }

    private void processSendEphemeralText(WireMessage.Text wireMessage) {
        // Expected message: `send-ephemeral-text`
        this.manager.sendMessage(WireMessage.Text.create(
                wireMessage.conversationId(),
                "This is an Ephemeral Text message",
                List.of(), List.of(),
                EPHEMERAL_MSG_EXPIRE_MILLIS));
    }

    private void processSendEphemeralPing(WireMessage.Text wireMessage) {
        // Expected message: `send-ephemeral-ping`
        this.manager.sendMessage(WireMessage.Ping.create(
                wireMessage.conversationId(),
                EPHEMERAL_MSG_EXPIRE_MILLIS));
    }

    private void processSendLocationMessage(WireMessage.Text wireMessage) {
        // Expected message: `send-location-message`
        this.manager.sendMessage(WireMessage.Location.create(
                wireMessage.conversationId(),
                52.52527f,
                13.36923f,
                "Berlin Hauptbahnhof, 10557 Berlin",
                50,
                Clock.System.INSTANCE.now(),
                null));
    }

    private void processSendEphemeralLocationMessage(WireMessage.Text wireMessage) {
        // Expected message: `send-ephemeral-location-message`
        this.manager.sendMessage(WireMessage.Location.create(
                wireMessage.conversationId(),
                52.51615f,
                13.37827f,
                "Pariser Platz, 10117 Berlin",
                50,
                Clock.System.INSTANCE.now(),
                EPHEMERAL_MSG_EXPIRE_MILLIS));
    }

}
