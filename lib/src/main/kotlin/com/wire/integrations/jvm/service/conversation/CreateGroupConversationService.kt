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

package com.wire.integrations.jvm.service.conversation

import com.wire.crypto.MLSGroupId
import com.wire.crypto.toGroupId
import com.wire.crypto.toMLSKeyPackage
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CoreCryptoClient
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.conversation.ConversationTeamInfo
import com.wire.integrations.jvm.model.http.conversation.CreateConversationRequest
import com.wire.integrations.jvm.model.http.conversation.KeyPackage
import com.wire.integrations.jvm.model.http.conversation.getRemovalKey
import com.wire.integrations.jvm.utils.toHexString
import io.ktor.util.decodeBase64Bytes
import java.util.Base64
import org.slf4j.LoggerFactory

internal class CreateGroupConversationService internal constructor(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun create(
        name: String,
        teamId: TeamId,
        userIds: List<QualifiedId>
    ): QualifiedId {
        val conversationRequest = CreateConversationRequest(
            name = name,
            conversationTeamInfo = ConversationTeamInfo(
                managed = false,
                teamId = teamId.value.toString()
            )
        )

        val conversationResponse = backendClient.createGroupConversation(
            createConversationRequest = conversationRequest
        )

        val mlsGroupId = Base64
            .getDecoder()
            .decode(conversationResponse.groupId)
            .toGroupId()

        val cipherSuiteCode = backendClient
            .getApplicationFeatures()
            .mlsFeatureResponse
            .mlsFeatureConfigResponse
            .defaultCipherSuite

        val cipherSuite = CoreCryptoClient.getMlsCipherSuiteName(code = cipherSuiteCode)

        val publicKeys = (conversationResponse.publicKeys ?: backendClient.getPublicKeys())
            .getRemovalKey(cipherSuite = cipherSuite)

        createMlsGroupConversation(
            publicKeys = publicKeys,
            cipherSuiteCode = cipherSuiteCode,
            mlsGroupId = mlsGroupId,
            userIds = userIds
        )

        val conversationId = conversationResponse.id
        logger.info("Conversation created with ID: $conversationId")
        return conversationId
    }

    private suspend fun createMlsGroupConversation(
        publicKeys: ByteArray?,
        cipherSuiteCode: Int,
        mlsGroupId: MLSGroupId,
        userIds: List<QualifiedId>
    ) {
        publicKeys?.let { externalSenders ->
            cryptoClient.createConversation(
                groupId = mlsGroupId,
                externalSenders = externalSenders
            )

            cryptoClient.commitPendingProposals(mlsGroupId)

            val claimedKeyPackages: List<ByteArray> = claimKeyPackages(
                userIds = userIds,
                cipherSuiteCode = cipherSuiteCode
            )

            if (claimedKeyPackages.isEmpty()) {
                cryptoClient.updateKeyingMaterial(mlsGroupId)
            } else {
                cryptoClient.addMemberToMlsConversation(
                    mlsGroupId = mlsGroupId,
                    keyPackages = claimedKeyPackages.map { keyPackage ->
                        keyPackage.toMLSKeyPackage()
                    }
                )
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun claimKeyPackages(
        userIds: List<QualifiedId>,
        cipherSuiteCode: Int
    ): List<ByteArray> {
        val claimedKeyPackages = mutableListOf<KeyPackage>()
        userIds.forEach { user ->
            try {
                val result = backendClient.claimKeyPackages(
                    userDomain = user.domain,
                    userId = user.id,
                    cipherSuite = cipherSuiteCode.toHexString()
                )

                if (result.keyPackages.isNotEmpty()) {
                    claimedKeyPackages.addAll(result.keyPackages)
                }
            } catch (exception: Exception) {
                // Ignoring when claiming key packages fails for a user
                // as for now there is no retry
                logger.info("Error when claiming key packages: $exception")
            }
        }

        return claimedKeyPackages.map { it.keyPackage.decodeBase64Bytes() }
    }
}
