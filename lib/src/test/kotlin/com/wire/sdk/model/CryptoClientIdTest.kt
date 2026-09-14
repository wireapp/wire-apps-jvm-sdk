package com.wire.sdk.model

import com.wire.sdk.utils.KtxSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.UUID
import kotlin.test.Test

class CryptoClientIdTest {
    @Test
    fun `test serialization and deserialization of ClientId`() {
        val cryptoClientId = CryptoClientId.create(
            userId = UUID.randomUUID().toString(),
            deviceId = "0001",
            userDomain = "wire.example.com"
        )
        val json = KtxSerializer.json.encodeToString(cryptoClientId)
        val deserializedCryptoClientId = KtxSerializer.json.decodeFromString<CryptoClientId>(json)

        assertEquals(cryptoClientId.userId, deserializedCryptoClientId.userId)
        assertEquals(cryptoClientId.deviceId, deserializedCryptoClientId.deviceId)
        assertEquals(cryptoClientId.userDomain, deserializedCryptoClientId.userDomain)
    }

    @Test
    fun `create builds client id from user id, device id, and domain`() {
        val appId = UUID.randomUUID()
        val deviceId = "device-123"
        val userDomain = "wire.example.com"

        val cryptoClientId = CryptoClientId.create(
            userId = appId.toString(),
            deviceId = deviceId,
            userDomain = userDomain
        )

        assertEquals(appId.toString(), cryptoClientId.userId)
        assertEquals(deviceId, cryptoClientId.deviceId)
        assertEquals(userDomain, cryptoClientId.userDomain)
    }
}
