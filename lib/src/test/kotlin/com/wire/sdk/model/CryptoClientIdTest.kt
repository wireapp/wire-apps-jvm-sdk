package com.wire.sdk.model

import com.wire.sdk.utils.KtxSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.UUID
import kotlin.test.Test

class CryptoClientIdTest {
    @Test
    fun `test serialization and deserialization of ClientId`() {
        val cryptoClientId = CryptoClientId(
            userId = QualifiedId(UUID.randomUUID(), "wire.example.com"),
            deviceId = "0001"
        )
        val json = KtxSerializer.json.encodeToString(cryptoClientId)
        val deserializedCryptoClientId = KtxSerializer.json.decodeFromString<CryptoClientId>(json)

        assertEquals(cryptoClientId, deserializedCryptoClientId)
    }

    @Test
    fun `constructor preserves qualified user id and device id`() {
        val userId = QualifiedId(UUID.randomUUID(), "wire.example.com")
        val deviceId = "device-123"

        val cryptoClientId = CryptoClientId(
            userId = userId,
            deviceId = deviceId
        )

        assertEquals(userId, cryptoClientId.userId)
        assertEquals(deviceId, cryptoClientId.deviceId)
    }

    @Test
    fun `toString obfuscates user id and device id`() {
        val cryptoClientId = CryptoClientId(
            userId = QualifiedId(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "wire.example.com"
            ),
            deviceId = "abcdef123456"
        )

        val obfuscatedValue = cryptoClientId.toString()

        assertEquals("1111111***:abc***@wire.example.com", obfuscatedValue)
        assertFalse(obfuscatedValue.contains(cryptoClientId.userId.id.toString()))
        assertFalse(obfuscatedValue.contains(cryptoClientId.deviceId))
        assertTrue(obfuscatedValue.contains(cryptoClientId.userId.domain))
    }
}
