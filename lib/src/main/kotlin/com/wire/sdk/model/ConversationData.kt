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

package com.wire.sdk.model

import com.wire.crypto.MLSGroupId
import com.wire.sdk.model.http.conversation.ConversationResponse

@JvmRecord
data class ConversationData(
    val id: QualifiedId,
    val name: String?,
    val teamId: TeamId?,
    internal val mlsGroupId: MLSGroupId,
    val type: Type
) {
    enum class Type {
        GROUP,
        ONE_TO_ONE;

        companion object {
            fun fromApi(value: ConversationResponse.Type): Type =
                when (value) {
                    ConversationResponse.Type.GROUP -> GROUP
                    ConversationResponse.Type.ONE_TO_ONE -> ONE_TO_ONE
                    ConversationResponse.Type.SELF -> {
                        error("App cannot be added to Self conversation.")
                    }
                }
        }
    }
}
