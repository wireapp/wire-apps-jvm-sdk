package com.wire.sdk.model.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
