/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.http.ApiVersionResponse
import com.wire.integrations.jvm.model.http.AppDataResponse
import com.wire.integrations.jvm.model.protobuf.ProtobufMapper
import com.wire.integrations.jvm.persistence.ConversationStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import kotlinx.coroutines.runBlocking

/**
 * Allows fetching common data and interacting with each Team instance invited to the Application.
 * Some functions are provided as blocking methods for Java interoperability or
 * as suspending methods for Kotlin consumers.
 */
class WireApplicationManager internal constructor(
    private val teamStorage: TeamStorage,
    private val conversationStorage: ConversationStorage,
    private val backendClient: BackendClient,
    private val cryptoClient: CryptoClient
) {
    fun getStoredTeams(): List<TeamId> = teamStorage.getAll()

    fun getStoredConversations(): List<ConversationData> = conversationStorage.getAll()

    /**
     * Get API configuration from the connected Wire backend.
     * Blocking method for Java interoperability
     */
    @Throws(WireException::class)
    fun getBackendConfiguration(): ApiVersionResponse =
        runBlocking {
            getBackendConfigurationSuspending()
        }

    /**
     * Get API configuration from the connected Wire backend.
     * Suspending method for Kotlin consumers
     */
    @Throws(WireException::class)
    suspend fun getBackendConfigurationSuspending(): ApiVersionResponse =
        backendClient.getBackendVersion()

    /**
     * Get the basic Wire Application data from the connected Wire backend.
     * Blocking method for Java interoperability
     */
    @Throws(WireException::class)
    fun getApplicationData(): AppDataResponse =
        runBlocking {
            getApplicationDataSuspending()
        }

    /**
     * Get the basic Wire Application data from the connected Wire backend.
     * Suspending method for Kotlin consumers
     */
    @Throws(WireException::class)
    suspend fun getApplicationDataSuspending(): AppDataResponse = backendClient.getApplicationData()

    /**
     * Sends a message to a conversation by getting the mlsGroupId and encrypting the message.
     *
     * Before calling this function, make sure that the conversation
     * has already been joined; otherwise, the message cannot be sent.
     *
     * The conversation ID can be retrieved from internal storage, but this is not required—if you
     * already have it from another source (from receiving events), you can use that instead.
     *
     * Blocking method for Java interoperability
     *
     * @param conversationId The unique ID of the conversation where the message should be sent.
     * @param message The text of the message to be sent.
     *
     * @throws WireException.EntityNotFound If the conversation cannot be found.
     */
    fun sendMessage(
        conversationId: QualifiedId,
        message: WireMessage
    ) {
        runBlocking {
            sendMessageSuspending(conversationId, message)
        }
    }

    /**
     * Sends a message to a conversation by getting the mlsGroupId and encrypting the message.
     *
     * Before calling this function, make sure that the conversation
     * has already been joined; otherwise, the message cannot be sent.
     *
     * The conversation ID can be retrieved from internal storage, but this is not required—if you
     * already have it from another source (from receiving events), you can use that instead.
     *
     * Suspending method for Kotlin consumers.
     *
     * @param conversationId The unique ID of the conversation where the message should be sent.
     * @param message The text of the message to be sent.
     *
     * @throws WireException.EntityNotFound If the conversation cannot be found.
     */
    suspend fun sendMessageSuspending(
        conversationId: QualifiedId,
        message: WireMessage
    ) {
        val conversation = conversationStorage.getById(conversationId = conversationId)
        conversation?.mlsGroupId?.let { mlsGroupId ->
            backendClient.sendMessage(
                mlsMessage = cryptoClient.encryptMls(
                    mlsGroupId = mlsGroupId,
                    message = ProtobufMapper.toGenericMessageByteArray(wireMessage = message)
                )
            )
        } ?: throw WireException.EntityNotFound("Couldn't find Conversation MLS Group ID")
    }

    /**
     * Downloads an asset as a raw byte array from the public assets endpoint.
     *
     * @param assetId The unique identifier of the asset to download.
     * @param assetDomain Optional domain or namespace under which the asset is categorized.
     * @param assetToken Optional token used for additional access control to the asset.
     * @return A [ByteArray] containing the raw binary content of the downloaded asset.
     *
     * @throws [WireException] If the request fails or an error occurs while fetching the asset.
     */
    suspend fun downloadAsset(
        assetId: String,
        assetDomain: String?,
        assetToken: String?
    ): ByteArray =
        backendClient.downloadAsset(
            assetId = assetId,
            assetDomain = assetDomain,
            assetToken = assetToken
        )
}
