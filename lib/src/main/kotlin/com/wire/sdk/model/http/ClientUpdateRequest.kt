package com.wire.sdk.model.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class ClientUpdateRequest(
    @SerialName("mls_public_keys")
    val mlsPublicKeys: MlsPublicKeys
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class MlsPublicKeys(
    @SerialName("ecdsa_secp256r1_sha256")
    val ecdsaSecp256r1Sha256: String? = null,
    @SerialName("ecdsa_secp384r1_sha384")
    val ecdsaSecp384r1Sha384: String? = null,
    @SerialName("ecdsa_secp521r1_sha512")
    @JsonNames("ecdsa_secp521r1_sha521")
    val ecdsaSecp521r1Sha512: String? = null,
    @SerialName("ed25519")
    val ed25519: String? = null
)
