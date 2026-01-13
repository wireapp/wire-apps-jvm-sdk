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
import com.wire.sdk.TestUtils
import com.wire.sdk.client.BackendClient
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.conversation.ConversationMembers
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.persistence.ConversationStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
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
                coEvery { getConversationsById(any()) } returns listOf()
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
                    getConversationsById(listOf(CONVERSATION_ID))
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
                    getConversationsById(listOf(CONVERSATION_ID))
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

    @Test
    fun whenLeavingConversationSuccessfullyThenInvokeBackendAndCleanup() =
        runTest {
            val appUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test-Group-Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(appUserId, "wire.com"),
                        role = ConversationRole.MEMBER
                    )
                )
                every { deleteAllMembersInConversation(CONVERSATION_ID) } returns Unit
                every { delete(CONVERSATION_ID) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery {
                    leaveConversation(QualifiedId(appUserId, "wire.com"), CONVERSATION_ID)
                } returns
                    Unit
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
                coEvery { wipeConversation(CONVERSATION_MLS_GROUP_ID) } returns Unit
            }

            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns appUserId
            every { IsolatedKoinContext.getBackendDomain() } returns "wire.com"

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            service.leaveConversation(CONVERSATION_ID)

            coVerify(exactly = 1) {
                backendClient.leaveConversation(QualifiedId(appUserId, "wire.com"), CONVERSATION_ID)
                cryptoClient.wipeConversation(CONVERSATION_MLS_GROUP_ID)
            }
            verify(exactly = 1) {
                conversationStorage.deleteAllMembersInConversation(CONVERSATION_ID)
                conversationStorage.delete(CONVERSATION_ID)
            }
        }

    @Test
    fun whenLeavingConversationAndAppUserNotInConversationThenThrowForbidden() =
        runTest {
            val appUserId = UUID.randomUUID()
            val anotherUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test-Group-Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(anotherUserId, "wire.com"),
                        role = ConversationRole.MEMBER
                    )
                )
            }

            val backendClient = mockk<BackendClient> {
                coEvery { leaveConversation(any(), any()) } returns Unit
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(any()) } returns true
                coEvery { wipeConversation(any()) } returns Unit
            }

            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns appUserId
            every { IsolatedKoinContext.getBackendDomain() } returns "wire.com"

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            kotlin.test.assertFailsWith<WireException.Forbidden> {
                service.leaveConversation(CONVERSATION_ID)
            }

            coVerify(exactly = 0) {
                backendClient.leaveConversation(any(), any())
                cryptoClient.wipeConversation(any())
            }
            verify(exactly = 0) {
                conversationStorage.deleteAllMembersInConversation(any())
                conversationStorage.delete(any())
            }
        }

    @Test
    fun whenLeavingConversationAndAppUserIdIsNullThenThrowInvalidParameter() =
        runTest {
            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test-Group-Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
            }

            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns null

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            kotlin.test.assertFailsWith<WireException.InvalidParameter> {
                service.leaveConversation(CONVERSATION_ID)
            }
        }

    @Test
    fun whenLeavingConversationAndTypeIsNotGroupThenThrowInvalidParameter() =
        runTest {
            val appUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test-OneToOne-Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.ONE_TO_ONE
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
            }

            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns appUserId
            every { IsolatedKoinContext.getBackendDomain() } returns "wire.com"

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            kotlin.test.assertFailsWith<WireException.InvalidParameter> {
                service.leaveConversation(CONVERSATION_ID)
            }
        }

    @Test
    fun whenDeletingGroupConversationAndUserIsAdminThenDeleteEverywhere() =
        runTest {
            val appUserId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test-Group-Conversation-1",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(appUserId, "wire.com"),
                        role = ConversationRole.ADMIN
                    ),
                    ConversationMember(
                        userId = QualifiedId(otherUserId, "wire.com"),
                        role = ConversationRole.MEMBER
                    )
                )
                every { deleteAllMembersInConversation(CONVERSATION_ID) } returns Unit
                every { delete(CONVERSATION_ID) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery { deleteConversation(TEAM_ID, CONVERSATION_ID) } returns Unit
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
                coEvery { wipeConversation(CONVERSATION_MLS_GROUP_ID) } returns Unit
            }

            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns appUserId

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            service.deleteConversation(CONVERSATION_ID)

            coVerify(exactly = 1) {
                backendClient.deleteConversation(TEAM_ID, CONVERSATION_ID)
                cryptoClient.wipeConversation(CONVERSATION_MLS_GROUP_ID)
            }

            verify(exactly = 1) {
                conversationStorage.deleteAllMembersInConversation(CONVERSATION_ID)
                conversationStorage.delete(CONVERSATION_ID)
            }
        }

    @Test
    fun whenDeletingGroupConversationAndConversationDoesNotExistThenSkip() =
        runTest {
            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns null
            }
            val backendClient = mockk<BackendClient> {
                coEvery { deleteConversation(TEAM_ID, CONVERSATION_ID) } returns Unit
            }
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
                coEvery { wipeConversation(CONVERSATION_MLS_GROUP_ID) } returns Unit
            }

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            kotlin.test.assertFailsWith<WireException.EntityNotFound> {
                service.deleteConversation(CONVERSATION_ID)
            }

            verify(exactly = 1) {
                conversationStorage.getById(CONVERSATION_ID)
            }

            coVerify(exactly = 0) {
                backendClient.deleteConversation(any(), any())
                cryptoClient.wipeConversation(any())
            }
            verify(exactly = 0) {
                conversationStorage.deleteAllMembersInConversation(any())
                conversationStorage.delete(any())
            }
        }

    @Test
    fun whenDeletingConversationAndTypeIsNotGroupThenThrowInvalidParameter() =
        runTest {
            val appUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test-OneToOne-Conversation-1",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.ONE_TO_ONE
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
            }
            val backendClient = mockk<BackendClient> {
                coEvery { deleteConversation(TEAM_ID, CONVERSATION_ID) } returns Unit
            }
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
                coEvery { wipeConversation(CONVERSATION_MLS_GROUP_ID) } returns Unit
            }

            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns appUserId

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            kotlin.test.assertFailsWith<WireException.InvalidParameter> {
                service.deleteConversation(CONVERSATION_ID)
            }
            coVerify(exactly = 0) {
                backendClient.deleteConversation(any(), any())
                cryptoClient.wipeConversation(any())
            }
            verify(exactly = 0) {
                conversationStorage.deleteAllMembersInConversation(any())
                conversationStorage.delete(any())
            }
        }

    @Test
    fun whenDeletingGroupConversationAndUserIsNotAdminThenThrowForbidden() =
        runTest {
            val appUserId = UUID.randomUUID()
            val otherUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test-Group-Conversation-2",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(otherUserId, "wire.com"),
                        role = ConversationRole.ADMIN
                    )
                )
            }
            val backendClient = mockk<BackendClient> {
                coEvery { deleteConversation(TEAM_ID, CONVERSATION_ID) } returns Unit
            }
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
                coEvery { wipeConversation(CONVERSATION_MLS_GROUP_ID) } returns Unit
            }

            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns appUserId

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            kotlin.test.assertFailsWith<WireException.Forbidden> {
                service.deleteConversation(CONVERSATION_ID)
            }
            coVerify(exactly = 0) {
                backendClient.deleteConversation(any(), any())
                cryptoClient.wipeConversation(any())
            }
            verify(exactly = 0) {
                conversationStorage.deleteAllMembersInConversation(any())
                conversationStorage.delete(any())
            }
        }

    private companion object {
        val CONVERSATION_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
        val TEAM_ID = TeamId(UUID.randomUUID())
        val CONVERSATION_MLS_GROUP_ID = ConversationId(UUID.randomUUID().toString().toByteArray())
        val CONVERSATION_MLS_GROUP_ID_BASE64 =
            Base64.getEncoder().encodeToString(CONVERSATION_MLS_GROUP_ID.copyBytes())
        val CONVERSATION_RESPONSE = ConversationResponse(
            id = CONVERSATION_ID,
            teamId = TEAM_ID.value,
            groupId = CONVERSATION_MLS_GROUP_ID_BASE64,
            name = "Random Conversation",
            epoch = 1L,
            members = ConversationMembers(
                others = emptyList(),
                self = TestUtils.dummyConversationMemberSelf(ConversationRole.MEMBER)
            ),
            type = ConversationResponse.Type.GROUP,
            protocol = CryptoProtocol.MLS
        )
    }
}
