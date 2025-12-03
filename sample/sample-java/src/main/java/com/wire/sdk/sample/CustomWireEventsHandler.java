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

package com.wire.sdk.sample;

import com.wire.sdk.WireEventsHandlerDefault;
import com.wire.sdk.exception.WireException;
import com.wire.sdk.model.ConversationData;
import com.wire.sdk.model.ConversationMember;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import com.wire.sdk.sample.testcommand.TestCommand;
import com.wire.sdk.sample.testcommand.TestCommandHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CustomWireEventsHandler extends WireEventsHandlerDefault {

    private static final Logger logger = LoggerFactory.getLogger(CustomWireEventsHandler.class);

    @Override
    public void onTextMessageReceived(@NotNull WireMessage.Text wireMessage) {
        logger.info("Received Text Message. conversationId:{}", wireMessage.conversationId());

        // ___ Case-1 : Process if the message is a test command
        final var testCommand = TestCommand.getCommand(wireMessage.text().split(" ")[0]);
        if (testCommand != null) {
            new TestCommandHandler(getManager()).handle(testCommand, wireMessage);
            return;
        }

        // ___ Case-2 : Send ephemeral message if the received message is ephemeral
        if (wireMessage.expiresAfterMillis() != null) {
            sendEphemeralTextMessage(
                    wireMessage.conversationId(),
                    "**This is a sample ephemeral message** -- Sent from the Sample-Java Ap");
            return;
        }


        // ___ Case-3: Normal text message

        // Send read receipt
        final var readReceipt = WireMessage.Receipt.create(
                wireMessage.conversationId(),
                WireMessage.Receipt.Type.READ,
                List.of(wireMessage.id().toString())
        );
        getManager().sendMessage(readReceipt);


        // Send reaction
        final var reaction = WireMessage.Reaction.create(
                wireMessage.conversationId(),
                wireMessage.id().toString(),
                Set.of("🧩", "☕️", "🎉")
        );
        getManager().sendMessage(reaction);

        sendSimpleReply(wireMessage.conversationId(),
                "Auto reply: " + wireMessage.text() + " -- Sent from the Sample-Java App",
                wireMessage);
    }

    @Override
    public void onAppAddedToConversation(@NotNull ConversationData conversation, @NotNull List<ConversationMember> members) {
        logger.info("App added to conversation. conversationId: {}, membersCount: {}", conversation.id(), members.size());
        welcomeTheChannel(conversation.id());
    }

    @Override
    public void onConversationDeleted(@NotNull QualifiedId conversationId) {
        logger.info("Conversation deleted. conversationId: {}", conversationId);
    }

    @Override
    public void onAssetMessageReceived(WireMessage.Asset wireMessage) {
        logger.info("Received Asset Message. conversationId: {}", wireMessage.conversationId());
        sendSimpleReply(wireMessage.conversationId(),
                "Received Asset:" + wireMessage.name(),
                wireMessage);
        saveAsset(wireMessage);
    }

    @Override
    public void onCompositeMessageReceived(WireMessage.Composite wireMessage) {
        logger.info("Received Composite Message. conversationId: {}", wireMessage.conversationId());
        wireMessage.items().forEach(item -> logger.info(" -> item: " + item));
    }

    @Override
    public void onButtonClicked(WireMessage.ButtonAction wireMessage) {
        super.onButtonClicked(wireMessage);
    }

    @Override
    public void onButtonClickConfirmed(WireMessage.ButtonActionConfirmation wireMessage) {
        super.onButtonClickConfirmed(wireMessage);
    }

    @Override
    public void onPingReceived(WireMessage.Ping wireMessage) {
        logger.info("Received Ping. conversationId: {}", wireMessage.conversationId());
        sendSimplePing(wireMessage.conversationId());
    }

    @Override
    public void onLocationMessageReceived(WireMessage.Location wireMessage) {
        logger.info("Received Location message. conversationId: {}", wireMessage.conversationId());
        final var reply =
                "Received Location: " + wireMessage.latitude() + ", " + wireMessage.longitude() +
                        "\nname: " + wireMessage.name() +
                        "\nzoom: " + wireMessage.zoom();

        sendSimpleReply(wireMessage.conversationId(), reply, wireMessage);
    }

    @Override
    public void onMessageDeleted(WireMessage.Deleted wireMessage) {
        logger.info("Received Message deletion. conversationId: {}", wireMessage.conversationId());
        final var message = "ℹ️Message deleted with id: " + wireMessage.messageId();
        sendSimpleTextMessage(wireMessage.conversationId(), message);
    }

    @Override
    public void onMessageDelivered(@NotNull WireMessage wireMessage) {
        super.onMessageDelivered(wireMessage);
    }

    @Override
    public void onTextMessageEdited(WireMessage.TextEdited wireMessage) {
        super.onTextMessageEdited(wireMessage);
    }

    @Override
    public void onCompositeMessageEdited(WireMessage.CompositeEdited wireMessage) {
        super.onCompositeMessageEdited(wireMessage);
    }

    @Override
    public void onMessageReactionReceived(WireMessage.Reaction wireMessage) {
        super.onMessageReactionReceived(wireMessage);
    }

    @Override
    public void onInCallReactionReceived(WireMessage.InCallEmoji wireMessage) {
        super.onInCallReactionReceived(wireMessage);
    }

    @Override
    public void onInCallHandRaiseReceived(WireMessage.InCallHandRaise wireMessage) {
        super.onInCallHandRaiseReceived(wireMessage);
    }

    @Override
    public void onUserJoinedConversation(@NotNull QualifiedId conversationId, @NotNull List<ConversationMember> members) {
        logger.info("User(s) joined conversation. conversationId: {}, membersCount: {}", conversationId, members.size());
        members.forEach(member -> {
            try {
                final var name = getManager().getUser(member.userId()).getName();
                welcomeTheNewJoiner(conversationId, name);
            } catch (WireException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onUserLeftConversation(@NotNull QualifiedId conversationId, @NotNull List<QualifiedId> members) {
        super.onUserLeftConversation(conversationId, members);
    }



    /*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
     * HELPER METHODS
     */

    private void saveAsset(WireMessage.Asset wireMessage) {
        final var remoteData = wireMessage.remoteData();
        if (remoteData == null) {
            return;
        }

        final var assetResource = getManager().downloadAsset(remoteData);
        final var fileName = wireMessage.name() == null
                ? "unknown-" + UUID.randomUUID()
                : wireMessage.name().trim();

        final File outputDir = new File("build/downloaded_assets/java_sample");
        outputDir.mkdirs();
        File outputFile = new File(outputDir, fileName);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(assetResource.getValue());
        } catch (IOException e) {
            logger.error("Failed to write asset file", e);
        }

        logger.info("Downloaded asset with size: {} bytes, saved to: {}",
                assetResource.getValue().length,
                outputFile.getAbsolutePath()
        );
    }

    private void welcomeTheNewJoiner(QualifiedId conversationId, String name) {
        sendSimpleTextMessage(conversationId,
                "🎉 **Hello " + name + "! Welcome to the conversation!** 👋");
        logger.info("Welcome message sent to new joiner. conversationId: {}", conversationId);
    }

    private void welcomeTheChannel(QualifiedId conversationId) {
        sendSimpleTextMessage(conversationId,
                "🧩 **Hello from Wire Integrations Team!** 👋\n" +
                        "This is a welcome message from the Sample-Java App.");
        logger.info("Welcome message sent. conversationId: {}", conversationId);
    }

    private void sendSimpleTextMessage(QualifiedId conversationId, String textMessage) {
        final WireMessage message = WireMessage.Text.create(
                conversationId,
                textMessage,
                List.of(),
                List.of(),
                null);

        getManager().sendMessage(message);
        logger.info("Message sent. conversationId: {}", conversationId);
    }

    private void sendEphemeralTextMessage(QualifiedId conversationId, String messageText) {
        final WireMessage message = WireMessage.Text.create(
                conversationId,
                messageText,
                List.of(),
                List.of(),
                10_000L); // Expires after 10 seconds

        getManager().sendMessage(message);
        logger.info("Ephemeral message sent. conversationId: {}", conversationId);
    }

    private void sendSimpleReply(QualifiedId conversationId, String messageText, WireMessage inReplyTo) {
        final WireMessage reply = WireMessage.Text.createReply(
                conversationId,
                messageText,
                List.of(),
                List.of(),
                inReplyTo,
                null);

        getManager().sendMessage(reply);
        logger.info("Reply sent. conversationId: {}", conversationId);
    }

    private void sendSimplePing(QualifiedId conversationId) {
        final WireMessage ping = WireMessage.Ping.create(conversationId, null);

        getManager().sendMessage(ping);
        logger.info("Ping sent. conversationId: {}", conversationId);
    }

}
