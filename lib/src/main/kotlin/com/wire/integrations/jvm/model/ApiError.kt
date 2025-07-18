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

package com.wire.integrations.jvm.model

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

object BaseSerializer : JsonContentPolymorphicSerializer<ApiError>(ApiError::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ApiError> {
        val jsonObject = element.jsonObject
        return when {
            jsonObject.keys.containsAll(
                listOf("code", "label", "message")
            ) -> ApiError.Standard.serializer()
            // Extend here for new types, e.g.:
            // jsonObject.containsKey("unreachable_backends") -> ApiError.Backend.serializer()
            else -> throw IllegalArgumentException("Unsupported API Error type. $jsonObject")
        }
    }
}

@Serializable(BaseSerializer::class)
sealed interface ApiError {
    @Serializable
    data class Standard(
        val code: Int,
        val label: String,
        val message: String
    ) : ApiError
    // You can add new error types here, e.g.:
    // @Serializable
    // data class Backend(
    //    @SerialName("unreachable_backends")
    //    val unreachableBackends: List<String>
    // ) : ApiError

    fun getExceptionMessage(): String =
        when (this) {
            is Standard -> this.message
            // Handle new types here
            // is Backend -> "Some domains are unreachable. " +
            //     unreachableBackends.joinToString(", ")
        }
}
