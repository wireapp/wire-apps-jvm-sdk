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
        val cryptoClientId = CryptoClientId.create(
            applicationQualifiedId = QualifiedId(UUID.randomUUID(), "wire.example.com"),
            deviceId = "0001"
        )
        val json = KtxSerializer.json.encodeToString(cryptoClientId)
        val deserializedCryptoClientId = KtxSerializer.json.decodeFromString<CryptoClientId>(json)

        assertEquals(cryptoClientId, deserializedCryptoClientId)
    }

    @Test
    fun `create builds client id from user id, device id, and domain`() {
        val appId = UUID.randomUUID()
        val deviceId = "device-123"
        val userDomain = "wire.example.com"

        val cryptoClientId = CryptoClientId.create(
            applicationQualifiedId = QualifiedId(appId, userDomain),
            deviceId = deviceId
        )

        assertEquals(appId.toString(), cryptoClientId.userId)
        assertEquals(deviceId, cryptoClientId.deviceId)
        assertEquals(userDomain, cryptoClientId.userDomain)
    }

    @Test
    fun `toString obfuscates user id and device id`() {
        val cryptoClientId = CryptoClientId.create(
            applicationQualifiedId = QualifiedId(
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "wire.example.com"
            ),
            deviceId = "abcdef123456"
        )

        val obfuscatedValue = cryptoClientId.toString()

        assertEquals("1111111***:abc***@wire.example.com", obfuscatedValue)
        assertFalse(obfuscatedValue.contains(cryptoClientId.userId))
        assertFalse(obfuscatedValue.contains(cryptoClientId.deviceId))
        assertTrue(obfuscatedValue.contains(cryptoClientId.userDomain))
    }
}
