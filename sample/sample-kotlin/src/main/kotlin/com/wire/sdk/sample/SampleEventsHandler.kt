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

package com.wire.sdk.sample

import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.AssetResource
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.WireMessage.Asset.AssetMetadata
import com.wire.sdk.model.asset.AssetRetention
import java.io.File
import java.util.*
import org.slf4j.LoggerFactory

class SampleEventsHandler : WireEventsHandlerSuspending() {
    private val logger = LoggerFactory.getLogger("SampleEventsHandler")

    override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        logger.info("Received Text Message : $wireMessage")

        if (isCreateOneToOneConversation(text = wireMessage.text)) {
            processCreateOneToOneConversation(wireMessage = wireMessage)
            return
        }

        if (isCreateGroupConversation(text = wireMessage.text)) {
            processCreateGroupConversation(wireMessage = wireMessage)
            return
        }

        if (isCreateChannelConversation(text = wireMessage.text)) {
            processCreateChannelConversation(wireMessage = wireMessage)
            return
        }

        if (isAssetImage(text = wireMessage.text)) {
            processAssetImage(wireMessage = wireMessage)
            return
        }

        if (isAssetAudio(text = wireMessage.text)) {
            processAssetAudio(wireMessage = wireMessage)
            return
        }

        if (isAssetVideo(text = wireMessage.text)) {
            processAssetVideo(wireMessage = wireMessage)
            return
        }

        if (isAssetPDFDocumentTestMessage(text = wireMessage.text)) {
            replyWithSamplePDFDocument(wireMessage = wireMessage)
            return
        }

        // Sends an Ephemeral message if received message is Ephemeral
        wireMessage.expiresAfterMillis?.let {
            val ephemeralMessage = WireMessage.Text.create(
                conversationId = wireMessage.conversationId,
                text = "${wireMessage.text} -- Ephemeral Message sent from the SDK",
                mentions = wireMessage.mentions,
                expiresAfterMillis = 10_000
            )

            manager.sendMessageSuspending(message = ephemeralMessage)
            return
        }

        val message = WireMessage.Text.createReply(
            conversationId = wireMessage.conversationId,
            text = "${wireMessage.text} -- Sent from the SDK",
            mentions = wireMessage.mentions,
            originalMessage = wireMessage
        )

        // Sending a Read Receipt for the received message
        val receipt = WireMessage.Receipt.create(
            conversationId = wireMessage.conversationId,
            type = WireMessage.Receipt.Type.READ,
            messages = listOf(wireMessage.id.toString())
        )

        // Add a reaction emoji to the received message
        val reaction = WireMessage.Reaction.create(
            conversationId = wireMessage.conversationId,
            messageId = wireMessage.id.toString(),
            emojiSet = setOf("🧩")
        )

