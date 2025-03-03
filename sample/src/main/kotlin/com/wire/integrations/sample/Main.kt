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

package com.wire.integrations.sample

import com.wire.integrations.jvm.WireAppSdk
import com.wire.integrations.jvm.WireEventsHandler
import java.util.UUID

fun main() {
    val wireAppSdk = WireAppSdk(
        applicationId = UUID.randomUUID(),
        apiToken = "myApiToken",
        apiHost = "staging-nginz-https.zinfra.io",
        cryptographyStoragePassword = "myDummyPassword",
        object : WireEventsHandler() {
            override fun onEvent(event: String) {
                println("Custom events handler: $event")
            }
        })
    println("Starting Wire Apps SDK...")
    wireAppSdk.start() // Will keep a thread running in the background until explicitly stopped
    val credentialsManager = wireAppSdk.getTeamManager()

    credentialsManager.getStoredTeams().forEach {
        println("Team: ${it.id}")
    }
    println("Wire backend domain: ${credentialsManager.getApplicationMetadata().domain}")

    // Use wireAppSdk.stop() to stop the SDK or just stop it with Ctrl+C/Cmd+C
}
