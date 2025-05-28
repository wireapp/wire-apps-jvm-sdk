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

package com.wire.integrations.jvm.model.http.user

import com.wire.integrations.jvm.model.CryptoProtocol
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.utils.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserResponse(
    @SerialName("qualified_id")
    val id: QualifiedId,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("team")
    val teamId: UUID?,
    @SerialName("email")
    val email: String?,
    @SerialName("name")
    val name: String,
    @SerialName("handle")
    val handle: String?,
    @SerialName("accent_id")
    val accentId: Long,
    @SerialName("supported_protocols")
    val supportedProtocols: List<CryptoProtocol>,
    @SerialName("deleted")
    val deleted: Boolean?
)
