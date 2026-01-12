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
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.CoreCryptoClient
import com.wire.sdk.crypto.CoreCryptoClient.Companion.toHexString
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.CryptoQualifiedId
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.conversation.AddMembersToConversationResult
import com.wire.sdk.model.conversation.ClaimedKeyPackagesResult
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.model.http.conversation.CreateConversationRequest
import com.wire.sdk.model.http.conversation.KeyPackage
import com.wire.sdk.model.http.conversation.MlsPublicKeysResponse
import com.wire.sdk.model.http.conversation.UpdateConversationMemberRoleRequest
import com.wire.sdk.model.http.conversation.getDecodedMlsGroupId
import com.wire.sdk.model.http.conversation.getRemovalKey
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.persistence.ConversationStorage
import com.wire.sdk.utils.obfuscateId
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
            createConversationRequest = CreateConversationRequest.createGroup(
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

        saveConversationWithMembers(
            qualifiedConversation = conversationId,
            conversationResponse = conversationCreatedResponse
        )

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
                createConversationRequest = CreateConversationRequest.createChannel(
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

            saveConversationWithMembers(
                qualifiedConversation = conversationId,
                conversationResponse = conversationCreatedResponse
            )

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
        val cipherSuite = CoreCryptoClient.getMlsCipherSuiteName(code = cipherSuiteCode)

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
                    id = IsolatedKoinContext.getApplicationId(),
                    domain = IsolatedKoinContext.getBackendDomain()
                )
            )

            val claimedKeyPackagesResult = claimKeyPackages(
                userIds = users,
                cipherSuiteCode = cipherSuiteCode
            )

            if (claimedKeyPackagesResult.keyPackages.isEmpty()) {
                cryptoClient.updateKeyingMaterial(mlsGroupId)
            } else {
                cryptoClient.addMemberToMlsConversation(
                    mlsGroupId = mlsGroupId,
                    keyPackages = claimedKeyPackagesResult.keyPackages.map { keyPackage ->
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
        userId: QualifiedId,
        newRole: ConversationRole
    ) {
        val updateConversationMemberRoleRequest = UpdateConversationMemberRoleRequest(
            conversationRole = newRole
        )
        backendClient.updateConversationMemberRole(
            conversationId = conversationId,
            userId = userId,
            updateConversationMemberRoleRequest = updateConversationMemberRoleRequest
        )
        conversationStorage.saveMembers(
            conversationId = conversationId,
            members = listOf(
                ConversationMember(
                    userId = userId,
                    role = newRole
                )
            )
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
    ): ClaimedKeyPackagesResult {
        val claimedKeyPackages = mutableListOf<KeyPackage>()
        val successUsers = mutableListOf<QualifiedId>()
        val failedUsers = mutableListOf<QualifiedId>()

        userIds.forEach { user ->
            try {
                val result = backendClient.claimKeyPackages(
                    user = user,
                    cipherSuite = cipherSuiteCode.toHexString()
                )

                if (result.keyPackages.isNotEmpty()) {
                    successUsers.add(user)
                    claimedKeyPackages.addAll(result.keyPackages)
                }
            } catch (exception: Exception) {
                // Ignoring when claiming key packages fails for a user
                // as for now there is no retry
                failedUsers.add(user)
                logger.error(
                    "Error when claiming key packages for userId: $user: $exception"
                )
            }
        }

        return ClaimedKeyPackagesResult(
            keyPackages = claimedKeyPackages.map { it.keyPackage.decodeBase64Bytes() },
            successUsers = successUsers,
            failedUsers = failedUsers
        )
    }

    suspend fun saveConversationWithMembers(
        qualifiedConversation: QualifiedId,
        conversationResponse: ConversationResponse
    ): Pair<ConversationEntity, List<ConversationMember>> {
        val conversationName =
            if (conversationResponse.type == ConversationResponse.Type.ONE_TO_ONE) {
                backendClient
                    .getUserData(
                        userId = conversationResponse.members.others.first().id
                    ).name
            } else {
                conversationResponse.name
            }

        val conversationEntity =
            ConversationEntity(
                id = qualifiedConversation,
                name = conversationName,
                mlsGroupId = conversationResponse.getDecodedMlsGroupId(),
                teamId = conversationResponse.teamId?.let { TeamId(it) },
                type = ConversationEntity.Type.fromApi(value = conversationResponse.type)
            )

        val members = (
            conversationResponse.members.others + listOf(conversationResponse.members.self)
        ).map {
            ConversationMember(
                userId = it.id,
                role = it.conversationRole
            )
        }

        logger.debug("Conversation data: {}", conversationEntity)
        logger.debug("Conversation members: {}", members)

        // Saves the conversation in the local database, used later to decrypt messages
        conversationStorage.save(conversationEntity)
        conversationStorage.saveMembers(qualifiedConversation, members)

        return Pair(conversationEntity, members)
    }

    fun saveMembers(
        conversationId: QualifiedId,
        members: List<ConversationMember>
    ) = conversationStorage.saveMembers(
        conversationId = conversationId,
        members = members
    )

    suspend fun processDeletedConversation(conversationId: QualifiedId) {
        conversationStorage.getById(conversationId)?.let {
            deleteAllConversationDataFromLocalStorages(conversationId, it.mlsGroupId)
        }
    }

    suspend fun leaveConversation(conversationId: QualifiedId) {
        logger.info("Attempting to leave conversation. conversationId: {}", conversationId)

        val conversation = getConversationById(conversationId)
        val appUserID: UUID? = IsolatedKoinContext.getApplicationId()
        if (appUserID == null ||
            conversation.type != ConversationEntity.Type.GROUP
        ) {
            logger.warn(
                "Skipping leaving conversation: invalid preconditions. conversationId: {}, " +
                    "conversationType:{}, appUserID: {}",
                conversationId,
                conversation.type,
                appUserID
            )
            throw WireException.InvalidParameter()
        }

        requireAppIsInConversation(conversationId)
        val appUser = QualifiedId(appUserID, IsolatedKoinContext.getBackendDomain())
        backendClient.leaveConversation(appUser, conversationId)
        deleteAllConversationDataFromLocalStorages(conversationId, conversation.mlsGroupId)

        logger.info(
            "App user left the conversation. user: {}, conversationId: {}",
            appUser,
            conversationId
        )
    }

    @Suppress("ThrowsCount")
    suspend fun deleteConversation(conversationId: QualifiedId) {
        logger.info("Attempting to delete conversation. conversationId: {}", conversationId)

        val conversation = getConversationById(conversationId = conversationId)

        requireConversationIsGroupOrChannel(
            conversationId = conversationId,
            conversationType = conversation.type
        )
        requireAppIsAdminInConversation(conversationId = conversationId)

        requireNotNull(conversation.teamId) {
            "Conversation teamId must not be null."
        }

        backendClient.deleteConversation(conversation.teamId, conversationId)
        deleteAllConversationDataFromLocalStorages(conversationId, conversation.mlsGroupId)

        logger.info(
            "Conversation is deleted. teamId: {}, conversationId: {}",
            conversation.teamId,
            conversationId
        )
    }

    private suspend fun deleteAllConversationDataFromLocalStorages(
        conversationId: QualifiedId,
        mlsGroupId: MLSGroupId
    ) {
        if (cryptoClient.conversationExists(mlsGroupId)) {
            cryptoClient.wipeConversation(mlsGroupId)
        }

        conversationStorage.deleteAllMembersInConversation(conversationId)
        conversationStorage.delete(conversationId = conversationId)
    }

    private fun requireAppIsAdminInConversation(conversationId: QualifiedId) {
        val appUserId = IsolatedKoinContext.getApplicationId()

        val isAppAdminInConversation = getStoredConversationMembers(conversationId).any {
            it.userId.id == appUserId && it.role == ConversationRole.ADMIN
        }

        if (!isAppAdminInConversation) {
            logger.warn(
                "App User is not an admin in the conversation. conversationId: {}, " +
                    "appUserID: {}",
                conversationId,
                appUserId.obfuscateId()
            )
            throw WireException.Forbidden.userIsNotAdmin()
        }
    }

    private fun requireAppIsInConversation(conversationId: QualifiedId) {
        val appUserId = IsolatedKoinContext.getApplicationId()
        val isAppInConversation = getStoredConversationMembers(conversationId).any {
            it.userId.id == appUserId
        }

        if (!isAppInConversation) {
            logger.warn(
                "App User is not in the conversation. conversationId: {}, appUserID: {}",
                conversationId,
                appUserId.obfuscateId()
            )
            throw WireException.Forbidden.userIsNotInConversation()
        }
    }

    private fun requireConversationIsGroupOrChannel(
        conversationId: QualifiedId,
        conversationType: ConversationEntity.Type
    ) {
        if (conversationType != ConversationEntity.Type.GROUP) {
            logger.warn(
                "Skipping operation, conversation is not a GROUP or CHANNEL. conversationId: {}, " +
                    "conversationType:{}",
                conversationId,
                conversationType
            )
            throw WireException.InvalidParameter()
        }
    }

    fun deleteMembers(
        conversationId: QualifiedId,
        users: List<QualifiedId>
    ) = conversationStorage.deleteMembers(
        conversationId = conversationId,
        users = users
    )

    suspend fun getConversationById(conversationId: QualifiedId): ConversationEntity =
        conversationStorage.getById(conversationId = conversationId) ?: run {
            val conversationResponse = backendClient.getConversation(conversationId)
            saveConversationWithMembers(
                qualifiedConversation = conversationId,
                conversationResponse = conversationResponse
            ).first
        }

    fun getAll() = conversationStorage.getAll()

    suspend fun addMembersToConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ): AddMembersToConversationResult {
        if (members.isEmpty()) {
            throw WireException.InvalidParameter(
                "List of members can not be empty."
            )
        }

        val conversation = getConversationById(conversationId = conversationId)

        requireConversationIsGroupOrChannel(
            conversationId = conversationId,
            conversationType = conversation.type
        )
        requireAppIsAdminInConversation(conversationId = conversationId)

        val cipherSuiteCode = getCipherSuiteCode()
        val claimedKeyPackagesResult = claimKeyPackages(
            userIds = members,
            cipherSuiteCode = cipherSuiteCode
        )

        try {
            cryptoClient.addMemberToMlsConversation(
                mlsGroupId = conversation.mlsGroupId,
                keyPackages = claimedKeyPackagesResult.keyPackages.map { keyPackage ->
                    keyPackage.toMLSKeyPackage()
                }
            )
        } catch (exception: MlsException.Other) {
            throw WireException.InvalidParameter(
                message = "Unable to claim Key Packages for list of members",
                throwable = exception
            )
        }

        conversationStorage.saveMembers(
            conversationId = conversationId,
            members = members.map { member ->
                ConversationMember(
                    userId = member,
                    role = ConversationRole.MEMBER
                )
            }
        )

        return AddMembersToConversationResult(
            successUsers = claimedKeyPackagesResult.successUsers,
            failedUsers = claimedKeyPackagesResult.failedUsers
        )
    }

    suspend fun removeMembersFromConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ) {
        if (members.isEmpty()) {
            throw WireException.InvalidParameter(
                "List of members can not be empty."
            )
        }

        val conversation = getConversationById(conversationId = conversationId)

        requireConversationIsGroupOrChannel(
            conversationId = conversationId,
            conversationType = conversation.type
        )
        requireAppIsAdminInConversation(conversationId = conversationId)

        val clients: List<CryptoQualifiedId> = if (members.size == SINGLE_MEMBER) {
            val clients = backendClient.getUserClients(userId = members.first())

            clients.map { client ->
                CryptoQualifiedId.create(
                    userId = members.first().id.toString(),
                    deviceId = client.id,
                    userDomain = members.first().domain
                )
            }
        } else {
            val usersClients = backendClient.getUsersClients(
                usersIds = members
            )

            usersClients.flatMap { (domain, users) ->
                users.flatMap { (userId, clients) ->
                    clients.map { client ->
                        CryptoQualifiedId.create(
                            userId = userId,
                            deviceId = client.id,
                            userDomain = domain
                        )
                    }
                }
            }
        }

        cryptoClient.removeMembersFromConversation(
            mlsGroupId = conversation.mlsGroupId,
            clientIds = clients
        )

        conversationStorage.deleteMembers(
            conversationId = conversationId,
            users = members
        )
    }

    private suspend fun getCipherSuiteCode(): Int =
        backendClient
            .getApplicationFeatures()
            .mlsFeatureResponse
            .mlsFeatureConfigResponse
            .defaultCipherSuite

    private companion object {
        const val SINGLE_MEMBER = 1
    }
}
