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
import com.wire.crypto.MlsException
import com.wire.crypto.toGroupId
import com.wire.crypto.toMLSKeyPackage
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CoreCryptoClient
import com.wire.integrations.jvm.crypto.CoreCryptoClient.Companion.toHexString
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.http.conversation.CreateConversationRequest
import com.wire.integrations.jvm.model.http.conversation.KeyPackage
import com.wire.integrations.jvm.model.http.conversation.MlsPublicKeysResponse
import com.wire.integrations.jvm.model.http.conversation.getRemovalKey
import io.ktor.util.decodeBase64Bytes
import java.util.UUID
import kotlin.collections.plus
import org.slf4j.LoggerFactory

internal class ConversationService internal constructor(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Creates a Group Conversation where currently the only admin is the App
     *
     * @param name Name of the created conversation
     * @param userIds List of QualifiedId of all the users to be added to the conversation
     * (excluding the App user)
     *
     * @return QualifiedId The Id of the created conversation
     */
    suspend fun createGroup(
        name: String,
        userIds: List<QualifiedId>
    ): QualifiedId {
        val conversationResponse = backendClient.createGroupConversation(
            createConversationRequest = CreateConversationRequest.create(
                name = name
            )
        )

        val mlsGroupId = conversationResponse.groupId.decodeBase64Bytes().toGroupId()
        val publicKeysResponse = conversationResponse.publicKeys ?: backendClient.getPublicKeys()

        createConversation(
            userIds = userIds,
            mlsGroupId = mlsGroupId,
            publicKeysResponse = publicKeysResponse,
            type = ConversationType.GROUP
        )

        val conversationId = conversationResponse.id
        logger.info("Group Conversation created with ID: $conversationId")
        return conversationId
    }

    /**
     * Creates a One To One Conversation with a user starting from the App
     *
     * @param userId QualifiedId of the user the App will create the conversation with
     *
     * @return QualifiedId The Id of the created conversation
     */
    suspend fun createOneToOne(userId: QualifiedId): QualifiedId {
        val oneToOneConversationResponse = backendClient.getOneToOneConversation(userId = userId)
        val conversation = oneToOneConversationResponse.conversation
        val mlsGroupId = conversation.groupId.decodeBase64Bytes().toGroupId()

        createConversation(
            userIds = listOf(userId),
            mlsGroupId = mlsGroupId,
            publicKeysResponse = oneToOneConversationResponse.publicKeys,
            type = ConversationType.ONE_TO_ONE
        )

        val conversationId = conversation.id
        logger.info("OneToOne Conversation created with ID: $conversationId")
        return conversationId
    }

    private suspend fun createConversation(
        userIds: List<QualifiedId>,
        mlsGroupId: MLSGroupId,
        publicKeysResponse: MlsPublicKeysResponse?,
        type: ConversationType
    ) {
        val cipherSuiteCode = getCipherSuiteCode()
        val cipherSuite = CoreCryptoClient.getMlsCipherSuiteName(code = cipherSuiteCode)

        val publicKeys = publicKeysResponse?.getRemovalKey(cipherSuite = cipherSuite)

        publicKeys?.let { externalSenders ->
            try {
                cryptoClient.createConversation(
                    groupId = mlsGroupId,
                    externalSenders = externalSenders
                )
            } catch (exception: MlsException.ConversationAlreadyExists) {
                throw WireException.CryptographicSystemError(
                    "Conversation already exists.",
                    throwable = exception
                )
            }

            val users = userIds + listOf(
                QualifiedId(
                    id = UUID.fromString(System.getenv("WIRE_SDK_USER_ID")),
                    domain = System.getenv("WIRE_SDK_ENVIRONMENT")
                )
            )

            val claimedKeyPackages: List<ByteArray> = claimKeyPackages(
                userIds = users,
                cipherSuiteCode = cipherSuiteCode
            )

            if (type == ConversationType.GROUP && claimedKeyPackages.isEmpty()) {
                cryptoClient.updateKeyingMaterial(mlsGroupId)
            } else {
                cryptoClient.addMemberToMlsConversation(
                    mlsGroupId = mlsGroupId,
                    keyPackages = claimedKeyPackages.map { keyPackage ->
                        keyPackage.toMLSKeyPackage()
                    }
                )
            }
        } ?: throw WireException.MissingParameter(
            message = "No Public Keys found, skipping creating a conversation."
        )
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
                    user = user,
                    cipherSuite = cipherSuiteCode.toHexString()
                )

                if (result.keyPackages.isNotEmpty()) {
                    claimedKeyPackages.addAll(result.keyPackages)
                }
            } catch (exception: Exception) {
                // Ignoring when claiming key packages fails for a user
                // as for now there is no retry
                logger.error("Error when claiming key packages: $exception")
            }
        }

        return claimedKeyPackages.map { it.keyPackage.decodeBase64Bytes() }
    }

    private suspend fun getCipherSuiteCode(): Int =
        backendClient
            .getApplicationFeatures()
            .mlsFeatureResponse
            .mlsFeatureConfigResponse
            .defaultCipherSuite

    private companion object {
        enum class ConversationType {
            /**
             * Conversation is between two Users
             */
            ONE_TO_ONE,

            /**
             * Conversation is between two or more Users
             * - Up to 500 Users
             */
            GROUP,

            /**
             * Almost the same as a Group Conversation but with extra perks:
             * - Public or Private (If public, can be found by other users outside the channel)
             * - History
             * - Currently up to 2000 Users
             */
            CHANNEL
        }
    }
}
