package com.wire.integrations.jvm.model

import com.wire.integrations.jvm.utils.KtxSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.Test

class AppClientIdTest {
    @Test
    fun `test serialization and deserialization of ClientId`() {
        val appClientId = AppClientId("test-client-id")
        val json = KtxSerializer.json.encodeToString(appClientId)
        val deserializedAppClientId = KtxSerializer.json.decodeFromString<AppClientId>(json)

        assertEquals(appClientId, deserializedAppClientId)
    }
}
