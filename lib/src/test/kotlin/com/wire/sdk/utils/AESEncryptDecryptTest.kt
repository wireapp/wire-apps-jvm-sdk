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

package com.wire.sdk.utils

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.io.File

class AESEncryptDecryptTest {
    @Test
    fun `should correctly encrypt and decrypt file data`() {
        // Arrange
        val resourcePath = javaClass.classLoader.getResource("banana-icon.png")?.path
            ?: throw IllegalStateException("Test resource 'banana-icon.png' not found")
        val originalData = File(resourcePath).readBytes()

        // Act - Generate key and encrypt
        val aesKey = AESEncrypt.generateRandomAES256Key()
        val encryptedData = AESEncrypt.encryptData(originalData, aesKey)

        // Calculate SHA-256 hash of encrypted data
        AESEncrypt.calculateSha256Hash(encryptedData)

        // Decrypt the data
        val decryptedData = AESDecrypt.decryptData(encryptedData, aesKey)

        // Assert
        assertArrayEquals(
            originalData,
            decryptedData,
            "Decrypted data should match the original data"
        )
    }

    @Test
    fun `should generate different AES keys each time`() {
        // Act
        val key1 = AESEncrypt.generateRandomAES256Key()
        val key2 = AESEncrypt.generateRandomAES256Key()

        // Assert
        assert(key1.size == 32) { "AES-256 key should be 32 bytes (256 bits)" }
        assert(key2.size == 32) { "AES-256 key should be 32 bytes (256 bits)" }
        assert(!key1.contentEquals(key2)) { "Generated keys should be different" }
    }

    @Test
    fun `should verify hash calculation works correctly`() {
        // Arrange
        val testData = "Test data for hashing".toByteArray()

        // Act
        val hash1 = AESEncrypt.calculateSha256Hash(testData)
        val hash2 = AESEncrypt.calculateSha256Hash(testData)

        // Assert
        assertArrayEquals(hash1, hash2, "Same input should produce the same hash")
        assert(hash1.size == 32) { "SHA-256 hash should be 32 bytes (256 bits)" }
    }
}
