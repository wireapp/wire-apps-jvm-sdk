/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.sdk.service

import com.wire.crypto.ConversationId
import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import com.wire.crypto.toGroupInfo
import com.wire.sdk.client.CallingApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.DecryptedMlsMessage
import com.wire.sdk.exception.WireException
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.calling.SubconversationEpochInfo
import com.wire.sdk.model.http.conversation.SubconversationResponse
import com.wire.sdk.persistence.AppStorage
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.Base64
import kotlinx.coroutines.CancellationException

/**
 * Joins the single "conference" child group and applies incoming MLS updates.
 * Other clients initialize conferences and commit proposals. CoreCrypto persists MLS state;
 * the backend supplies the group ID when this process has no cached mapping.
 */
@Suppress("TooManyFunctions")
internal class SubconversationService(
    private val api: CallingApiClient,
    private val crypto: CryptoClient,
    private val appStorage: AppStorage
) : AutoCloseable {
    /** The caller owns [epochInfo] and must deliver it to the app or close it. */
    data class DecryptionResult(
        val message: DecryptedMlsMessage,
        val epochInfo: SubconversationEpochInfo? = null,
        val hasLeft: Boolean = false
    )

    private class Conference(val groupId: ConversationId) {
        var lastEpoch: Long? = null
    }

    private val conferences = ConcurrentHashMap<QualifiedId, Conference>()

    suspend fun join(conversationId: QualifiedId): SubconversationEpochInfo =
        catch {
            val remote = api.getConference(conversationId)
            joinRemote(conversationId, remote)
            val conference = conferences.getValue(conversationId)
            getSubconversationEpoch(conversationId, conference).also {
                conference.lastEpoch = it.epoch
            }
        }

    private suspend fun joinRemote(
        id: QualifiedId,
        remote: SubconversationResponse
    ) {
        if (remote.epoch == 0uL) {
            throw WireException.EntityNotFound(
                "The conference has not been initialized by another client"
            )
        }
        var groupId = ConversationId(Base64.decode(remote.groupId))
        val exists = crypto.conversationExists(groupId)
        val alreadyJoined = remote.hasSelf() &&
            exists &&
            crypto.conversationEpoch(groupId) == remote.epoch
        if (!alreadyJoined) {
            // Joining is the only conference commit the SDK initiates.
            crypto.joinMlsConversationRequest(api.getGroupInfo(id).toGroupInfo())
        }
        val previous = conferences[id]
        if (previous?.groupId != groupId) {
            conferences[id] = Conference(groupId)
        }
    }

    suspend fun leave(conversationId: QualifiedId) =
        catch {
            val remote = api.getConference(conversationId)
            if (conferences[conversationId] == null) restore(conversationId, remote)
            // Check remote membership so repeat leaves and restart recovery are safe.
            if (remote.hasSelf()) api.leaveConference(conversationId)
            // Keep the mapping until CoreCrypto applies the conference removal commit.
        }

    /**
     * Get subconversation conference either from cache or the backend, and decrypt the message.
     * If the conference is not found, return null.
     */
    suspend fun decrypt(
        conversationId: QualifiedId,
        data: String
    ): DecryptionResult? =
        catch {
            val conference = conferences[conversationId]
                ?: restore(conversationId, api.getConference(conversationId))
                ?: return@catch null
            val decrypted = try {
                crypto.decryptMls(conference.groupId, data) ?: return@catch null
            } catch (exception: CoreCryptoException.Mls) {
                if (exception.mlsError.isConsumed()) return@catch null
                throw exception
            } catch (exception: MlsException) {
                if (exception.isConsumed()) return@catch null
                throw exception
            }
            val messages = listOf(decrypted) + decrypted.bufferedMessages
            if (messages.any { !it.isActive }) {
                // CoreCrypto already deleted the group while applying the removal commit.
                val removed = conferences.remove(conversationId, conference)
                DecryptionResult(decrypted, hasLeft = removed)
            } else {
                DecryptionResult(decrypted, getEpochUpdate(conversationId, conference))
            }
        }

    private suspend fun restore(
        id: QualifiedId,
        remote: SubconversationResponse
    ): Conference? {
        val groupId = ConversationId(Base64.decode(remote.groupId))
        // Backend membership can already exclude us while the removal commit is still pending.
        if (!crypto.conversationExists(groupId)) return null
        return Conference(groupId).also { conferences[id] = it }
    }

    private fun SubconversationResponse.hasSelf(): Boolean =
        members.any { it.matches(appStorage.getApplicationQualifiedId(), appStorage.getDeviceId()) }

    private suspend fun getSubconversationEpoch(
        id: QualifiedId,
        conference: Conference
    ): SubconversationEpochInfo = crypto.getConferenceEpochInfo(id, conference.groupId)

    private suspend fun getEpochUpdate(
        id: QualifiedId,
        conference: Conference
    ): SubconversationEpochInfo? {
        val info = getSubconversationEpoch(id, conference)
        if (info.epoch == conference.lastEpoch) {
            info.close()
            return null
        }
        conference.lastEpoch = info.epoch
        return info
    }

    private fun MlsException.isConsumed(): Boolean =
        this is MlsException.DuplicateMessage ||
            this is MlsException.SelfCommitIgnored ||
            this is MlsException.BufferedFutureMessage ||
            this is MlsException.BufferedCommit ||
            this is MlsException.StaleProposal ||
            this is MlsException.StaleCommit ||
            this is MlsException.MessageEpochTooOld

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> catch(action: suspend () -> T): T =
        try {
            action()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: WireException) {
            throw exception
        } catch (exception: CoreCryptoException) {
            throw WireException.CryptographicSystemError("Conference operation failed", exception)
        } catch (exception: MlsException) {
            throw WireException.CryptographicSystemError("Conference operation failed", exception)
        } catch (exception: Exception) {
            throw WireException.UnknownError("Conference operation failed", exception)
        }

    override fun close() {
        conferences.clear()
    }
}
