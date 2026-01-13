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

import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import com.wire.crypto.toGroupInfo
import com.wire.crypto.toMLSKeyPackage
import com.wire.sdk.client.BackendClient
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.MlsCryptoClient
import com.wire.sdk.crypto.MlsCryptoClient.Companion.toHexString
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.CryptoProtocol
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory
import java.util.UUID

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
        mlsGroupId: ConversationId,
        publicKeysResponse: MlsPublicKeysResponse?
    ) {
        val cipherSuiteCode = getCipherSuiteCode()
        val cipherSuite = MlsCryptoClient.getMlsCipherSuiteName(code = cipherSuiteCode)

        val publicKeys = (publicKeysResponse ?: backendClient.getPublicKeys()).run {
            getRemovalKey(cipherSuite = cipherSuite)
        }

        publicKeys?.let { externalSenders ->
            try {
                cryptoClient.createConversation(
                    mlsGroupId = mlsGroupId,
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
        mlsGroupId: ConversationId,
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
        val appUserID: UUID = IsolatedKoinContext.getApplicationId()

        requireConversationIsGroupOrChannel(
            conversationId = conversationId,
            conversationType = conversation.type
        )

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
        mlsGroupId: ConversationId
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

    @Suppress("LongMethod", "ThrowsCount")
    suspend fun addMembersToConversation(
        conversationId: QualifiedId,
        members: List<QualifiedId>
    ): AddMembersToConversationResult {
        if (members.isEmpty()) {
            throw WireException.InvalidParameter(
                "List of members cannot be empty."
            )
        }

        val conversation = getConversationById(conversationId = conversationId)

        requireConversationIsGroupOrChannel(
            conversationId = conversationId,
            conversationType = conversation.type
        )
        requireAppIsAdminInConversation(conversationId = conversationId)

        logger.info(
            "Attempting to claim key-packages for {} member(s). conversationId: {}",
            members.size,
            conversationId
        )

        val cipherSuiteCode = getCipherSuiteCode()
        val claimedKeyPackagesResult = claimKeyPackages(
            userIds = members,
            cipherSuiteCode = cipherSuiteCode
        )

        logger.info(
            "Attempting to add {} member(s) to the conversation and ignoring {} member(s). " +
                "conversationId: {}",
            claimedKeyPackagesResult.successUsers.size,
            claimedKeyPackagesResult.failedUsers.size,
            conversationId
        )

        try {
            cryptoClient.addMemberToMlsConversation(
                mlsGroupId = conversation.mlsGroupId,
                keyPackages = claimedKeyPackagesResult.keyPackages.map { keyPackage ->
                    keyPackage.toMLSKeyPackage()
                }
            )
        } catch (exception: MlsException) {
            throw WireException.InvalidParameter(
                message = "Unable to claim Key Packages for list of members",
                throwable = exception
            )
        } catch (exception: CoreCryptoException.Mls) {
            val message = if (exception.mlsError.message.equals(DUPLICATE_GROUP_SIGNATURE)) {
                "Member is already in the conversation."
            } else {
                exception.message
            }

            throw WireException.InvalidParameter(
                message = message,
                throwable = exception
            )
        }

        conversationStorage.saveMembers(
            conversationId = conversationId,
            members = claimedKeyPackagesResult.successUsers.map { member ->
                ConversationMember(
                    userId = member,
                    role = ConversationRole.MEMBER
                )
            }
        )

        logger.info(
            "{} member(s) successfully added to the conversation. conversationId: {}",
            claimedKeyPackagesResult.successUsers.size,
            conversationId
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
                "List of members cannot be empty."
            )
        }

        val conversation = getConversationById(conversationId = conversationId)

        requireConversationIsGroupOrChannel(
            conversationId = conversationId,
            conversationType = conversation.type
        )
        requireAppIsAdminInConversation(conversationId = conversationId)

        logger.info(
            "Attempting to remove {} member(s) from the conversation. conversationId: {}",
            members.size,
            conversationId
        )

        val clients: List<CryptoClientId> = getClientsByUserIds(userIds = members)

        cryptoClient.removeMembersFromConversation(
            mlsGroupId = conversation.mlsGroupId,
            clientIds = clients
        )

        logger.info(
            "Removing {} member(s) from the database. conversationId: {}",
            members.size,
            conversationId
        )

        conversationStorage.deleteMembers(
            conversationId = conversationId,
            users = members
        )

        logger.info(
            "{} member(s) successfully removed from the conversation. conversationId: {}",
            members.size,
            conversationId
        )
    }

    private suspend fun getClientsByUserIds(userIds: List<QualifiedId>): List<CryptoClientId> {
        val usersClients = if (userIds.size == 1) {
            val user = userIds.first()
            logger.info("Retrieving clients for User: {}", user)

            val clients = backendClient.getClientsByUserId(userId = user)

            mapOf(user to clients)
        } else {
            logger.info("Retrieving clients for {} users.", userIds.size)

            backendClient.getClientsByUserIds(userIds = userIds)
        }

        return usersClients.flatMap { (user, clients) ->
            logger.debug("Mapping {} clients for User: {}", clients.size, user.id)
            clients.map { client ->
                CryptoClientId.create(
                    userId = user.id.toString(),
                    deviceId = client.id,
                    userDomain = user.domain
                )
            }
        }.also {
            logger.info("Returning {} clients for {} user(s).", it.size, userIds.size)
        }
    }

    private suspend fun getCipherSuiteCode(): Int =
        backendClient
            .getApplicationFeatures()
            .mlsFeatureResponse
            .mlsFeatureConfigResponse
            .defaultCipherSuite

    private companion object {
        const val DUPLICATE_GROUP_SIGNATURE = "Duplicate signature key in proposals and group."
    }
}
