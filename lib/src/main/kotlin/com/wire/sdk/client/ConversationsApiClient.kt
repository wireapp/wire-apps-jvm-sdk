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
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.conversation.ConversationIdsRequest
import com.wire.sdk.model.http.conversation.ConversationIdsResponse
import com.wire.sdk.model.http.conversation.ConversationListPaginationConfig
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.ConversationsResponse
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import com.wire.sdk.utils.Mls
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
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
        const val FETCH_CONVERSATIONS_START_INDEX = 0
        const val FETCH_CONVERSATIONS_END_INDEX = 1000
        const val FETCH_CONVERSATIONS_INCREASE_INDEX = 1000
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

    suspend fun getConversationsById(
        conversationIds: List<QualifiedId>
    ): List<ConversationResponse> {
        val conversations: MutableList<ConversationResponse> = mutableListOf()

        if (!conversationIds.isEmpty()) {
            var startIndex = FETCH_CONVERSATIONS_START_INDEX
            var endIndex = FETCH_CONVERSATIONS_END_INDEX

            do {
                if (endIndex > conversationIds.size) {
                    endIndex = conversationIds.size
                }

                val conversationIdsRequest = ConversationIdsRequest(
                    qualifiedIds = conversationIds.subList(startIndex, endIndex)
                )

                val conversationsListResponse =
                    httpClient.post("/$API_VERSION/conversations/list") {
                        setBody(conversationIdsRequest)
                        contentType(ContentType.Application.Json)
                        accept(ContentType.Application.Json)
                    }.body<ConversationsResponse>()

                conversations.addAll(conversationsListResponse.found)

                startIndex += FETCH_CONVERSATIONS_INCREASE_INDEX
                endIndex += FETCH_CONVERSATIONS_INCREASE_INDEX
            } while (endIndex < conversationIds.size + FETCH_CONVERSATIONS_INCREASE_INDEX)
        }

        return conversations
    }

    suspend fun getConversationGroupInfo(conversationId: QualifiedId): ByteArray {
        logger.info("Fetching conversation groupInfo: $conversationId")
        return httpClient.get(
            "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}" +
                "/groupinfo"
        ) {
            accept(Mls)
        }.body<ByteArray>()
    }

    suspend fun leaveConversation(
        userId: QualifiedId,
        conversationId: QualifiedId
    ) {
        logger.info(
            "App user will be removed from the conversation in the backend. " +
                "userId:{}, conversationId:{}",
            userId,
            conversationId
        )

        val path = "/$API_VERSION/conversations/${conversationId.domain}/${conversationId.id}" +
            "/members/${userId.domain}/${userId.id}"

        httpClient.delete(path)

        logger.info(
            "App user is removed from the conversation in the backend. " +
                "userId:{}, conversationId:{}",
            userId,
            conversationId
        )
    }

    suspend fun deleteConversation(
        teamId: TeamId,
        conversationId: QualifiedId
    ) {
        logger.info(
            "Conversation will be deleted in the backend. teamId:{}, conversationId:{}",
            teamId,
            conversationId
        )

        val path = "/$API_VERSION/teams/${teamId.value}/conversations/${conversationId.id}"

        httpClient.delete(path)

        logger.info(
            "Conversation is deleted in the backend. teamId:{}, conversationId:{}",
            teamId,
            conversationId
        )
    }
}
