package com.wire.integrations.jvm.model

import com.wire.crypto.PreKey
import kotlinx.serialization.Serializable
import java.util.Base64

@Serializable
data class ProteusPreKey(val id: Int, val key: String)

fun PreKey.toProteusPreKey() =
    ProteusPreKey(id = this.id.toInt(), key = Base64.getEncoder().encodeToString(this.data))
