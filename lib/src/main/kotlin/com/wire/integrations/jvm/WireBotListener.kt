package com.wire.integrations.jvm

/**
 * Interface expose by the SDK, clients can implement this interface,
 * and pass it during SDK initialization to handle Wire events.
 */
interface WireBotListener {
    fun onEvent(event: String)
}
