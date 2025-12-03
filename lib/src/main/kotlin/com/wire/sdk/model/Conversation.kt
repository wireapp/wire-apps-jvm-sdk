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

package com.wire.sdk.model

data class Conversation(
    val id: QualifiedId,
    val name: String?,
    val teamId: TeamId?,
    val type: Type
) {
    enum class Type {
        GROUP,
        ONE_TO_ONE
    }

    companion object {
        internal fun fromEntity(conversationEntity: ConversationEntity): Conversation =
            Conversation(
                id = conversationEntity.id,
                name = conversationEntity.name,
                teamId = conversationEntity.teamId,
                type = when (conversationEntity.type) {
                    ConversationEntity.Type.GROUP -> Type.GROUP
                    ConversationEntity.Type.ONE_TO_ONE -> Type.ONE_TO_ONE
                    ConversationEntity.Type.SELF -> {
                        error("App cannot be added to Self conversation.")
                    }
                }
            )
    }
}
