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

import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.utils.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class ConversationResponse(
    @SerialName("qualified_id")
    val id: QualifiedId,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("team")
    val teamId: UUID?,
    @SerialName("group_id")
    val groupId: String,
    @SerialName("name")
    val name: String?,
    @SerialName("epoch")
    val epoch: Long,
    @SerialName("members")
    val members: ConversationMembers,
    @Serializable(with = ConversationTypeSerializer::class)
    val type: Type
) {
    enum class Type(val id: Int) {
        GROUP(0),
        SELF(1),
        ONE_TO_ONE(2);

        companion object {
            fun fromId(id: Int): Type = entries.first { type -> type.id == id }
        }
    }
}

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

class ConversationTypeSerializer : KSerializer<ConversationResponse.Type> {
    override val descriptor = PrimitiveSerialDescriptor("type", PrimitiveKind.INT)

    override fun serialize(
        encoder: Encoder,
        value: ConversationResponse.Type
    ): Unit = encoder.encodeInt(value.id)

    override fun deserialize(decoder: Decoder): ConversationResponse.Type {
        val rawValue = decoder.decodeInt()
        return ConversationResponse.Type.fromId(rawValue)
    }
}
