package com.wire.integrations.jvm.model.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class ApiVersionResponse(
    @SerialName("development")
    val development: List<Int>,
    @SerialName("domain")
    val domain: String,
    @SerialName("federation")
    val federation: Boolean,
    @SerialName("supported")
    val supported: List<Int>
)
