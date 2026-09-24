/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
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

package com.wire.sdk.service

import com.wire.sdk.client.MlsApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.crypto.MlsCryptoClient.Companion.toHexString
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * Periodically checks the backend key-package inventory and replenishes it when needed.
 */
internal class KeyPackageReplenisher(
    private val mlsApiClient: MlsApiClient,
    private val cryptoClient: CryptoClient,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val checkInterval: Duration = KEY_PACKAGE_COUNT_CHECK_INTERVAL
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var replenishmentJob: Job? = null

    @Synchronized
    fun start(): Job {
        replenishmentJob?.takeIf { it.isActive }?.let { return it }

        return scope.launch {
            while (isActive) {
                replenishKeyPackagesIfNeeded()
                delay(checkInterval)
            }
        }.also { replenishmentJob = it }
    }

    @Synchronized
    fun stop(job: Job? = null) {
        if (job != null && job !== replenishmentJob) return

        replenishmentJob?.cancel()
        replenishmentJob = null
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun replenishKeyPackagesIfNeeded() {
        try {
            val cipherSuite = cryptoClient.cipherSuite.value
                .toInt()
                .toHexString()
            val keyPackageCount = mlsApiClient.getAvailableKeyPackageCount(cipherSuite).count
            val refillThreshold = (CryptoClient.DEFAULT_KEYPACKAGE_COUNT / 2u).toInt()

            if (keyPackageCount < refillThreshold) {
                logger.info(
                    "Found {} available MLS key packages, replenishing with {} new packages",
                    keyPackageCount,
                    CryptoClient.DEFAULT_KEYPACKAGE_COUNT
                )
                mlsApiClient.uploadMlsKeyPackages(
                    cryptoClient.mlsGenerateKeyPackages().map { it.serialize() }
                )
            } else {
                logger.info(
                    "Found {} available MLS key packages, replenishment is not needed",
                    keyPackageCount
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error("Failed to check or replenish MLS key packages", exception)
        }
    }

    override fun close() {
        stop()
        scope.cancel()
    }

    private companion object {
        val KEY_PACKAGE_COUNT_CHECK_INTERVAL = 24.hours
    }
}
