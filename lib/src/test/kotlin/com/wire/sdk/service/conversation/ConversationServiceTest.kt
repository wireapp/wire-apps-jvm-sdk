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

import com.wire.crypto.MlsException
import com.wire.crypto.toGroupId
import com.wire.sdk.TestUtils
import com.wire.sdk.client.BackendClient
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.ConversationMember
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.CryptoQualifiedId
import com.wire.sdk.model.MlsStatus
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.StandardError
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.FeaturesResponse
import com.wire.sdk.model.http.MlsFeatureConfigResponse
import com.wire.sdk.model.http.MlsFeatureResponse
import com.wire.sdk.model.http.conversation.ClaimedKeyPackageList
import com.wire.sdk.model.http.conversation.ConversationMembers
import com.wire.sdk.model.http.conversation.ConversationResponse
import com.wire.sdk.model.http.conversation.ConversationRole
import com.wire.sdk.model.http.conversation.KeyPackage
import com.wire.sdk.model.http.user.UserClientResponse
import com.wire.sdk.persistence.AppStorage
import com.wire.sdk.persistence.ConversationStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.verify
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeAll

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
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.MEMBER
                    )
                )
                every { deleteAllMembersInConversation(CONVERSATION_ID) } returns Unit
                every { delete(CONVERSATION_ID) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery {
                    leaveConversation(QualifiedId(APP_USER_ID, BACKEND_DOMAIN), CONVERSATION_ID)
                } returns
                    Unit
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
                coEvery { wipeConversation(CONVERSATION_MLS_GROUP_ID) } returns Unit
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            service.leaveConversation(CONVERSATION_ID)

            coVerify(exactly = 1) {
                backendClient.leaveConversation(
                    userId = QualifiedId(
                        id = APP_USER_ID,
                        domain = BACKEND_DOMAIN
                    ),
                    conversationId = CONVERSATION_ID
                )
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
                        userId = QualifiedId(anotherUserId, BACKEND_DOMAIN),
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

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.Forbidden> {
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
    fun whenLeavingConversationAndTypeIsNotGroupThenThrowInvalidParameter() =
        runTest {
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

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.InvalidParameter> {
                service.leaveConversation(CONVERSATION_ID)
            }
        }

    @Test
    fun whenDeletingGroupConversationAndUserIsAdminThenDeleteEverywhere() =
        runTest {
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
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    ),
                    ConversationMember(
                        userId = QualifiedId(otherUserId, BACKEND_DOMAIN),
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
                coEvery {
                    getConversation(CONVERSATION_ID)
                } throws WireException.ClientError(
                    response = StandardError(
                        code = 404,
                        label = "no-conversation",
                        message = "Conversation not found"
                    ),
                    throwable = null
                )
                coEvery { deleteConversation(TEAM_ID, CONVERSATION_ID) } returns Unit
            }
            val cryptoClient = mockk<CryptoClient> {
                coEvery { conversationExists(CONVERSATION_MLS_GROUP_ID) } returns true
                coEvery { wipeConversation(CONVERSATION_MLS_GROUP_ID) } returns Unit
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            assertFailsWith<WireException.ClientError> {
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

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.InvalidParameter> {
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
                        userId = QualifiedId(otherUserId, BACKEND_DOMAIN),
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

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.Forbidden> {
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
    fun whenAddingMembersToConversationSuccessfullyThenSaveMembersAndReturnResult() =
        runTest {
            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val claimResult = ClaimedKeyPackageList(
                keyPackages = listOf(
                    createDummyKeyPackage(CONVERSATION_MEMBER_1),
                    createDummyKeyPackage(CONVERSATION_MEMBER_2)
                )
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
                every { saveMembers(CONVERSATION_ID, any()) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery { getApplicationFeatures() } returns FEATURES_RESPONSE
                coEvery { claimKeyPackages(any(), any()) } returns claimResult
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery {
                    addMemberToMlsConversation(CONVERSATION_MLS_GROUP_ID, any())
                } returns Unit
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            val result = service.addMembersToConversation(
                conversationId = CONVERSATION_ID,
                members = listOf(CONVERSATION_MEMBER_1, CONVERSATION_MEMBER_2)
            )

            assertEquals(2, result.successUsers.size)
            assertEquals(0, result.failedUsers.size)

            coVerify(exactly = 1) {
                cryptoClient.addMemberToMlsConversation(CONVERSATION_MLS_GROUP_ID, any())
            }
            verify(exactly = 1) {
                conversationStorage.saveMembers(CONVERSATION_ID, any())
            }
        }

    @Test
    fun whenAddingMembersToConversationWithEmptyListThenThrowInvalidParameter() =
        runTest {
            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = mockk(),
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.InvalidParameter> {
                service.addMembersToConversation(CONVERSATION_ID, emptyList())
            }
        }

    @Test
    fun whenAddingMembersToConversationAndTypeIsNotGroupThenThrowInvalidParameter() =
        runTest {
            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.ONE_TO_ONE
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
            }

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.InvalidParameter> {
                service.addMembersToConversation(
                    conversationId = CONVERSATION_ID,
                    members = listOf(CONVERSATION_MEMBER_1)
                )
            }
        }

    @Test
    fun whenAddingMembersToConversationAndUserIsNotAdminThenThrowForbidden() =
        runTest {
            val otherUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.MEMBER // Not an admin
                    ),
                    ConversationMember(
                        userId = QualifiedId(otherUserId, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
            }

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.Forbidden> {
                service.addMembersToConversation(
                    conversationId = CONVERSATION_ID,
                    members = listOf(CONVERSATION_MEMBER_1)
                )
            }
        }

    @Test
    fun whenAddingMembersToConversationAndUserNotInConversationThenThrowForbidden() =
        runTest {
            val otherUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(otherUserId, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                    // App user not in the list
                )
            }

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.Forbidden> {
                service.addMembersToConversation(
                    conversationId = CONVERSATION_ID,
                    members = listOf(CONVERSATION_MEMBER_1)
                )
            }
        }

    @Test
    fun whenAddingMembersToConversationAndClaimKeyPackagesFailsThenThrowInvalidParameter() =
        runTest {
            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val claimResult = ClaimedKeyPackageList(
                keyPackages = listOf(createDummyKeyPackage(CONVERSATION_MEMBER_1))
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
            }

            val backendClient = mockk<BackendClient> {
                coEvery { getApplicationFeatures() } returns FEATURES_RESPONSE
                coEvery { claimKeyPackages(any(), any()) } returns claimResult
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery {
                    addMemberToMlsConversation(CONVERSATION_MLS_GROUP_ID, any())
                } throws MlsException.Other("Failed to add member")
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            assertFailsWith<WireException.InvalidParameter> {
                service.addMembersToConversation(
                    conversationId = CONVERSATION_ID,
                    members = listOf(CONVERSATION_MEMBER_1)
                )
            }

            coVerify(exactly = 1) {
                cryptoClient.addMemberToMlsConversation(CONVERSATION_MLS_GROUP_ID, any())
            }
            verify(exactly = 0) {
                conversationStorage.saveMembers(any(), any())
            }
        }

    @Test
    fun whenAddingMembersToConversationWithPartialSuccessThenReturnMixedResult() =
        runTest {
            val successMember = CONVERSATION_MEMBER_1
            val failedMember = CONVERSATION_MEMBER_2
            val membersToAdd = listOf(successMember, failedMember)

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val keyPackage1 = KeyPackage(
                clientID = UUID.randomUUID().toString(),
                domain = BACKEND_DOMAIN,
                keyPackage = UUID.randomUUID().toString(),
                keyPackageRef = UUID.randomUUID().toString(),
                userId = successMember.id.toString()
            )
            val claimResult = ClaimedKeyPackageList(
                keyPackages = listOf(keyPackage1)
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
                every { saveMembers(CONVERSATION_ID, any()) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery { getApplicationFeatures() } returns FEATURES_RESPONSE
                coEvery { claimKeyPackages(successMember, any()) } returns claimResult
                coEvery {
                    claimKeyPackages(failedMember, any())
                } throws WireException.ClientError(
                    response = StandardError(
                        code = 404,
                        label = "no-user",
                        message = "User not found"
                    ),
                    throwable = null
                )
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery {
                    addMemberToMlsConversation(CONVERSATION_MLS_GROUP_ID, any())
                } returns Unit
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            val result = service.addMembersToConversation(CONVERSATION_ID, membersToAdd)

            assertEquals(1, result.successUsers.size)
            assertEquals(1, result.failedUsers.size)
            assertEquals(successMember, result.successUsers[0])
            assertEquals(failedMember, result.failedUsers[0])

            verify(exactly = 1) {
                conversationStorage.saveMembers(CONVERSATION_ID, any())
            }
        }

    @Test
    fun whenRemovingMembersFromConversationSuccessfullyThenRemoveMembersAndCleanup() =
        runTest {
            val membersToRemove = listOf(CONVERSATION_MEMBER_1)

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val client = UserClientResponse(id = "client1")

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
                every { deleteMembers(CONVERSATION_ID, membersToRemove) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery { getClientsByUserId(CONVERSATION_MEMBER_1) } returns listOf(client)
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery {
                    removeMembersFromConversation(CONVERSATION_MLS_GROUP_ID, any())
                } returns Unit
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            service.removeMembersFromConversation(CONVERSATION_ID, membersToRemove)

            coVerify(exactly = 1) {
                backendClient.getClientsByUserId(CONVERSATION_MEMBER_1)
                cryptoClient.removeMembersFromConversation(CONVERSATION_MLS_GROUP_ID, any())
            }
            verify(exactly = 1) {
                conversationStorage.deleteMembers(CONVERSATION_ID, membersToRemove)
            }
        }

    @Test
    fun whenRemovingMultipleMembersFromConversationThenUseGetUsersClients() =
        runTest {
            val membersToRemove = listOf(CONVERSATION_MEMBER_1, CONVERSATION_MEMBER_2)

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val client1 = UserClientResponse(id = "client1")
            val client2 = UserClientResponse(id = "client2")

            val usersClientsMap = mapOf(
                BACKEND_DOMAIN to mapOf(
                    CONVERSATION_MEMBER_1.id.toString() to listOf(client1),
                    CONVERSATION_MEMBER_2.id.toString() to listOf(client2)
                )
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
                every { deleteMembers(CONVERSATION_ID, membersToRemove) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery { getClientsByUserIds(membersToRemove) } returns usersClientsMap
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery {
                    removeMembersFromConversation(CONVERSATION_MLS_GROUP_ID, any())
                } returns Unit
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            service.removeMembersFromConversation(CONVERSATION_ID, membersToRemove)

            coVerify(exactly = 1) {
                backendClient.getClientsByUserIds(membersToRemove)
                cryptoClient.removeMembersFromConversation(CONVERSATION_MLS_GROUP_ID, any())
            }
            coVerify(exactly = 0) {
                backendClient.getClientsByUserId(any())
            }
            verify(exactly = 1) {
                conversationStorage.deleteMembers(CONVERSATION_ID, membersToRemove)
            }
        }

    @Test
    fun whenRemovingMembersFromConversationWithEmptyListThenThrowInvalidParameter() =
        runTest {
            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = mockk(),
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.InvalidParameter> {
                service.removeMembersFromConversation(CONVERSATION_ID, emptyList())
            }
        }

    @Test
    fun whenRemovingMembersFromConversationAndTypeIsNotGroupThenThrowInvalidParameter() =
        runTest {
            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.ONE_TO_ONE
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
            }

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.InvalidParameter> {
                service.removeMembersFromConversation(
                    conversationId = CONVERSATION_ID,
                    members = listOf(CONVERSATION_MEMBER_1)
                )
            }
        }

    @Test
    fun whenRemovingMembersFromConversationAndUserIsNotAdminThenThrowForbidden() =
        runTest {
            val otherUserId = UUID.randomUUID()

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.MEMBER // Not an admin
                    ),
                    ConversationMember(
                        userId = QualifiedId(otherUserId, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
            }

            val service = ConversationService(
                backendClient = mockk(),
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = mockk()
            )

            assertFailsWith<WireException.Forbidden> {
                service.removeMembersFromConversation(
                    conversationId = CONVERSATION_ID,
                    members = listOf(CONVERSATION_MEMBER_1)
                )
            }
        }

    @Test
    fun whenRemovingMembersFromConversationAndCryptoFailsThenThrowException() =
        runTest {
            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val client1 = UserClientResponse(id = "client1")

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
            }

            val backendClient = mockk<BackendClient> {
                coEvery { getClientsByUserId(CONVERSATION_MEMBER_1) } returns listOf(client1)
            }

            val cryptoClient = mockk<CryptoClient> {
                coEvery {
                    removeMembersFromConversation(CONVERSATION_MLS_GROUP_ID, any())
                } throws MlsException.Other("Failed to remove members")
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            assertFailsWith<MlsException.Other> {
                service.removeMembersFromConversation(
                    conversationId = CONVERSATION_ID,
                    members = listOf(CONVERSATION_MEMBER_1)
                )
            }

            coVerify(exactly = 1) {
                cryptoClient.removeMembersFromConversation(CONVERSATION_MLS_GROUP_ID, any())
            }
            verify(exactly = 0) {
                conversationStorage.deleteMembers(any(), any())
            }
        }

    @Test
    fun whenRemovingSingleMemberWithMultipleClientsThenRemoveAllClients() =
        runTest {
            val membersToRemove = listOf(CONVERSATION_MEMBER_1)

            val conversationEntity = ConversationEntity(
                id = CONVERSATION_ID,
                name = "Test Conversation",
                mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                teamId = TEAM_ID,
                type = ConversationEntity.Type.GROUP
            )

            val client1 = UserClientResponse(id = "client1")
            val client2 = UserClientResponse(id = "client2")
            val client3 = UserClientResponse(id = "client3")

            val conversationStorage = mockk<ConversationStorage> {
                every { getById(CONVERSATION_ID) } returns conversationEntity
                every { getMembersByConversationId(CONVERSATION_ID) } returns listOf(
                    ConversationMember(
                        userId = QualifiedId(APP_USER_ID, BACKEND_DOMAIN),
                        role = ConversationRole.ADMIN
                    )
                )
                every { deleteMembers(CONVERSATION_ID, membersToRemove) } returns Unit
            }

            val backendClient = mockk<BackendClient> {
                coEvery {
                    getClientsByUserId(CONVERSATION_MEMBER_1)
                } returns listOf(client1, client2, client3)
            }

            val capturedClients = slot<List<CryptoQualifiedId>>()
            val cryptoClient = mockk<CryptoClient> {
                coEvery {
                    removeMembersFromConversation(
                        mlsGroupId = CONVERSATION_MLS_GROUP_ID,
                        clientIds = capture(lst = capturedClients)
                    )
                } returns Unit
            }

            val service = ConversationService(
                backendClient = backendClient,
                conversationStorage = conversationStorage,
                appStorage = mockk(),
                cryptoClient = cryptoClient
            )

            service.removeMembersFromConversation(CONVERSATION_ID, membersToRemove)

            assertEquals(3, capturedClients.captured.size)

            coVerify(exactly = 1) {
                backendClient.getClientsByUserId(CONVERSATION_MEMBER_1)
                cryptoClient.removeMembersFromConversation(CONVERSATION_MLS_GROUP_ID, any())
            }
            verify(exactly = 1) {
                conversationStorage.deleteMembers(CONVERSATION_ID, membersToRemove)
            }
        }

    private companion object {
        const val BACKEND_DOMAIN = "wire.com"
        val CONVERSATION_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = BACKEND_DOMAIN
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
            members = ConversationMembers(
                others = emptyList(),
                self = TestUtils.dummyConversationMemberSelf(ConversationRole.MEMBER)
            ),
            type = ConversationResponse.Type.GROUP,
            protocol = CryptoProtocol.MLS
        )
        val FEATURES_RESPONSE = FeaturesResponse(
            mlsFeatureResponse = MlsFeatureResponse(
                mlsFeatureConfigResponse = MlsFeatureConfigResponse(
                    allowedCipherSuites = listOf(1),
                    defaultCipherSuite = 1,
                    defaultProtocol = CryptoProtocol.MLS,
                    supportedProtocols = listOf(CryptoProtocol.MLS)
                ),
                status = MlsStatus.ENABLED
            )
        )

        val APP_USER_ID: UUID = UUID.randomUUID()
        val CONVERSATION_MEMBER_1 = QualifiedId(UUID.randomUUID(), BACKEND_DOMAIN)
        val CONVERSATION_MEMBER_2 = QualifiedId(UUID.randomUUID(), BACKEND_DOMAIN)

        fun createDummyKeyPackage(userId: QualifiedId): KeyPackage =
            KeyPackage(
                clientID = UUID.randomUUID().toString(),
                domain = BACKEND_DOMAIN,
                keyPackage = UUID.randomUUID().toString(),
                keyPackageRef = UUID.randomUUID().toString(),
                userId = userId.id.toString()
            )

        @JvmStatic
        @BeforeAll
        fun setUp() {
            mockkObject(IsolatedKoinContext)
            every { IsolatedKoinContext.getApplicationId() } returns APP_USER_ID
            every { IsolatedKoinContext.getBackendDomain() } returns BACKEND_DOMAIN
        }
    }
}
