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

package com.wire.sdk.model

import java.util.UUID

/**
 * Represents a Wire user exposed through the public SDK API.
 *
 * @property id The qualified user identity (UUID + domain).
 * @property name The display name of the user.
 * @property email The email address of the user.
 * @property handle The unique handle (username) of the user.
 * @property teamId The UUID of the team the user belongs to.
 * @property supportedProtocols The list of cryptographic protocols supported by the user.
 * @property deleted Whether the user account has been deleted.
 */
@JvmRecord
data class WireUser(
    val id: QualifiedId,
    val name: String,
    val email: String?,
    val handle: String?,
    val teamId: UUID?,
    val supportedProtocols: List<CryptoProtocol>,
    val deleted: Boolean?
)
