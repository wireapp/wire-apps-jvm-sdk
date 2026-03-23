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
import com.wire.sdk.model.WireMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * This example demonstrates how to automatically download an asset when it's received in a conversation.
 * When an asset message is received, the app will download the asset and save it to the local file system.
 * After downloading, it will send a confirmation message back to the conversation.
 */
public class DownloadOnAssetReceivedExample extends WireEventsHandlerDefault {
    private static final Logger logger = LoggerFactory.getLogger(DownloadOnAssetReceivedExample.class);

    @Override
    public void onAssetMessageReceived(WireMessage.Asset assetMessage) {
        logger.info("Received Asset Message. conversationId: {}", assetMessage.conversationId());
        saveAsset(assetMessage);
        informChannelAfterDownload(assetMessage);
    }

    private void saveAsset(WireMessage.Asset wireMessage) {
        final var remoteData = wireMessage.remoteData();
        if (remoteData == null) {
            return;
        }

        final var assetResource = getManager().downloadAsset(remoteData);
        final var fileName = (wireMessage.name() == null || wireMessage.name().isBlank())
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

    private void informChannelAfterDownload(WireMessage.Asset wireMessage) {
        final WireMessage infoMessage = WireMessage.Text.create(
                wireMessage.conversationId(),
                "ℹ️ I've downloaded the asset you sent. The file name is '" + wireMessage.name() + "'.",
                List.of(),
                List.of(),
                null
        );

        getManager().sendMessage(infoMessage);
    }
}
