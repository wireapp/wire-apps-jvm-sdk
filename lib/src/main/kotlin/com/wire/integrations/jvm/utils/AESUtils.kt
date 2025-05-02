/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.integrations.jvm.utils

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AESEncrypt {
    fun encryptData(
        assetData: ByteArray,
        key: ByteArray
    ): ByteArray {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(key, 0, key.size, KEY_ALGORITHM)

        // Create random iv
        val iv = ByteArray(IV_SIZE)
        SecureRandom().nextBytes(iv)

        // Do the encryption
        cipher.init(Cipher.ENCRYPT_MODE, symmetricAESKey, IvParameterSpec(iv))
        val cipherData = cipher.doFinal(assetData)

        // We prefix the first 16 bytes of the final encoded array with the Initialization Vector
        return cipher.iv + cipherData
    }

    fun generateRandomAES256Key(): ByteArray {
        // AES256 Symmetric secret key generation
        val keygen = KeyGenerator.getInstance(KEY_ALGORITHM)
        keygen.init(AES_KEYGEN_SIZE)
        return keygen.generateKey().encoded
    }

    fun calculateSha256Hash(data: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data)
    }
}

object AESDecrypt {
    fun decryptData(
        encryptedData: ByteArray,
        key: ByteArray
    ): ByteArray {
        // Fetch AES256 Algorithm
        val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)

        // Parse Secret Key from our custom AES256Key model object
        val symmetricAESKey = SecretKeySpec(key, 0, key.size, KEY_ALGORITHM)

        // Get first 16 as they belong to the IV
        val iv = encryptedData.copyOfRange(0, IV_SIZE)

        // Do the decryption
        cipher.init(Cipher.DECRYPT_MODE, symmetricAESKey, IvParameterSpec(iv))
        val decryptedData = cipher.doFinal(encryptedData)

        return decryptedData.copyOfRange(IV_SIZE, decryptedData.size)
    }
}

private const val KEY_ALGORITHM = "AES"
private const val KEY_ALGORITHM_CONFIGURATION = "AES/CBC/PKCS5PADDING"
private const val IV_SIZE = 16
private const val AES_KEYGEN_SIZE = 256
const val MAX_DATA_SIZE = 100 * 1024 * 1024
