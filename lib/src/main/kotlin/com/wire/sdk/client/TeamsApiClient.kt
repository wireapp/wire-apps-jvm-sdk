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
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import org.slf4j.LoggerFactory

internal class TeamsApiClient(private val httpClient: HttpClient) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val basePath = "teams"

    suspend fun deleteConversation(
        teamId: TeamId,
        conversationId: QualifiedId
    ) {
        logger.info(
            "Conversation will be deleted in the backend. teamId:{}, conversationId:{}",
            teamId,
            conversationId
        )

        val path = "/$API_VERSION/$basePath/${teamId.value}/conversations/${conversationId.id}"

        httpClient.delete(path)

        logger.info(
            "Conversation is deleted in the backend. teamId:{}, conversationId:{}",
            teamId,
            conversationId
        )
    }
}
