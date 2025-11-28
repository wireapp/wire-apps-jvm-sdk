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

package com.wire.sdk.service.conversation

import com.wire.crypto.MLSGroupId
import com.wire.crypto.MlsException
import com.wire.crypto.toGroupInfo
import com.wire.crypto.toMLSKeyPackage
import com.wire.sdk.client.BackendClient
import com.wire.sdk.crypto.CoreCryptoClient
import com.wire.sdk.crypto.CoreCryptoClient.Companion.toHexString
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.KeyPackage
import com.wire.sdk.model.http.conversation.MlsPublicKeysResponse
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import com.wire.sdk.model.http.conversation.getDecodedMlsGroupId
import com.wire.sdk.model.http.conversation.getRemovalKey
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.persistence.ConversationStorage
import io.ktor.util.decodeBase64Bytes
import java.util.UUID
import kotlin.collections.plus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
internal class ConversationService internal constructor(
    private val backendClient: BackendClient,
    private val conversationStorage: ConversationStorage,
    private val appStorage: AppStorage,
    private val cryptoClient: CryptoClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val selfTeamId: Deferred<UUID?> by lazy {
        CoroutineScope(Dispatchers.IO).async {
            backendClient.getSelfUser().teamId
        }
    }

    private suspend fun getSelfTeamId(): TeamId =
        selfTeamId.await()
            ?.let(::TeamId)
            ?: throw WireException.MissingParameter("TeamId should not be empty or null.")

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
        val teamId = getSelfTeamId()
        val conversationCreatedResponse = backendClient.createGroupConversation(
            createConversationRequest = CreateConversationRequest.Companion.createGroup(
                name = name,
                teamId = teamId
            )
        )

        val mlsGroupId = conversationCreatedResponse.getDecodedMlsGroupId()

        establishMlsConversation(
            userIds = userIds,
            mlsGroupId = mlsGroupId,
            publicKeysResponse = conversationCreatedResponse.publicKeys
        )

        val conversationId = conversationCreatedResponse.id
        logger.info("Group Conversation created with ID: $conversationId")
        return conversationId
    }

    /**
     * Creates a Channel Conversation where currently the only admin is the App
     *
     * @param name Name of the created conversation
     * @param userIds List of QualifiedId of all the users to be added to the conversation
     * (excluding the App user)
     *
     * @return QualifiedId The Id of the created conversation
     */
    suspend fun createChannel(
        name: String,
        userIds: List<QualifiedId>
    ): QualifiedId {
        try {
            val teamId = getSelfTeamId()
            val conversationCreatedResponse = backendClient.createGroupConversation(
                createConversationRequest = CreateConversationRequest.Companion.createChannel(
                    name = name,
                    teamId = teamId
                )
            )

            val mlsGroupId = conversationCreatedResponse.getDecodedMlsGroupId()

            establishMlsConversation(
                userIds = userIds,
                mlsGroupId = mlsGroupId,
                publicKeysResponse = conversationCreatedResponse.publicKeys
            )

            val conversationId = conversationCreatedResponse.id
            logger.info("Channel Conversation created with ID: $conversationId")
            return conversationId
        } catch (exception: WireException.ClientError) {
            if (exception.response.isOperationDenied()) {
                throw WireException.Forbidden(
                    message = "Insufficient Permissions to create Channel.",
                    throwable = exception
                )
            }
            throw exception
        }
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
        val mlsGroupId = conversation.getDecodedMlsGroupId()

        establishMlsConversation(
            userIds = listOf(userId),
            mlsGroupId = mlsGroupId,
            publicKeysResponse = oneToOneConversationResponse.publicKeys
        )

        val conversationId = conversation.id
        logger.info("OneToOne Conversation created with ID: $conversationId")
        return conversationId
    }

    private suspend fun establishMlsConversation(
        userIds: List<QualifiedId>,
        mlsGroupId: MLSGroupId,
        publicKeysResponse: MlsPublicKeysResponse?
    ) {
        val cipherSuiteCode = getCipherSuiteCode()
        val cipherSuite = CoreCryptoClient.Companion.getMlsCipherSuiteName(code = cipherSuiteCode)

        val publicKeys = (publicKeysResponse ?: backendClient.getPublicKeys()).run {
            getRemovalKey(cipherSuite = cipherSuite)
        }

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
        } ?: throw WireException.MissingParameter(
            message = "No Public Keys found, skipping creating a conversation."
        )
    }

    fun getStoredConversationMembers(conversationId: QualifiedId): List<ConversationMember> =
        conversationStorage.getMembersByConversationId(conversationId)

    suspend fun establishOrRejoinConversations() {
        val shouldRejoinConversations = appStorage.getShouldRejoinConversations()
        if (shouldRejoinConversations != null && !shouldRejoinConversations) {
            logger.info("Skipping re-joining conversations as its not needed.")
            return
        }

        val conversations = fetchConversationsToRejoin()

        conversations
            .filter { conversation -> conversation.protocol == CryptoProtocol.MLS }
            .forEach { conversation ->
                establishOrJoinMlsConversation(
                    conversationId = conversation.id,
                    mlsGroupId = conversation.getDecodedMlsGroupId(),
                    conversation = conversation,
                    backendClient = backendClient,
                    cryptoClient = cryptoClient
                )
            }

        appStorage.setShouldRejoinConversations(should = false)
    }

    suspend fun updateConversationMemberRole(
        conversationId: QualifiedId,
        conversationMember: ConversationMember
    ) {
        val updateConversationMemberRoleRequest = UpdateConversationMemberRoleRequest(
            conversationRole = conversationMember.role
        )
        backendClient.updateConversationMemberRole(
            conversationId = conversationId,
            userId = conversationMember.userId,
            updateConversationMemberRoleRequest = updateConversationMemberRoleRequest
        )
        conversationStorage.updateMember(
            conversationId = conversationId,
            conversationMember = conversationMember
        )
    }

    private suspend fun fetchConversationsToRejoin(): List<ConversationResponse> {
        val conversationIdsToRejoin = backendClient.getConversationIds()

        val conversations: List<ConversationResponse> =
            backendClient.getConversationsById(conversationIds = conversationIdsToRejoin)

        return conversations
    }

    private suspend fun establishOrJoinMlsConversation(
        conversationId: QualifiedId,
        mlsGroupId: MLSGroupId,
        conversation: ConversationResponse,
        backendClient: BackendClient,
        cryptoClient: CryptoClient
    ) {
        if (cryptoClient.conversationExists(mlsGroupId)) {
            logger.info("Conversation {} already exists, skipping it", conversationId)
            return
        }

        when {
            conversation.epoch != null && conversation.epoch != 0L -> {
                val conversationGroupInfo: ByteArray =
                    backendClient.getConversationGroupInfo(conversationId = conversationId)

                cryptoClient.joinMlsConversationRequest(
                    groupInfo = conversationGroupInfo.toGroupInfo()
                )
            }

            conversation.type == ConversationResponse.Type.SELF -> {
                establishMlsConversation(
                    userIds = emptyList(),
                    mlsGroupId = mlsGroupId,
                    publicKeysResponse = null
                )
            }

            conversation.type == ConversationResponse.Type.ONE_TO_ONE -> {
                val users = conversationStorage
                    .getMembersByConversationId(conversationId = conversationId)
                    .map { members -> members.userId }

                establishMlsConversation(
                    userIds = users,
                    mlsGroupId = mlsGroupId,
                    publicKeysResponse = null
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
}
