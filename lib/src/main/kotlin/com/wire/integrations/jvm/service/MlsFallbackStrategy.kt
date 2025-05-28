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

package com.wire.integrations.jvm.service

import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId

class MlsFallbackStrategy internal constructor(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient
) {
    /**
     * Verifies if a conversation is out of sync and re-syncs (re-joining or updating the epoch)
     *
     * If current CoreCrypto client is not a member of the given conversation or
     * current epoch value is different from remote epoch value, then a join by External Commit
     * is sent to CoreCrypto.
     *
     * @param mlsGroupId [MLSGroupId] Conversation GroupId
     * @param conversationId [QualifiedId] Conversation QualifiedId
     */
    suspend fun verifyConversationOutOfSync(
        mlsGroupId: MLSGroupId,
        conversationId: QualifiedId
    ) {
        val conversationExists = cryptoClient.conversationExists(mlsGroupId = mlsGroupId)
        val fetchedConversation = backendClient.getConversation(conversationId = conversationId)
        val currentEpoch = cryptoClient.conversationEpoch(mlsGroupId = mlsGroupId)

        if (!conversationExists || currentEpoch.toLong() < fetchedConversation.epoch) {
            val groupInfo = backendClient.getConversationGroupInfo(
                conversationId = conversationId
            )
            cryptoClient.joinMlsConversationRequest(
                groupInfo = GroupInfo(value = groupInfo)
            )
        }
    }
}
