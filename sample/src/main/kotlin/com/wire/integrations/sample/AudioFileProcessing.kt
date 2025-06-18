package com.wire.integrations.sample

import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.service.WireApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.slf4j.Logger
import java.io.File
import java.net.HttpURLConnection

object AudioFileProcessing {
    suspend fun process(
        fileName: String,
        outputFile: File,
        wireMessage: WireMessage.Asset,
        manager: WireApplicationManager,
        logger: Logger
    ) = withContext(Dispatchers.IO) {
        try {
            val whisperUrl = "http://localhost:8082/transcribe/diarized"
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
                          "model": "llama3.2",
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
                    try {
                        val json = org.json.JSONObject(cleanedOllamaBody)
                        val content = json.getJSONObject("message").getString("content")
                        unescapeUnicode(content)
                    } catch (e: Exception) {
                        "No summary found."
                    }
                } else {
                    "❌ Failed to get summary from Ollama. Status: ${ollamaResponse.statusCode()}"
                }
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
