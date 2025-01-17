package com.wire.integrations.jvm.model.http

import kotlinx.serialization.Serializable

@Serializable
data class ApiVersionResponse(
    val development: List<Int>,
    val domain: String,
    val federation: Boolean,
    val supported: List<Int>
)
