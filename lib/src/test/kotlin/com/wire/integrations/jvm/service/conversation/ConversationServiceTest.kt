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

import com.wire.crypto.toGroupId
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.CryptoProtocol
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.conversation.ConversationMembers
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import com.wire.integrations.jvm.persistence.AppStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class ConversationServiceTest {
    @Test
    fun whenEstablishingConversationsAndShouldRejoinConversationIsFalseThenSkip() =
        runTest {
            val appStorage = mockk<AppStorage> {
                coEvery { getShouldRejoinConversations() } returns false
            }
            val backendClient = mockk<BackendClient>()

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = mockk(),
                appStorage = appStorage,
                cryptoClient = mockk()
            )

            service.establishOrRejoinConversations()

            verify(exactly = 1) {
                appStorage.getShouldRejoinConversations()
            }
            coVerify(exactly = 0) {
                backendClient.getConversationIds()
            }
        }

    @Test
    fun whenEstablishingConversationsAndConversationsAreEmptyThenSkipAndMarkAsRejoined() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { getShouldRejoinConversations() } returns true
                every { setShouldRejoinConversations(any()) } returns Unit
            }
            val backendClient = mockk<BackendClient> {
                coEvery { getConversationIds() } returns listOf()
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = mockk(),
                appStorage = appStorage,
                cryptoClient = mockk()
            )

            service.establishOrRejoinConversations()

            verify(exactly = 1) {
                appStorage.getShouldRejoinConversations()
            }
            coVerify(exactly = 1) {
                backendClient.getConversationIds()
            }
            verify(exactly = 1) {
                appStorage.setShouldRejoinConversations(false)
            }
        }

    @Test
    fun whenEstablishingConversationsAndConversationsExistsLocallyThenSkip() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { getShouldRejoinConversations() } returns true
                every { setShouldRejoinConversations(any()) } returns Unit
            }
            val backendClient = mockk<BackendClient> {
                coEvery { getConversationIds() } returns listOf(
                    CONVERSATION_ID
                )
                coEvery {
                    getConversationFromIds(listOf(CONVERSATION_ID))
                } returns listOf(CONVERSATION_RESPONSE)
            }
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = mockk(),
                appStorage = appStorage,
                cryptoClient = cryptoClient
            )

            service.establishOrRejoinConversations()

            verify(exactly = 1) {
                appStorage.getShouldRejoinConversations()
            }
            coVerify(exactly = 1) {
                backendClient.getConversationIds()
            }
            verify(exactly = 1) {
                appStorage.setShouldRejoinConversations(false)
            }
            coVerify(exactly = 1) {
                cryptoClient.conversationExists(CONVERSATION_MLS_GROUP_ID)
            }
        }

    @Test
    fun whenEstablishingConversationsAndConversationsDoesNotExistsLocallyThenRejoin() =
        runTest {
            val appStorage = mockk<AppStorage> {
                every { getShouldRejoinConversations() } returns true
                every { setShouldRejoinConversations(any()) } returns Unit
            }
            val backendClient = mockk<BackendClient> {
                coEvery { getConversationIds() } returns listOf(CONVERSATION_ID)
                coEvery {
                    getConversationFromIds(listOf(CONVERSATION_ID))
                } returns listOf(CONVERSATION_RESPONSE)
                coEvery {
                    getConversationGroupInfo(conversationId = CONVERSATION_ID)
                } returns CONVERSATION_MLS_GROUP_ID.copyBytes()
            }
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns false
                coEvery {
                    joinMlsConversationRequest(any())
                } returns CONVERSATION_MLS_GROUP_ID
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = mockk(),
                appStorage = appStorage,
                cryptoClient = cryptoClient
            )

            service.establishOrRejoinConversations()

            verify(exactly = 1) {
                appStorage.getShouldRejoinConversations()
            }
            coVerify(exactly = 1) {
                backendClient.getConversationIds()
            }
            verify(exactly = 1) {
                appStorage.setShouldRejoinConversations(false)
            }
            coVerify(exactly = 1) {
                cryptoClient.conversationExists(CONVERSATION_MLS_GROUP_ID)
            }
            coVerify(exactly = 1) {
                cryptoClient.joinMlsConversationRequest(any())
            }
        }

    private companion object {
        val CONVERSATION_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
        val TEAM_ID = TeamId(UUID.randomUUID())
        val CONVERSATION_MLS_GROUP_ID = UUID.randomUUID().toString().toGroupId()
        val CONVERSATION_MLS_GROUP_ID_BASE64 =
            Base64.getEncoder().encodeToString(CONVERSATION_MLS_GROUP_ID.copyBytes())
        val CONVERSATION_RESPONSE = ConversationResponse(
            id = CONVERSATION_ID,
            teamId = TEAM_ID.value,
            groupId = CONVERSATION_MLS_GROUP_ID_BASE64,
            name = "Random Conversation",
            epoch = 1L,
            members = ConversationMembers(others = emptyList()),
            type = ConversationResponse.Type.GROUP,
            protocol = CryptoProtocol.MLS
        )
    }
}
