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

import com.wire.sdk.utils.UUIDSerializer
import com.wire.sdk.utils.obfuscateId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@JvmRecord
@Serializable
data class QualifiedId(
    @SerialName("id")
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @SerialName("domain")
    val domain: String
) {
    override fun toString(): String = "${id.obfuscateId()}@$domain"
}
