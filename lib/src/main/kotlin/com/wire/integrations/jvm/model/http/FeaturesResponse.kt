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

package com.wire.integrations.jvm.model.http

import com.wire.integrations.jvm.model.CryptoProtocol
import com.wire.integrations.jvm.model.MlsStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeaturesResponse(
    @SerialName("mls")
    val mlsFeatureResponse: MlsFeatureResponse
) {
    fun isMlsEnabled(): Boolean = mlsFeatureResponse.status == MlsStatus.ENABLED
}

@Serializable
data class MlsFeatureResponse(
    @SerialName("config")
    val mlsFeatureConfigResponse: MlsFeatureConfigResponse,
    @SerialName("status")
    val status: MlsStatus
)

@Serializable
data class MlsFeatureConfigResponse(
    @SerialName("allowedCipherSuites")
    var allowedCipherSuites: List<Int>,
    @SerialName("defaultCipherSuite")
    var defaultCipherSuite: Int,
    @SerialName("defaultProtocol")
    var defaultProtocol: CryptoProtocol,
    @SerialName("supportedProtocols")
    var supportedProtocols: List<CryptoProtocol>
)
