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
import com.wire.sdk.model.http.conversation.ConversationIdsResponse
import com.wire.sdk.model.http.conversation.ConversationListPaginationConfig
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.slf4j.LoggerFactory

internal class ConversationsApiClient(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private companion object {
        const val CONVERSATION_LIST_IDS_PAGING_SIZE = 100
    }

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

    suspend fun updateConversationMemberRole(
        conversationId: QualifiedId,
        userId: QualifiedId,
        updateConversationMemberRoleRequest: UpdateConversationMemberRoleRequest
    ) {
        val conversationPath = "conversations/${conversationId.domain}/${conversationId.id}"
        val memberPath = "members/${userId.domain}/${userId.id}"

        httpClient.put("/$API_VERSION/$conversationPath/$memberPath") {
            setBody(updateConversationMemberRoleRequest)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun getConversationIds(): List<QualifiedId> {
        val conversationIds: MutableList<QualifiedId> = mutableListOf()

        var pagingConfig = ConversationListPaginationConfig(
            pagingState = null,
            size = CONVERSATION_LIST_IDS_PAGING_SIZE
        )

        var hasMorePages: Boolean
        do {
            val listIdsResponse = httpClient.post("/$API_VERSION/conversations/list-ids") {
                setBody(pagingConfig)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }.body<ConversationIdsResponse>()

            hasMorePages = listIdsResponse.hasMore
            pagingConfig = pagingConfig.copy(pagingState = listIdsResponse.pagingState)
            conversationIds.addAll(listIdsResponse.qualifiedConversations)
        } while (hasMorePages)

        return conversationIds
    }
}
