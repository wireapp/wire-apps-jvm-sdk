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

package com.wire.sdk.model.calling

import com.wire.sdk.model.QualifiedId

/**
 * A consistent snapshot of the conference MLS epoch and its calling key.
 * Pass the secret to your calling engine and close this snapshot when finished with it.
 * The SDK never stores this exported secret in its database.
 */
class SubconversationEpochInfo internal constructor(
    val conversationId: QualifiedId,
    val groupId: String,
    val epoch: Long,
    members: Map<QualifiedId, List<String>>,
    sharedSecret: ByteArray
) : AutoCloseable {
    /** Conference member user IDs mapped to their device IDs at this epoch. */
    val members: Map<QualifiedId, List<String>> = java.util.Collections.unmodifiableMap(
        members.mapValues { (_, devices) ->
            java.util.Collections.unmodifiableList(devices.toList())
        }
    )
    private val secret = sharedSecret.copyOf()

    /** Returns a copy. The caller owns and should clear the returned bytes after use. */
    @Synchronized
    fun getSharedSecret(): ByteArray = secret.copyOf()

    @Synchronized
    override fun close() {
        secret.fill(0)
    }

    override fun toString(): String =
        "SubconversationEpochInfo(conversationId=$conversationId, epoch=$epoch)"
}
