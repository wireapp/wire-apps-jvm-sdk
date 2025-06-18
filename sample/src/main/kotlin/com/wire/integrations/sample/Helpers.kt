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

package com.wire.integrations.sample

import org.json.JSONObject

internal fun removeThinkingBlock(text: String): String {
    return text.replace(Regex("""\\u003cthink\\u003e.*?\\u003c/think\\u003e""", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL), "")
}

internal fun unescapeUnicode(input: String): String {
    return input.replace(Regex("""\\u([0-9a-fA-F]{4})""")) {
        it.groupValues[1].toInt(16).toChar().toString()
    }
}

internal fun formatWhisperSegments(responseBody: String): String {
    val json = JSONObject(responseBody)
    val segments = json.getJSONArray("segments")
    val sb = StringBuilder()
    fun formatTime(seconds: Double): String {
        val totalSeconds = seconds.toInt()
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format("%d:%02d", minutes, secs)
    }
    for (i in 0 until segments.length()) {
        val seg = segments.getJSONObject(i)
        val start = seg.getDouble("start")
        val end = seg.getDouble("end")
        val text = seg.getString("text")
        val speaker = seg.optString("speaker", "Unknown")
        sb.append("${formatTime(start)}-${formatTime(end)} | $speaker: $text\n")
    }
    return sb.toString().trim()
}

internal fun parseOllamaResponse(responseBody: String): String {
    return try {
        val contentRegex = """"content"\s*:\s*"([^"\\]*(\\.[^"\\]*)*)"""".toRegex()
        val matchResult = contentRegex.find(responseBody)
        if (matchResult != null) {
            matchResult.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
        } else {
            "No description could be extracted from the response."
        }
    } catch (e: Exception) {
        "Error parsing response from Ollama."
    }
}