        manager.sendMessageSuspending(message = message)
        manager.sendMessageSuspending(message = receipt)
        manager.sendMessageSuspending(message = reaction)
    }

    override suspend fun onAssetMessageReceived(wireMessage: WireMessage.Asset) {
        logger.info("Received Asset Message : $wireMessage")

        val message = WireMessage.Text.createReply(
            conversationId = wireMessage.conversationId,
            text = "Received Asset : ${wireMessage.name}",
            originalMessage = wireMessage
        )

        manager.sendMessageSuspending(message = message)

        wireMessage.remoteData?.let { remoteData ->
            val asset = manager.downloadAssetSuspending(remoteData)
            val fileName = wireMessage.name ?: "unknown-${UUID.randomUUID()}"
            val outputDir = File("build/downloaded_assets").apply { mkdirs() }
            val outputFile = File(outputDir, fileName)
            outputFile.writeBytes(asset.value)
            logger.info("Downloaded asset with size: ${asset.value.size} bytes, saved to: ${outputFile.absolutePath}")
        }
    }

    override suspend fun onCompositeMessageReceived(wireMessage: WireMessage.Composite) {
        logger.info("Received Composite Message : $wireMessage")

        logger.info("Received Composite Items:")
        wireMessage.items.forEach {
            logger.info("Composite Item: $it")
        }
    }

    override suspend fun onButtonClicked(wireMessage: WireMessage.ButtonAction) {
        logger.info("Received ButtonAction Message : $wireMessage")
    }

    override suspend fun onButtonClickConfirmed(wireMessage: WireMessage.ButtonActionConfirmation) {
        logger.info("Received ButtonActionConfirmation Message : $wireMessage")
    }

    override suspend fun onPingReceived(wireMessage: WireMessage.Ping) {
        logger.info("Received Ping: $wireMessage")

        val ping = WireMessage.Ping.create(
            conversationId = wireMessage.conversationId
        )

        manager.sendMessageSuspending(message = ping)
    }

    override suspend fun onLocationMessageReceived(wireMessage: WireMessage.Location) {
        logger.info("Received Location Message : $wireMessage")

        val message = WireMessage.Text.createReply(
            conversationId = wireMessage.conversationId,
            text = "Received Location\n\nLatitude: ${wireMessage.latitude}\n\nLongitude: ${wireMessage.longitude}\n\nName: ${wireMessage.name}\n\nZoom: ${wireMessage.zoom}",
            originalMessage = wireMessage
        )

        manager.sendMessageSuspending(message = message)
    }

    override suspend fun onMessageDeleted(wireMessage: WireMessage.Deleted) {
        logger.info("Received Message Deletion event: $wireMessage")

        val message = WireMessage.Text.create(
            conversationId = wireMessage.conversationId,
            text = "Deleted Messaged with ID : ${wireMessage.messageId}"
        )

        manager.sendMessageSuspending(message = message)
    }

    private fun getSampleAudioMetadata(): AssetMetadata.Audio {
        val base64Loudness = "/////////////////////////////////////8u+iP///8TCo///////l//////7" +
                "q3x6cXWAhIGOfn6KjouUi4SQlZGdkIeSm5OenoWFioqJnYZ/hIqOlJOIjZOanJSNkp2jqf///////" +
                "///////////////////////////////i3v///+ytIf/////1rfp/////8CWiHuDhYubk4SKi5GgnZ" +
                "COjJOlmpiQjJKmop6Jio2Pjp+MiYqKjpuQhIOFi5KUfoKKkJX/"

        return AssetMetadata.Audio(
            durationMs = 6000L,
            normalizedLoudness = Base64.getDecoder().decode(base64Loudness)
        )
    }

    private fun isCreateOneToOneConversation(text: String): Boolean =
        text.startsWith("create-one2one-conversation")

    private fun isCreateGroupConversation(text: String): Boolean =
        text.startsWith("create-group-conversation")

    private fun isCreateChannelConversation(text: String): Boolean =
        text.startsWith("create-channel-conversation")

    private fun isAssetImage(text: String): Boolean =
        text.startsWith("asset-image")

    private fun isAssetAudio(text: String): Boolean =
        text.startsWith("asset-audio")

    private fun isAssetVideo(text: String): Boolean =
        text.startsWith("asset-video")

    private fun isAssetPDFDocumentTestMessage(text: String): Boolean =
        text.startsWith("asset-document-pdf")

    private fun processCreateOneToOneConversation(wireMessage: WireMessage.Text) {
        // Expected message: `create-one2one-conversation [USER_ID] [DOMAIN]
        val split = wireMessage.text.split(" ")

        manager.createOneToOneConversation(
            userId = QualifiedId(
                id = UUID.fromString(split[1]),
                domain = split[2]
            )
        )
    }

    private fun processCreateGroupConversation(wireMessage: WireMessage.Text) {
        // Expected message: `create-group-conversation [NAME] [USER_ID] [DOMAIN]`
        val split = wireMessage.text.split(" ")

        manager.createGroupConversation(
            name = split[1],
            userIds = listOf(
                QualifiedId(
                    id = UUID.fromString(split[2]),
                    domain = split[3]
                )
            )
        )
    }

    private fun processCreateChannelConversation(wireMessage: WireMessage.Text) {
        // Expected message: `create-channel-conversation [NAME] [USER_ID] [DOMAIN]`
        val split = wireMessage.text.split(" ")

        manager.createChannelConversation(
            name = split[1],
            userIds = listOf(
                QualifiedId(
                    id = UUID.fromString(split[2]),
                    domain = split[3]
                )
            )
        )
    }

    private suspend fun processAssetImage(wireMessage: WireMessage.Text) {
        val resourcePath = javaClass.classLoader.getResource("banana-icon.png")?.path
            ?: throw IllegalStateException("Test resource 'banana-icon.png' not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            name = asset.name,
            mimeType = "image/png",
            retention = AssetRetention.VOLATILE
        )
    }

    private suspend fun processAssetAudio(wireMessage: WireMessage.Text) {
        val resourcePath = javaClass.classLoader.getResource("sample_audio_6s.mp3")?.path
            ?: throw IllegalStateException("Test resource 'sample_audio_6s.mp3' not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            metadata = getSampleAudioMetadata(),
            name = asset.name,
            mimeType = "audio/mp3",
            retention = AssetRetention.VOLATILE
        )
    }

    private suspend fun processAssetVideo(wireMessage: WireMessage.Text) {
        val resourcePath = javaClass.classLoader.getResource("sample_video_5s.mp4")?.path
            ?: throw IllegalStateException("Test resource 'sample_video_5s.mp4' not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            metadata = AssetMetadata.Video(
                width = 1920,
                height = 1080,
                durationMs = 6000L
            ),
            name = asset.name,
            mimeType = "video/mp4",
            retention = AssetRetention.VOLATILE
        )
    }

    private suspend fun replyWithSamplePDFDocument(wireMessage: WireMessage.Text) {
        val fileName = "sample-pdf-1.pdf"
        val resourcePath = javaClass.classLoader.getResource(fileName)?.path
            ?: throw IllegalStateException("Test resource $fileName not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            metadata = null,
            name = asset.name,
            mimeType = "application/pdf",
            retention = AssetRetention.VOLATILE
        )
    }
}
