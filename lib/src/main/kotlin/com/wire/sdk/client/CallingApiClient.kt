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

import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.http.conversation.SubconversationResponse
import com.wire.sdk.utils.Mls
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get

/** Authenticated Wire backend operations for calling configuration and conference membership. */
internal class CallingApiClient(private val backendClient: HttpClient) {
    suspend fun getConference(conversationId: QualifiedId): SubconversationResponse =
        backendClient.get(conferencePath(conversationId)).body()

    suspend fun getGroupInfo(conversationId: QualifiedId): ByteArray =
        backendClient.get("${conferencePath(conversationId)}/groupinfo") {
            accept(Mls)
        }.body()

    suspend fun leaveConference(conversationId: QualifiedId) {
        backendClient.delete("${conferencePath(conversationId)}/self")
    }

    suspend fun getConfiguration(): String = backendClient.get("/calls/config/v2").body()

    private fun conferencePath(id: QualifiedId): String =
        "/conversations/${id.domain}/${id.id}/subconversations/conference"
}
