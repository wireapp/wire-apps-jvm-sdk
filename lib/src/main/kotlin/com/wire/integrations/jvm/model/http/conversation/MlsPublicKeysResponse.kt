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

package com.wire.integrations.jvm.model.http.conversation

import com.wire.crypto.Ciphersuite
import io.ktor.util.decodeBase64Bytes
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MlsPublicKeysResponse(
    @SerialName("removal")
    val removal: Map<String, String>?
)

fun MlsPublicKeysResponse.getRemovalKey(cipherSuite: Ciphersuite): ByteArray? {
    val keySignature = MlsPublicKeysMapper.fromCipherSuite(cipherSuite)
    val key = this.removal?.let { removalKeys ->
        removalKeys[keySignature.value]
    } ?: return null
    return key.decodeBase64Bytes()
}

@Suppress("ClassName")
sealed interface MlsPublicKeyType {
    val value: String?

    data object ECDSA_SECP256R1_SHA256 : MlsPublicKeyType {
        override val value: String = "ecdsa_secp256r1_sha256"
    }

    data object ECDSA_SECP384R1_SHA384 : MlsPublicKeyType {
        override val value: String = "ecdsa_secp384r1_sha384"
    }

    data object ECDSA_SECP521R1_SHA512 : MlsPublicKeyType {
        override val value: String = "ecdsa_secp521r1_sha512"
    }

    data object ED448 : MlsPublicKeyType {
        override val value: String = "ed448"
    }

    data object ED25519 : MlsPublicKeyType {
        override val value: String = "ed25519"
    }

    data class Unknown(override val value: String?) : MlsPublicKeyType

    companion object {
        fun from(value: String) =
            when (value) {
                ECDSA_SECP256R1_SHA256.value -> ECDSA_SECP256R1_SHA256
                ECDSA_SECP384R1_SHA384.value -> ECDSA_SECP384R1_SHA384
                ECDSA_SECP521R1_SHA512.value -> ECDSA_SECP521R1_SHA512
                ED448.value -> ED448
                ED25519.value -> ED25519
                else -> Unknown(value)
            }
    }
}
