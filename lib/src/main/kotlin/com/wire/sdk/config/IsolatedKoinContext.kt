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

import com.wire.sdk.model.QualifiedId
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.fileProperties
import java.util.UUID

@Suppress("TooManyFunctions")
internal object IsolatedKoinContext {
    private var _koinApp: KoinApplication? = null
    private var _applicationUser: QualifiedId? = null

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

        clearCachedApplicationUser()
    }

    fun stop() {
        _koinApp?.close()
        _koinApp = null
        clearCachedApplicationUser()
    }

    fun setApplicationId(value: UUID) {
        this.koinApp.koin.setProperty(APPLICATION_ID, value)
        clearCachedApplicationUser()
    }

    fun getApplicationUser(): QualifiedId {
        val id = checkNotNull(this.koinApp.koin.getProperty<UUID>(APPLICATION_ID)) {
            "App ID is not set in Koin properties"
        }

        val domain = checkNotNull(koinApp.koin.getProperty<String>(BACKEND_DOMAIN)) {
            "Wire Backend domain is not set in Koin properties"
        }

        return _applicationUser ?: synchronized(this) {
            _applicationUser ?: QualifiedId(
                id = id,
                domain = domain
            ).also { _applicationUser = it }
        }
    }

    fun getApplicationId(): UUID =
        checkNotNull(this.koinApp.koin.getProperty(APPLICATION_ID)) {
            "App ID is not set in Koin properties"
        }

    fun setApiHost(value: String) {
        this.koinApp.koin.setProperty(API_HOST, value)
    }

    fun getApiHost(): String =
        checkNotNull(this.koinApp.koin.getProperty(API_HOST)) {
            "Api Host is not set in Koin properties"
        }

    fun setCryptographyStorageKey(value: ByteArray) {
        this.koinApp.koin.setProperty(CRYPTOGRAPHY_STORAGE_KEY, value)
    }

    fun getCryptographyStorageKey(): ByteArray =
        checkNotNull(this.koinApp.koin.getProperty(CRYPTOGRAPHY_STORAGE_KEY)) {
            "Cryptography Storage Key is not set in Koin properties"
        }

    fun setBackendDomain(value: String) {
        this.koinApp.koin.setProperty(BACKEND_DOMAIN, value)
        clearCachedApplicationUser()
    }

    fun getBackendDomain(): String =
        checkNotNull(koinApp.koin.getProperty(BACKEND_DOMAIN)) {
            "Wire Backend domain is not set in Koin properties"
        }

    private fun clearCachedApplicationUser() {
        synchronized(this) {
            _applicationUser = null
        }
    }

    /**
     * Property Constants
     */
    private const val APPLICATION_ID = "APPLICATION_ID"
    private const val API_HOST = "API_HOST"
    private const val CRYPTOGRAPHY_STORAGE_KEY = "CRYPTOGRAPHY_STORAGE_KEY"
    private const val BACKEND_DOMAIN = "BACKEND_DOMAIN"
}
