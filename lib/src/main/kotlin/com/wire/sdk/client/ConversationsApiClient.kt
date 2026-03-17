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

package com.wire.sdk.client

import com.wire.sdk.client.BackendClient.Companion.API_VERSION
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.slf4j.LoggerFactory

internal class ConversationsApiClient(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun getConversation(conversationId: QualifiedId): ConversationResponse {
        logger.info("Fetching conversation: $conversationId")
        return httpClient.get(
            "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}"
        ).body<ConversationResponse>()
    }

    suspend fun createGroupConversation(
        createConversationRequest: CreateConversationRequest
    ): ConversationResponse {
        return httpClient.post("/$API_VERSION/conversations") {
            setBody(createConversationRequest)
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<ConversationResponse>()
    }
}
