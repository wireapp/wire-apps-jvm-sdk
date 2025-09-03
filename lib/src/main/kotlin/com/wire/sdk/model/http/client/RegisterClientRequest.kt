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

package com.wire.sdk.model.http.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterClientRequest(
    val password: String,
    val type: String = "temporary",
    val model: String = "App Client",
    @SerialName("lastkey")
    val lastKey: PreKeyRequest,
    @SerialName("prekeys")
    val preKeys: List<PreKeyRequest>,
    val capabilities: List<CapabilitiesRequest>
) {
    companion object {
        val DEFAULT_CAPABILITIES = listOf<CapabilitiesRequest>(
            CapabilitiesRequest.LEGALHOLD_IMPLICIT_CONSENT,
            CapabilitiesRequest.CONSUMABLE_NOTIFICATIONS
        )
    }
}
