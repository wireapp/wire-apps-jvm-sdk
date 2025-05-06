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

package com.wire.integrations.jvm.model.asset

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetUploadData(
    // Public means not encrypted, like profile pictures. All other files should be private
    @SerialName("public")
    val public: Boolean = false,
    @SerialName("retention")
    val retention: AssetRetention,
    // MD5 hash of the file, used for checksum
    @SerialName("md5")
    val md5: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssetUploadData) return false

        if (public != other.public) return false
        if (retention != other.retention) return false
        if (!md5.contentEquals(other.md5)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = public.hashCode()
        result = 31 * result + retention.hashCode()
        result = 31 * result + md5.contentHashCode()
        return result
    }
}
