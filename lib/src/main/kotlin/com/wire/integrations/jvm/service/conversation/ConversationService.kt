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
import com.wire.integrations.jvm.crypto.CoreCryptoClient.Companion.toHexString
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.http.conversation.CreateConversationRequest
import com.wire.integrations.jvm.model.http.conversation.KeyPackage
import com.wire.integrations.jvm.model.http.conversation.getRemovalKey
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import java.util.Base64
import java.util.UUID
import kotlin.collections.plus
import org.slf4j.LoggerFactory

internal class ConversationService internal constructor(
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun createGroup(
        name: String,
        userIds: List<QualifiedId>
    ): QualifiedId {
        val conversationRequest = CreateConversationRequest.create(
            name = name
        )

        val conversationResponse = backendClient.createGroupConversation(
            createConversationRequest = conversationRequest
        )

        val mlsGroupId = Base64
            .getDecoder()
            .decode(conversationResponse.groupId)
            .toGroupId()

        val cipherSuiteCode = getCipherSuiteCode()

        val cipherSuite = CoreCryptoClient.getMlsCipherSuiteName(code = cipherSuiteCode)

        val publicKeys = (conversationResponse.publicKeys ?: backendClient.getPublicKeys())
            .getRemovalKey(cipherSuite = cipherSuite)

        // Adds self user to list of userIds
        val users = userIds + QualifiedId(
            id = UUID.fromString(System.getenv("WIRE_SDK_USER_ID")),
            domain = System.getenv("WIRE_SDK_ENVIRONMENT")
        )

        createMlsGroupConversation(
            publicKeys = publicKeys,
            cipherSuiteCode = cipherSuiteCode,
            mlsGroupId = mlsGroupId,
            userIds = users
        )

        val conversationId = conversationResponse.id
        logger.info("Group Conversation created with ID: $conversationId")
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
        } ?: logger.error("No Public Keys found, skipping creating a conversation.")
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

    suspend fun createOneToOne(
        userId: QualifiedId
    ): QualifiedId {
        logger.info("fetching One2One conversation")
        val oneToOneConversationResponse = backendClient.getOneToOneConversation(userId = userId)
        val conversation = oneToOneConversationResponse.conversation
        logger.info("fetched One2One conversation id : ${conversation.id}")

        val cipherSuiteCode = getCipherSuiteCode()
        logger.info("ciphersuitecode = $cipherSuiteCode")

        val cipherSuite = CoreCryptoClient.getMlsCipherSuiteName(code = cipherSuiteCode)

//        val publicKeys = (oneToOneConversationResponse.publicKeys ?: backendClient.getPublicKeys())
//            .getRemovalKey(cipherSuite = cipherSuite)
        val publicKeys = oneToOneConversationResponse.publicKeys?.getRemovalKey(cipherSuite = cipherSuite)
        logger.info("got removal keys : ${publicKeys?.encodeBase64()}")

        val mlsGroupId = conversation.groupId.toGroupId()
        logger.info("got mlsGroupId: ${mlsGroupId.copyBytes().encodeBase64()}")

        publicKeys?.let { externalSenders ->
            logger.info("creating CC conversation")
            // verify if conversation exists?
            cryptoClient.createConversation(
                groupId = mlsGroupId,
                externalSenders = externalSenders
            )

            // logger.info("commiting pending proposals")
            // cryptoClient.commitPendingProposals(mlsGroupId)

            // Adds self user to list of userIds
            val users = listOf(
                userId,
                QualifiedId(
                    id = UUID.fromString(System.getenv("WIRE_SDK_USER_ID")),
                    domain = System.getenv("WIRE_SDK_ENVIRONMENT")
                )
            )
            logger.info("users: ${users.size}")

            val claimedKeyPackages: List<ByteArray> = claimKeyPackages(
                userIds = users,
                cipherSuiteCode = cipherSuiteCode
            )
            logger.info("claimed key packages -> $claimedKeyPackages")
            claimedKeyPackages.forEach {
                logger.info("keyPackage : " + it.encodeBase64())
            }
            logger.info("keyPackage : end")

            cryptoClient.addMemberToMlsConversation(
                mlsGroupId = mlsGroupId,
                keyPackages = claimedKeyPackages.map { keyPackage ->
                    keyPackage.toMLSKeyPackage()
                }
            )
            logger.info("added members to conversation")
        }

        val conversationId = conversation.id
        logger.info("OneToOne Conversation created with ID: $conversationId")
        return conversationId
    }
}
