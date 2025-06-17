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
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.asset.AssetRetention
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.File
import java.util.UUID
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.net.HttpURLConnection
import com.wire.integrations.sample.*

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
                    text = "\uD83D\uDCCE Received Asset: ${wireMessage.name}\nStarting processing...\n"
                )

                manager.sendMessageSuspending(message = message)

                wireMessage.remoteData?.let { remoteData ->
                    val asset = manager.downloadAssetSuspending(remoteData)
                    val fileName = wireMessage.name ?: "unknown-${UUID.randomUUID()}"
                    val outputDir = File("build/downloaded_assets").apply { mkdirs() }
                    val outputFile = File(outputDir, fileName)
                    outputFile.writeBytes(asset.value)
                    logger.info("Downloaded asset with size: ${asset.value.size} bytes, saved to: " +
                            "${outputFile.absolutePath}")

                    // Helper function to check if file is an image
                    fun isImageFile(fileName: String): Boolean {
                        val imageExtensions =
                            listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "tiff", "svg")
                        val extension = fileName.substringAfterLast('.', "").lowercase()
                        return extension in imageExtensions
                    }
                    // Helper function to check if file is an audio file
                    fun isAudioFile(fileName: String): Boolean {
                        val audioExtensions = listOf("mp3", "wav", "m4a", "ogg", "flac", "mp4", "aac")
                        val extension = fileName.substringAfterLast('.', "").lowercase()
                        return extension in audioExtensions
                    }
                    // Use imported helpers from Helpers.kt
                    if (isAudioFile(fileName)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val whisperUrl = "http://localhost:8081/transcribe/detailed"
                                val client = OkHttpClient.Builder()
                                    .connectTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                                    .writeTimeout(5, java.util.concurrent.TimeUnit.MINUTES)
                                    .readTimeout(10, java.util.concurrent.TimeUnit.MINUTES)
                                    .build()
                                val requestBody = MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart(
                                        "file",
                                        fileName,
                                        outputFile.asRequestBody("audio/wav".toMediaTypeOrNull())
                                    )
                                    .build()
                                val request = Request.Builder()
                                    .url(whisperUrl)
                                    .post(requestBody)
                                    .build()
                                logger.info("Sending audio to Whisper for transcription via OkHttp (async)...")
                                val response = client.newCall(request).execute()
                                logger.info("OkHttp request executed, response received.")
                                val responseBody = response.body?.string() ?: ""
                                logger.info("Whisper response status: ${response.code}")
                                logger.info("Whisper response body: $responseBody")
                                val transcript = if (response.code == HttpURLConnection.HTTP_OK) {
                                    removeThinkingBlock(unescapeUnicode(formatWhisperSegments(responseBody)))
                                } else {
                                    "❌ Failed to get transcript from Whisper. Status: ${response.code}"
                                }
                                val transcriptMessage = WireMessage.Text.create(
                                    conversationId = wireMessage.conversationId,
                                    text = "\uD83C\uDFB6 **${wireMessage.name}**\n📝 Audio Transcript:\n${transcript}"
                                )
                                manager.sendMessageSuspending(message = transcriptMessage)

                                // 2. If transcript is valid, send to Ollama for summary
                                if (response.code == HttpURLConnection.HTTP_OK && transcript.isNotBlank()) {
                                    val ollamaUrl = "http://localhost:11434/api/chat"
                                    val summaryPrompt = """
                                            Summarize the following conversation transcript.
                                            Provide a general idea, bullet points of what was discussed, and any decisions or conclusions made.
                                            Use markdown formatting for the summary.
                                            Make sure to include the most important points.
                                            This is the one time request, do not ask for more information.

                                            Transcript:
                                            $transcript
                                        """.trimIndent().replace("\n", "\\n")
                                    val ollamaRequestBody = """
                                            {
                                              "model": "gemma3:4b",
                                              "messages": [
                                                {
                                                  "role": "user",
                                                  "content": "$summaryPrompt"
                                                }
                                              ],
                                              "stream": false
                                            }
                                        """.trimIndent()
                                    val ollamaClient = java.net.http.HttpClient.newHttpClient()
                                    val ollamaRequest = java.net.http.HttpRequest.newBuilder()
                                        .uri(java.net.URI.create(ollamaUrl))
                                        .header("Content-Type", "application/json")
                                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(ollamaRequestBody))
                                        .timeout(java.time.Duration.ofSeconds(60))
                                        .build()
                                    logger.info("Sending transcript to Ollama for summary...")
                                    val ollamaResponse = ollamaClient.send(
                                        ollamaRequest,
                                        java.net.http.HttpResponse.BodyHandlers.ofString()
                                    )
                                    val ollamaBody = ollamaResponse.body()
                                    val cleanedOllamaBody = removeThinkingBlock(ollamaBody)
                                    logger.info("Ollama response status: ${ollamaResponse.statusCode()}")
                                    logger.info("Ollama response body: $cleanedOllamaBody")
                                    val summary = if (ollamaResponse.statusCode() == HttpURLConnection.HTTP_OK) {
                                        // Try to extract "content" field from JSON
                                        val contentRegex = """"content"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
                                        val match = contentRegex.find(cleanedOllamaBody)
                                        val rawSummary = match?.groupValues?.get(1)
                                            ?.replace("\\n", "\n")
                                            ?.replace("\\\"", "\"")
                                            ?.replace("\\\\", "\\")
                                            ?: "No summary found."
                                        unescapeUnicode(rawSummary)
                                    } else {
                                        "❌ Failed to get summary from Ollama. Status: ${ollamaResponse.statusCode()}"
                                    }
                                    // 3. Send summary to user
                                    val summaryMessage = WireMessage.Text.create(
                                        conversationId = wireMessage.conversationId,
                                        text = "\uD83C\uDFB6 ${wireMessage.name}\n📝 Conversation Summary:\n$summary"
                                    )
                                    manager.sendMessageSuspending(message = summaryMessage)
                                }
                            } catch (e: Exception) {
                                logger.error("Error during Whisper/Ollama processing", e)
                                val errorMsg = WireMessage.Text.create(
                                    conversationId = wireMessage.conversationId,
                                    text = "❌ An error occurred while transcribing or summarizing the audio."
                                )
                                manager.sendMessageSuspending(message = errorMsg)
                            }
                        }
                    }

                    if (isImageFile(fileName)) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val ollamaUrl = "http://localhost:11434/api/chat"
                                val prompt = "Describe the content of the attached image. " +
                                        "If there any text in the image, please extract it to the end of description." +
                                        "use markdown formatting for the description."
                                val base64Image =
                                    java.util.Base64.getEncoder().encodeToString(asset.value)

                                val requestBody = """
                                {
                                  "model": "qwen2.5vl:latest",
                                  "messages": [
                                    {
                                      "role": "user",
                                      "content": "$prompt",
                                      "images": ["$base64Image"]
                                    }
                                  ],
                                  "stream": false
                                }
                                """.trimIndent()

                                val client = java.net.http.HttpClient.newHttpClient()
                                val request = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(ollamaUrl))
                                    .header("Content-Type", "application/json")
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestBody))
                                    .timeout(java.time.Duration.ofSeconds(60))
                                    .build()

                                logger.info("Sending request to Ollama...")
                                val response = client.send(
                                    request,
                                    java.net.http.HttpResponse.BodyHandlers.ofString()
                                )
                                logger.debug("Ollama response status: ${response.statusCode()}")
                                logger.debug("Ollama response body: ${response.body()}")

                                val replyText = if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                                    val description = parseOllamaResponse(response.body())
                                    "🖼️ Image Description:\n$description"
                                } else {
                                    "❌ Failed to get description from Ollama. Status: ${response.statusCode()}"
                                }

                                val responseMessage = WireMessage.Text.create(
                                    conversationId = wireMessage.conversationId,
                                    text = replyText
                                )
                                manager.sendMessageSuspending(message = responseMessage)

                            } catch (e: Exception) {
                                logger.error("Error during Ollama processing", e)
                                val errorMsg = WireMessage.Text.create(
                                    conversationId = wireMessage.conversationId,
                                    text = "❌ An error occurred while processing the image."
                                )
                                manager.sendMessageSuspending(message = errorMsg)
                            }
                        }
                    }

                    if (!isImageFile(fileName) && !isAudioFile(fileName)) {
                        val nonImageMessage = WireMessage.Text.create(
                            conversationId = wireMessage.conversationId,
                            text = "Sorry, I can process only images and audio/video files. Received: $fileName"
                        )
                        manager.sendMessageSuspending(message = nonImageMessage)
                        return@let
                    }

//                    manager.uploadAndSendMessageSuspending(
//                        conversationId = wireMessage.conversationId,
//                        asset = AssetResource(outputFile.readBytes()),
//                        mimeType = wireMessage.mimeType ?: "application/octet-stream",
//                        retention = AssetRetention.VOLATILE
//                    )
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
    val selfUser = QualifiedId(
        id = UUID.fromString("ee159b66-fd70-4739-9bae-23c96a02cb09"),
        domain = "chala.wire.link"
    )
    logger.info(applicationManager.getUser(selfUser).toString())
    logger.info("Wire backend domain: ${applicationManager.getBackendConfiguration().domain}")

    // Use wireAppSdk.stop() to stop the SDK or just stop it with Ctrl+C/Cmd+C
}
