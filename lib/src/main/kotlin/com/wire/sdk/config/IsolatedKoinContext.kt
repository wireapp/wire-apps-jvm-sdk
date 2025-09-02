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

package com.wire.sdk.config

import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.fileProperties
import java.util.UUID

internal object IsolatedKoinContext {
    private var _koinApp: KoinApplication? = null
    val koinApp: KoinApplication
        get() = _koinApp ?: error("Koin not started")

    val koin: Koin
        get() = koinApp.koin

    fun start() {
        // Ensure any old Koin instance is closed
        _koinApp?.close()

        // Start a new Koin instance
        _koinApp = koinApplication {
            modules(sdkModule)
            fileProperties("/koin-sdk.properties")
        }
    }

    fun stop() {
        _koinApp?.close()
        _koinApp = null
    }

    fun setApplicationId(value: UUID) {
        this.koinApp.koin.setProperty(APPLICATION_ID, value)
    }

    fun getApplicationId(): UUID? = this.koinApp.koin.getProperty(APPLICATION_ID)

    fun setApiHost(value: String) {
        this.koinApp.koin.setProperty(API_HOST, value)
    }

    fun getApiHost(): String? = this.koinApp.koin.getProperty(API_HOST)

    fun setApiToken(value: String) {
        this.koinApp.koin.setProperty(API_TOKEN, value)
    }

    fun getApiToken(): String? = this.koinApp.koin.getProperty(API_TOKEN)

    fun setCryptographyStoragePassword(value: String) {
        val cryptoPassword = value.toByteArray()
        this.koinApp.koin.setProperty(CRYPTOGRAPHY_STORAGE_PASSWORD, cryptoPassword)
    }

    fun getCryptographyStoragePassword(): ByteArray? =
        this.koinApp.koin.getProperty(CRYPTOGRAPHY_STORAGE_PASSWORD)

    /**
     * Property Constants
     */
    private const val APPLICATION_ID = "APPLICATION_ID"
    private const val API_HOST = "API_HOST"
    private const val API_TOKEN = "API_TOKEN"
    private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "CRYPTOGRAPHY_STORAGE_PASSWORD"
}
