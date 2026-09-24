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

import com.wire.crypto.KeyPackage
import com.wire.sdk.client.BackendClient
import com.wire.sdk.client.MlsApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.CryptoProtocol
import com.wire.sdk.model.MlsStatus
import com.wire.sdk.model.http.FeaturesResponse
import com.wire.sdk.model.http.MlsFeatureConfigResponse
import com.wire.sdk.model.http.MlsFeatureResponse
import com.wire.sdk.model.http.MlsKeyPackageCountResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KeyPackageManagerTest {
    @Test
    fun `when backend key package count is below threshold, then packages are replenished`() =
        runTest {
            val arrangement = Arrangement(this).withKeyPackageCount(49)

            arrangement.manager.start()
            runCurrent()

            coVerify(exactly = 1) {
                arrangement.cryptoClient.mlsGenerateKeyPackages(
                    CryptoClient.DEFAULT_KEYPACKAGE_COUNT
                )
                arrangement.mlsApiClient.uploadMlsKeyPackages(any())
            }
            arrangement.manager.close()
        }

    @Test
    fun `when backend key package count is at threshold, then packages are not replenished`() =
        runTest {
            val arrangement = Arrangement(this).withKeyPackageCount(50)

            arrangement.manager.start()
            runCurrent()

            coVerify(exactly = 0) {
                arrangement.cryptoClient.mlsGenerateKeyPackages(any())
                arrangement.mlsApiClient.uploadMlsKeyPackages(any())
            }
            arrangement.manager.close()
        }

    @Test
    fun `when manager starts, then it checks immediately and again after the interval`() =
        runTest {
            val arrangement = Arrangement(this).withKeyPackageCount(100)

            arrangement.manager.start()
            runCurrent()
            advanceTimeBy(CHECK_INTERVAL)
            runCurrent()

            coVerify(exactly = 2) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
            arrangement.manager.close()
        }

    @Test
    fun `when manager is started twice, then only one schedule is created`() =
        runTest {
            val arrangement = Arrangement(this).withKeyPackageCount(100)

            arrangement.manager.start()
            arrangement.manager.start()
            runCurrent()

            coVerify(exactly = 1) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
            arrangement.manager.close()
        }

    @Test
    fun `when a check fails, then the next scheduled check still runs`() =
        runTest {
            val arrangement = Arrangement(this)
            var invocation = 0
            coEvery {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            } coAnswers {
                invocation++
                if (invocation == 1) throw IllegalStateException("Backend unavailable")
                MlsKeyPackageCountResponse(100)
            }

            arrangement.manager.start()
            runCurrent()
            advanceTimeBy(CHECK_INTERVAL)
            runCurrent()

            coVerify(exactly = 2) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
            arrangement.manager.close()
        }

    @Test
    fun `when manager stops, then future checks are cancelled`() =
        runTest {
            val arrangement = Arrangement(this).withKeyPackageCount(100)

            arrangement.manager.start()
            runCurrent()
            arrangement.manager.stop()
            advanceTimeBy(CHECK_INTERVAL)
            runCurrent()

            coVerify(exactly = 1) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
            arrangement.manager.close()
        }

    private class Arrangement(testScope: kotlinx.coroutines.test.TestScope) {
        val backendClient = mockk<BackendClient> {
            coEvery { getApplicationFeatures() } returns FEATURES_RESPONSE
        }
        val mlsApiClient = mockk<MlsApiClient>(relaxed = true)
        val cryptoClient = mockk<CryptoClient>()
        private val keyPackage = mockk<KeyPackage> {
            every { serialize() } returns byteArrayOf(0x01)
        }
        val manager = KeyPackageManager(
            backendClient = backendClient,
            mlsApiClient = mlsApiClient,
            cryptoClient = cryptoClient,
            dispatcher = StandardTestDispatcher(testScope.testScheduler),
            checkInterval = CHECK_INTERVAL
        )

        init {
            coEvery {
                cryptoClient.mlsGenerateKeyPackages(CryptoClient.DEFAULT_KEYPACKAGE_COUNT)
            } returns listOf(keyPackage)
        }

        fun withKeyPackageCount(count: Int) =
            apply {
                coEvery {
                    mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
                } returns MlsKeyPackageCountResponse(count)
            }
    }

    private companion object {
        val CHECK_INTERVAL = 24.hours
        const val CIPHER_SUITE = "0x0001"

        val FEATURES_RESPONSE = FeaturesResponse(
            mlsFeatureResponse = MlsFeatureResponse(
                mlsFeatureConfigResponse = MlsFeatureConfigResponse(
                    allowedCipherSuites = listOf(1),
                    defaultCipherSuite = 1,
                    defaultProtocol = CryptoProtocol.MLS,
                    supportedProtocols = listOf(CryptoProtocol.MLS)
                ),
                status = MlsStatus.ENABLED
            )
        )
    }
}
