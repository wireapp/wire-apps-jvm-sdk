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

import com.wire.sdk.WireAppSdk
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("WireAppSdkSample")

fun main() {
    val wireAppSdk = WireAppSdk(
        applicationId = UUID.fromString(System.getenv("WIRE_SDK_APPLICATION_ID")),
        applicationDomain = System.getenv("WIRE_SDK_APPLICATION_DOMAIN"),
        apiToken = System.getenv("WIRE_SDK_API_TOKEN"),
        apiHost = System.getenv("WIRE_SDK_API_HOST"),
        cryptographyStoragePassword = System.getenv("WIRE_SDK_CRYPTO_STORAGE_PASSWORD"),
        wireEventsHandler = SampleEventsHandler()
    )

    logger.info("Starting Wire Apps SDK...")
    wireAppSdk.startListening() // Will keep a thread running in the background until explicitly stopped
    val applicationManager = wireAppSdk.getApplicationManager()

    applicationManager.getStoredTeams().forEach {
        logger.info("Team: $it")
    }
    applicationManager.getStoredConversations().forEach {
        logger.info("Conversation: $it")
    }
    logger.info("Wire backend domain: ${applicationManager.getBackendConfiguration().domain}")

    // Use wireAppSdk.stop() to stop the SDK or just stop it with Ctrl+C/Cmd+C
}
