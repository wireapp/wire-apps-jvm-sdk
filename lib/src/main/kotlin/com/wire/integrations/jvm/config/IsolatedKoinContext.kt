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

package com.wire.integrations.jvm.config

import org.koin.dsl.koinApplication
import org.koin.fileProperties
import java.util.UUID

internal object IsolatedKoinContext {
    val koinApp =
        koinApplication {
            modules(sdkModule)
            fileProperties("/koin.properties")
        }

    fun setApplicationId(value: UUID) {
        this.koinApp.koin.setProperty(APPLICATION_ID, value)
    }

    fun getApplicationId() = this.koinApp.koin.getProperty<UUID>(APPLICATION_ID)

    fun setApiHost(value: String) {
        this.koinApp.koin.setProperty(API_HOST, value)
    }

    fun getApiHost() = this.koinApp.koin.getProperty<String>(API_HOST)

    fun getWebSocketHost() =
        getApiHost()
            ?.replace("https://", "wss://")
            ?.replace("http://", "ws://")
            ?.replace("-https", "-ssl")

    fun setCryptographyStoragePassword(value: String) {
        this.koinApp.koin.setProperty(CRYPTOGRAPHY_STORAGE_PASSWORD, value)
    }

    fun getCryptographyStoragePassword() =
        this.koinApp.koin.getProperty<String>(CRYPTOGRAPHY_STORAGE_PASSWORD)

    /**
     * Property Constants
     */
    private const val APPLICATION_ID = "APPLICATION_ID"
    private const val API_HOST = "API_HOST"
    private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "CRYPTOGRAPHY_STORAGE_PASSWORD"
}
