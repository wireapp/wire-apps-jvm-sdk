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

package com.wire.sdk.model.asset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AssetRetention(val value: String) {
    /**
     * The asset is retained indefinitely.
     * Typically used for profile pictures / assets frequently accessed.
     */
    @SerialName("eternal")
    ETERNAL("eternal"),

    /**
     * The asset is retained indefinitely.
     * Typically used for profile pictures / assets frequently accessed.
     */
    @SerialName("persistent")
    @Deprecated("Use ETERNAL instead")
    PERSISTENT("persistent"),

    /**
     * The asset is retained for a short period of time.
     */
    @SerialName("volatile")
    VOLATILE("volatile"),

    /**
     * The asset is retained indefinitely, storage is optimised
     * for infrequent access.
     */
    @SerialName("eternal-infrequent_access")
    ETERNAL_INFREQUENT_ACCESS("eternal-infrequent_access"),

    /**
     * The asset is retained for an extended period of time,
     * but not indefinitely.
     */
    @SerialName("expiring")
    EXPIRING("expiring")
}
