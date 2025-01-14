package com.wire.integrations.jvm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WireBotSdkTest {
    @Test
    fun someLibraryMethodReturnsTrue() {
        val wireBotSdk =
            WireBotSdk(
                object : WireBotListener {
                    override fun onEvent(event: String) {
                        println(event)
                    }
                }
            )
        assertTrue(wireBotSdk.someLibraryMethod(), "someLibraryMethod should return 'true'")
    }
}
