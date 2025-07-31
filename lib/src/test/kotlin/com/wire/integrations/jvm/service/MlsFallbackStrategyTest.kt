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

import com.wire.crypto.toGroupId
import com.wire.crypto.toGroupInfo
import com.wire.integrations.jvm.client.BackendClientDemo
import com.wire.integrations.jvm.crypto.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.conversation.ConversationMembers
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class MlsFallbackStrategyTest {
    @Test
    fun whenConversationDoesNotExistLocally_thenRejoinConversation() =
        runTest {
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(mlsGroupId = MLS_GROUP_ID) } returns false
                coEvery { conversationEpoch(mlsGroupId = MLS_GROUP_ID) } returns 0UL
                coEvery {
                    joinMlsConversationRequest(groupInfo = any())
                } returns MLS_GROUP_ID
            }

            val backendClient = mockk<BackendClientDemo> {
                coEvery {
                    getConversation(conversationId = CONVERSATION_ID)
                } returns CONVERSATION_RESPONSE
                coEvery {
                    getConversationGroupInfo(conversationId = CONVERSATION_ID)
                } returns MLS_GROUP_ID.copyBytes()
            }

            val fallbackStrategy = MlsFallbackStrategy(
                backendClient = backendClient,
                cryptoClient = cryptoClient
            )

            fallbackStrategy.verifyConversationOutOfSync(
                mlsGroupId = MLS_GROUP_ID,
                conversationId = CONVERSATION_ID
            )

            coVerify(exactly = 1) {
                cryptoClient.joinMlsConversationRequest(
                    groupInfo = any()
                )
            }
        }

    @Test
    fun whenConversationEpochIsOutOfSync_thenRejoinConversation() =
        runTest {
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(mlsGroupId = MLS_GROUP_ID) } returns true
                coEvery { conversationEpoch(mlsGroupId = MLS_GROUP_ID) } returns 1UL
                coEvery {
                    joinMlsConversationRequest(groupInfo = any())
                } returns MLS_GROUP_ID
            }

            val backendClient = mockk<BackendClientDemo> {
                coEvery {
                    getConversation(conversationId = CONVERSATION_ID)
                } returns CONVERSATION_RESPONSE.copy(epoch = 2L)
                coEvery {
                    getConversationGroupInfo(conversationId = CONVERSATION_ID)
                } returns MLS_GROUP_ID.copyBytes()
            }

            val fallbackStrategy = MlsFallbackStrategy(
                backendClient = backendClient,
                cryptoClient = cryptoClient
            )

            fallbackStrategy.verifyConversationOutOfSync(
                mlsGroupId = MLS_GROUP_ID,
                conversationId = CONVERSATION_ID
            )

            coVerify(exactly = 1) {
                cryptoClient.joinMlsConversationRequest(
                    groupInfo = any()
                )
            }
        }

    @Test
    fun whenConversationIsSynced_thenLogicIsIgnored() =
        runTest {
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(mlsGroupId = MLS_GROUP_ID) } returns true
                coEvery { conversationEpoch(mlsGroupId = MLS_GROUP_ID) } returns 1UL
                coEvery {
                    joinMlsConversationRequest(groupInfo = MLS_GROUP_ID.copyBytes().toGroupInfo())
                } returns MLS_GROUP_ID
            }

            val backendClient = mockk<BackendClientDemo> {
                coEvery {
                    getConversation(conversationId = CONVERSATION_ID)
                } returns CONVERSATION_RESPONSE.copy(epoch = 1L)
                coEvery {
                    getConversationGroupInfo(conversationId = CONVERSATION_ID)
                } returns MLS_GROUP_ID.copyBytes()
            }

            val fallbackStrategy = MlsFallbackStrategy(
                backendClient = backendClient,
                cryptoClient = cryptoClient
            )

            fallbackStrategy.verifyConversationOutOfSync(
                mlsGroupId = MLS_GROUP_ID,
                conversationId = CONVERSATION_ID
            )

            coVerify(exactly = 0) {
                cryptoClient.joinMlsConversationRequest(
                    groupInfo = MLS_GROUP_ID.copyBytes().toGroupInfo()
                )
                backendClient.getConversationGroupInfo(
                    conversationId = CONVERSATION_ID
                )
            }
        }

    companion object {
        private val CONVERSATION_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
        private val TEAM_ID = TeamId(UUID.randomUUID())
        private val MLS_GROUP_ID = ByteArray(32) { 1 }.toGroupId()
        private val CONVERSATION_RESPONSE = ConversationResponse(
            id = CONVERSATION_ID,
            teamId = TEAM_ID.value,
            groupId = MLS_GROUP_ID.toString(),
            name = "Random Conversation",
            epoch = 0L,
            members = ConversationMembers(others = emptyList()),
            type = ConversationResponse.Type.GROUP
        )
    }
}
