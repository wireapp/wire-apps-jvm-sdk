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
import com.wire.sdk.model.http.conversation.OneToOneConversationResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal class OneToOneConversationsApiClient(private val httpClient: HttpClient) {
    private val basePath = "one2one-conversations"

    suspend fun getByUserId(userId: QualifiedId): OneToOneConversationResponse {
        return httpClient.get("/$basePath/${userId.domain}/${userId.id}") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body<OneToOneConversationResponse>()
    }
}
