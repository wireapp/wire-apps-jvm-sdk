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

package com.wire.integrations.jvm.model.http.client

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = CapabilitiesRequestSerializer::class)
enum class CapabilitiesRequest(val value: String) {
    LEGALHOLD_IMPLICIT_CONSENT("legalhold-implicit-consent"),
    CONSUMABLE_NOTIFICATIONS("consumable-notifications")
}

object CapabilitiesRequestSerializer : KSerializer<CapabilitiesRequest> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "CapabilitiesRequest",
            PrimitiveKind.STRING
        )

    override fun serialize(
        encoder: Encoder,
        value: CapabilitiesRequest
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): CapabilitiesRequest {
        val value = decoder.decodeString()
        return CapabilitiesRequest.entries.find { it.value == value }
            ?: throw SerializationException("Unknown capability: $value")
    }
}
