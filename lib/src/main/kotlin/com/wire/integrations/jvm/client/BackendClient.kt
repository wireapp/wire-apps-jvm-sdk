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

package com.wire.integrations.jvm.client

import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.http.FeaturesResponse
import com.wire.integrations.jvm.model.http.MlsPublicKeys
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import io.ktor.websocket.Frame
import kotlinx.coroutines.channels.ReceiveChannel

interface BackendClient {
    suspend fun connectWebSocket(handleFrames: suspend (ReceiveChannel<Frame>) -> Unit)

    fun getBackendVersion(): ApiVersionResponse

    fun getApplicationData(): AppDataResponse

    fun getApplicationFeatures(): FeaturesResponse

    fun confirmTeam(teamId: TeamId)

    fun updateClientWithMlsPublicKey(
        appClientId: AppClientId,
        mlsPublicKeys: MlsPublicKeys
    )

    fun uploadMlsKeyPackages(
        appClientId: AppClientId,
        mlsKeyPackages: List<ByteArray>
    )

    fun uploadCommitBundle(commitBundle: ByteArray)

    fun sendMessage(mlsMessage: ByteArray)

    fun getConversation(conversationId: QualifiedId): ConversationResponse
}
