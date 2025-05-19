/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.integrations.sample

import com.wire.integrations.jvm.WireAppSdk
import com.wire.integrations.jvm.WireEventsHandlerSuspending
import com.wire.integrations.jvm.model.AssetResource
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.asset.AssetRetention
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID

private val logger = LoggerFactory.getLogger("WireAppSdkSample")

fun main() {
    val wireAppSdk = WireAppSdk(
        applicationId = UUID.randomUUID(),
        apiToken = "myApiToken",
        apiHost = "https://nginz-https.chala.wire.link",
        cryptographyStoragePassword = "myDummyPassword",
        object : WireEventsHandlerSuspending() {
            override suspend fun onMessage(wireMessage: WireMessage.Text) {
                logger.info("Received Text Message : $wireMessage")

                if (wireMessage.text.contains("asset")) {
                    val resourcePath = javaClass.classLoader.getResource("banana-icon.png")?.path
                        ?: throw IllegalStateException("Test resource 'banana-icon.png' not found")
                    val originalData = File(resourcePath).readBytes()

                    manager.uploadAndSendMessageSuspending(
                        conversationId = wireMessage.conversationId,
                        asset = AssetResource(originalData),
                        mimeType = "image/png",
                        retention = AssetRetention.VOLATILE
                    )
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

                val message = WireMessage.Text.create(
                    conversationId = wireMessage.conversationId,
                    text = "${wireMessage.text} -- Sent from the SDK",
                    mentions = wireMessage.mentions
                )

                // Sending a Read Receipt for the received message
                val receipt = WireMessage.Receipt.create(
                    conversationId = wireMessage.conversationId,
                    type = WireMessage.Receipt.Type.READ,
                    messages = listOf(wireMessage.id.toString())
                )

                manager.sendMessageSuspending(message = message)
                manager.sendMessageSuspending(message = receipt)
            }

            override suspend fun onAsset(wireMessage: WireMessage.Asset) {
                logger.info("Received Asset Message : $wireMessage")

                val message = WireMessage.Text.create(
                    conversationId = wireMessage.conversationId,
                    text = "Received Asset : ${wireMessage.name}"
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

            override suspend fun onComposite(wireMessage: WireMessage.Composite) {
                logger.info("Received Composite Message : $wireMessage")

                logger.info("Received Composite Items:")
                wireMessage.items.forEach {
                    logger.info("Composite Item: $it")
                }
            }

            override suspend fun onButtonAction(wireMessage: WireMessage.ButtonAction) {
                logger.info("Received ButtonAction Message : $wireMessage")
            }

            override suspend fun onButtonActionConfirmation(wireMessage: WireMessage.ButtonActionConfirmation) {
                logger.info("Received ButtonActionConfirmation Message : $wireMessage")
            }

            override suspend fun onKnock(wireMessage: WireMessage.Knock) {
                logger.info("Received onKnockSuspending Message : $wireMessage")

                val knock = WireMessage.Knock.create(
                    conversationId = wireMessage.conversationId,
                    hotKnock = true
                )

                manager.sendMessageSuspending(message = knock)
            }

            override suspend fun onLocation(wireMessage: WireMessage.Location) {
                logger.info("Received onLocationSuspending Message : $wireMessage")

                val message = WireMessage.Text.create(
                    conversationId = wireMessage.conversationId,
                    text = "Received Location\n\nLatitude: ${wireMessage.latitude}\n\nLongitude: ${wireMessage.longitude}\n\nName: ${wireMessage.name}\n\nZoom: ${wireMessage.zoom}"
                )

                manager.sendMessageSuspending(message = message)
            }

            override suspend fun onDeletedMessage(wireMessage: WireMessage.Deleted) {
                logger.info("Received onDeletedMessageSuspending Message: $wireMessage")

                val message = WireMessage.Text.create(
                    conversationId = wireMessage.conversationId,
                    text = "Deleted Messaged with ID : ${wireMessage.messageId}"
                )

                manager.sendMessageSuspending(message = message)
            }
        }
    )

    logger.info("Starting Wire Apps SDK...")
    wireAppSdk.startListening() // Will keep a thread running in the background until explicitly stopped
    val applicationManager = wireAppSdk.getApplicationManager()

    applicationManager.getStoredTeams().forEach {
        logger.info("Team: $it")
    }
    applicationManager.getStoredConversations().forEach {
        logger.info("Conversation: $it")
    }
    logger.info("Wire backend domain: ${applicationManager.getBackendConfiguration().domain}")

    // Use wireAppSdk.stop() to stop the SDK or just stop it with Ctrl+C/Cmd+C
}
