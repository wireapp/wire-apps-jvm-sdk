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

import com.wire.sdk.model.AssetResource;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import com.wire.sdk.model.asset.AssetRetention;
import com.wire.sdk.service.WireApplicationManager;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

class TestCommandProcessor {

    private final WireApplicationManager manager;

    TestCommandProcessor(WireApplicationManager applicationManager) {
        this.manager = applicationManager;
    }

    void process(TestCommand testCommand, WireMessage.Text wireMessage) {
        switch (testCommand) {
            case CREATE_ONE_TO_ONE_CONVERSATION -> processCreateOneToOneConversation(wireMessage);
            case CREATE_GROUP_CONVERSATION -> processCreateGroupConversation(wireMessage);
            case LEAVE_GROUP_CONVERSATION -> processLeaveGroupConversation(wireMessage);
            case DELETE_GROUP_CONVERSATION -> processDeleteGroupConversation(wireMessage);
            case CREATE_CHANNEL_CONVERSATION -> processCreateChannelConversation(wireMessage);
            case ADD_MEMBER_IN_CONVERSATION -> processAddMemberInConversation(wireMessage);
            case REMOVE_MEMBER_FROM_CONVERSATION -> processRemoveMemberFromConversation(wireMessage);
            case ASSET_IMAGE -> processAssetImage(wireMessage);
            case ASSET_AUDIO -> processAssetAudio(wireMessage);
            case ASSET_VIDEO -> processAssetVideo(wireMessage);
            case ASSET_PDF_DOCUMENT -> replyWithSamplePDFDocument(wireMessage);
        }
    }

    private void processCreateOneToOneConversation(WireMessage.Text wireMessage) {
        // Expected message: `create-one2one-conversation [USER_ID] [DOMAIN]
        final var split = wireMessage.text().split(" ");
        final var userId = new QualifiedId(UUID.fromString(split[1]), split[2]);
        this.manager.createOneToOneConversation(userId);
    }

    private void processCreateGroupConversation(WireMessage.Text wireMessage) {
        // Expected message: `create-group-conversation [NAME] [USER_ID] [DOMAIN]`
        final var split = wireMessage.text().split(" ");
        final var userIds = List.of(new QualifiedId(UUID.fromString(split[2]), split[3]));
        this.manager.createGroupConversation(split[1], userIds);
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
        // Expected message: `create-channel-conversation [NAME] [USER_ID] [DOMAIN]`
        final var split = wireMessage.text().split(" ");
        final var userIds = List.of(new QualifiedId(UUID.fromString(split[2]), split[3]));
        this.manager.createChannelConversation(split[1], userIds);
    }

    private void processAddMemberInConversation(WireMessage.Text wireMessage) {
        // Expected message: `add-members-to-conversation [USER_ID] [DOMAIN]
        final var split = wireMessage.text().split(" ");
        final var members = List.of(new QualifiedId(UUID.fromString(split[2]), split[3]));
        this.manager.addMembersToConversation(
                wireMessage.conversationId(),
                members
        );
    }

    private void processRemoveMemberFromConversation(WireMessage.Text wireMessage) {
        // Expected message: `remove-members-from-conversation [USER_ID] [DOMAIN]
        final var split = wireMessage.text().split(" ");
        final var members = List.of(new QualifiedId(UUID.fromString(split[2]), split[3]));
        this.manager.addMembersToConversation(
                wireMessage.conversationId(),
                members
        );
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

}
