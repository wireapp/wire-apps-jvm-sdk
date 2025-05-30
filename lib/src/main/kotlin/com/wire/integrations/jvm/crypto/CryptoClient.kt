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

package com.wire.integrations.jvm.crypto

import com.wire.crypto.GroupInfo
import com.wire.crypto.MLSGroupId
import com.wire.crypto.MLSKeyPackage
import com.wire.crypto.Welcome
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.http.MlsPublicKeys

internal interface CryptoClient : AutoCloseable {
    fun getAppClientId(): AppClientId

    suspend fun encryptMls(
        mlsGroupId: MLSGroupId,
        message: ByteArray
    ): ByteArray

    suspend fun decryptMls(
        mlsGroupId: MLSGroupId,
        encryptedMessage: String
    ): ByteArray?

    suspend fun mlsGetPublicKey(): MlsPublicKeys

    suspend fun mlsGenerateKeyPackages(
        packageCount: UInt = DEFAULT_KEYPACKAGE_COUNT
    ): List<MLSKeyPackage>

    /**
     * Create a request to join an MLS conversation.
     */
    suspend fun joinMlsConversationRequest(groupInfo: GroupInfo): MLSGroupId

    /**
     * Create an MLS conversation, adding the client as the first member.
     */
    suspend fun createConversation(groupId: MLSGroupId)

    /**
     * Alternative way to add a member to an MLS conversation.
     * Instead of creating a join request accepted by the new client,
     * this method directly adds a member to a conversation.
     */
    suspend fun addMemberToMlsConversation(
        mlsGroupId: MLSGroupId,
        keyPackages: List<MLSKeyPackage>
    )

    /**
     * Process an MLS welcome message, adding this client to a conversation, and return the groupId.
     */
    suspend fun processWelcomeMessage(welcome: Welcome): MLSGroupId

    suspend fun hasTooFewKeyPackageCount(): Boolean

    suspend fun conversationExists(mlsGroupId: MLSGroupId): Boolean

    suspend fun conversationEpoch(mlsGroupId: MLSGroupId): ULong

    companion object {
        const val DEFAULT_KEYPACKAGE_COUNT = 100u
    }
}
