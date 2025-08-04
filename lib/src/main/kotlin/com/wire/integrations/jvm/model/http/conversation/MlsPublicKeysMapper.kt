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

object MlsPublicKeysMapper {
    fun fromCipherSuite(cipherSuite: Ciphersuite): MlsPublicKeyType {
        return when (cipherSuite) {
            Ciphersuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256 ->
                MlsPublicKeyType.ECDSA_SECP256R1_SHA256
            Ciphersuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519 ->
                MlsPublicKeyType.ED25519
            Ciphersuite.MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_Ed25519 ->
                MlsPublicKeyType.ED25519
            Ciphersuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384 ->
                MlsPublicKeyType.ECDSA_SECP384R1_SHA384
            Ciphersuite.MLS_256_DHKEMP521_AES256GCM_SHA512_P521 ->
                MlsPublicKeyType.ECDSA_SECP521R1_SHA512
            Ciphersuite.MLS_256_DHKEMX448_AES256GCM_SHA512_Ed448 ->
                MlsPublicKeyType.ED448
            Ciphersuite.MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_Ed448 ->
                MlsPublicKeyType.ED448
        }
    }
}
