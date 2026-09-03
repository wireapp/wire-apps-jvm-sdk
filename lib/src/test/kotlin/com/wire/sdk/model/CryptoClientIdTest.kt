package com.wire.sdk.model

import com.wire.sdk.utils.KtxSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import java.util.UUID
import kotlin.test.Test

class CryptoClientIdTest {
    @Test
    fun `test serialization and deserialization of ClientId`() {
        val cryptoClientId = CryptoClientId("test-client-id")
        val json = KtxSerializer.json.encodeToString(cryptoClientId)
        val deserializedCryptoClientId = KtxSerializer.json.decodeFromString<CryptoClientId>(json)

        assertEquals(cryptoClientId, deserializedCryptoClientId)
    }

    @Test
    fun `create builds client id from qualified id and device id`() {
        val appId = UUID.randomUUID()
        val qualifiedId = QualifiedId(appId, "wire.example.com")
        val deviceId = "device-123"

        val cryptoClientId = CryptoClientId.create(
            applicationQualifiedId = qualifiedId,
            deviceId = deviceId
        )

        assertEquals("$appId:$deviceId@wire.example.com", cryptoClientId.value)
    }
}
