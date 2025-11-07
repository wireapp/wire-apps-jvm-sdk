/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.sdk.sample

import com.wire.sdk.BackendConnectionListener
import com.wire.sdk.WireAppSdk
import com.wire.sdk.model.QualifiedId
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("WireAppSdkSample")

fun main() {
    // Create a connection listener to monitor backend connection status
    val connectionListener = object : BackendConnectionListener {
        override fun onConnected() {
            logger.info("Backend connection established - Ready to send/receive messages")
        }

        override fun onDisconnected() {
            logger.warn("Backend connection lost - Attempting to reconnect...")
            // Optionally implement custom reconnection logic, alerting, or fallback behavior here
        }
    }

    val wireAppSdk = WireAppSdk(
        applicationId = UUID.randomUUID(),
        apiToken = "myApiToken",
        apiHost = "https://staging-nginz-https.zinfra.io",
        cryptographyStoragePassword = "myDummyPasswordOfRandom32BytesCH",
        wireEventsHandler = SampleEventsHandler(),
    )

    wireAppSdk.setBackendConnectionListener(connectionListener)

    logger.info("Starting Wire Apps SDK...")
    wireAppSdk.startListening() // Will keep a coroutine running in the background until explicitly stopped
    val applicationManager = wireAppSdk.getApplicationManager()

    applicationManager.getStoredTeams().forEach {
        logger.info("Team: $it")
    }
    applicationManager.getStoredConversations().forEach {
        logger.info("Conversation: $it")
    }
    val selfUser = QualifiedId(
        id = UUID.fromString("2afce87f-3195-4c51-9e7c-3b01faf13ac5"),
        domain = "staging.zinfra.io"
    )
    logger.info(applicationManager.getUser(selfUser).toString())
    logger.info("Wire backend domain: ${applicationManager.getBackendConfiguration().domain}")

    // Use wireAppSdk.stop() to stop the SDK or just stop it with Ctrl+C/Cmd+C
}
