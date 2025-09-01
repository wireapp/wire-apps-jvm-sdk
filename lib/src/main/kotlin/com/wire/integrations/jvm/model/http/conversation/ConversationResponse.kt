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

package com.wire.integrations.jvm.model.http.conversation

import com.wire.crypto.MLSGroupId
import com.wire.crypto.toGroupId
import com.wire.integrations.jvm.exception.WireException
import com.wire.integrations.jvm.model.CryptoProtocol
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.utils.UUIDSerializer
import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ConversationResponse(
    @SerialName("qualified_id")
    val id: QualifiedId,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("team")
    val teamId: UUID?,
    @SerialName("group_id")
    val groupId: String?,
    @SerialName("name")
    val name: String?,
    @SerialName("epoch")
    val epoch: Long?,
    @SerialName("protocol")
    val protocol: CryptoProtocol,
    @SerialName("members")
    val members: ConversationMembers,
    @SerialName("type")
    val type: Type,
    @SerialName("public_keys")
    val publicKeys: MlsPublicKeysResponse? = null
) {
    @Serializable
    enum class Type {
        @SerialName("0")
        GROUP,

        @SerialName("1")
        SELF,

        @SerialName("2")
        ONE_TO_ONE
    }
}

fun ConversationResponse.getDecodedMlsGroupId(): MLSGroupId =
    this.groupId?.decodeBase64Bytes()?.toGroupId()
        ?: throw WireException.MissingParameter("MLSGroupId should not be empty or null.")

@Serializable
data class OneToOneConversationResponse(
    @SerialName("public_keys")
    val publicKeys: MlsPublicKeysResponse? = null,
    @SerialName("conversation")
    val conversation: ConversationResponse
)

@Serializable
data class ConversationMembers(
    @SerialName("others")
    val others: List<ConversationMemberOther>
)

@Serializable
data class ConversationMemberOther(
    @SerialName("qualified_id")
    val id: QualifiedId,
    @SerialName("conversation_role")
    val conversationRole: ConversationRole
)
