package com.wire.integrations.jvm.model.http

import com.wire.integrations.jvm.model.ProteusPreKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientAddRequest(
    val type: String = "permanent",
//    val capabilities: List<String> = listOf("consumable-notifications"),
    // Only required clients other thant the first one
    val password: String?,
    val lastkey: ProteusPreKey,
    val prekeys: List<ProteusPreKey>
)

@Serializable
data class ClientUpdateRequest(
    @SerialName("mls_public_keys")
    val mlsPublicKeys: MlsPublicKeys
)

@Serializable
data class MlsPublicKeys(
    @SerialName("ecdsa_secp256r1_sha256")
    val ecdsaSecp256r1Sha256: String? = null,
    @SerialName("ecdsa_secp384r1_sha384")
    val ecdsaSecp384r1Sha384: String? = null,
    @SerialName("ecdsa_secp521r1_sha512")
    val ecdsaSecp521r1Sha512: String? = null,
    @SerialName("ed25519")
    val ed25519: String? = null
)
