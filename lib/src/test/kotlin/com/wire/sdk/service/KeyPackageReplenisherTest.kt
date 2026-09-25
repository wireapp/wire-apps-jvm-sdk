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

import com.wire.crypto.CipherSuite
import com.wire.crypto.KeyPackage
import com.wire.sdk.client.MlsApiClient
import com.wire.sdk.crypto.CryptoClient
import com.wire.sdk.model.http.MlsKeyPackageCountResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class KeyPackageReplenisherTest {
    @Test
    fun `when backend key package count is below threshold, then packages are replenished`() =
        replenisherTest { arrangement ->
            arrangement.withKeyPackageCount(REFILL_THRESHOLD - 1)

            arrangement.replenisher.start()
            runCurrent()

            coVerify(exactly = 1) {
                arrangement.cryptoClient.mlsGenerateKeyPackages(
                    CryptoClient.DEFAULT_KEYPACKAGE_COUNT
                )
                arrangement.mlsApiClient.uploadMlsKeyPackages(any())
            }
        }

    @Test
    fun `when backend key package count is at threshold, then packages are not replenished`() =
        replenisherTest { arrangement ->
            arrangement.withKeyPackageCount(REFILL_THRESHOLD)

            arrangement.replenisher.start()
            runCurrent()

            coVerify(exactly = 0) {
                arrangement.cryptoClient.mlsGenerateKeyPackages(any())
                arrangement.mlsApiClient.uploadMlsKeyPackages(any())
            }
        }

    @Test
    fun `when replenisher starts, then it checks immediately and again after the interval`() =
        replenisherTest { arrangement ->
            arrangement.withKeyPackageCount(DEFAULT_KEY_PACKAGE_COUNT)

            arrangement.replenisher.start()
            runCurrent()
            advanceTimeBy(CHECK_INTERVAL)
            runCurrent()

            coVerify(exactly = 2) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
        }

    @Test
    fun `when replenisher is started twice, then only one schedule is created`() =
        replenisherTest { arrangement ->
            arrangement.withKeyPackageCount(DEFAULT_KEY_PACKAGE_COUNT)

            arrangement.replenisher.start()
            arrangement.replenisher.start()
            runCurrent()

            coVerify(exactly = 1) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
        }

    @Test
    fun `when a check fails, then the next scheduled check still runs`() =
        replenisherTest { arrangement ->
            var invocation = 0
            coEvery {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            } coAnswers {
                invocation++
                if (invocation == 1) throw IllegalStateException("Backend unavailable")
                MlsKeyPackageCountResponse(DEFAULT_KEY_PACKAGE_COUNT)
            }

            arrangement.replenisher.start()
            runCurrent()
            advanceTimeBy(CHECK_INTERVAL)
            runCurrent()

            coVerify(exactly = 2) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
        }

    @Test
    fun `when replenisher stops, then future checks are cancelled`() =
        replenisherTest { arrangement ->
            arrangement.withKeyPackageCount(DEFAULT_KEY_PACKAGE_COUNT)

            arrangement.replenisher.start()
            runCurrent()
            arrangement.replenisher.stop()
            advanceTimeBy(CHECK_INTERVAL)
            runCurrent()

            coVerify(exactly = 1) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
        }

    @Test
    fun `when replenisher restarts, then checks resume`() =
        replenisherTest { arrangement ->
            arrangement.withKeyPackageCount(DEFAULT_KEY_PACKAGE_COUNT)

            arrangement.replenisher.start()
            runCurrent()
            arrangement.replenisher.stop()
            arrangement.replenisher.start()
            runCurrent()

            coVerify(exactly = 2) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
        }

    @Test
    fun `when replenisher is stopped twice and restarted, then only the new schedule runs`() =
        replenisherTest { arrangement ->
            arrangement.withKeyPackageCount(DEFAULT_KEY_PACKAGE_COUNT)

            arrangement.replenisher.start()
            runCurrent()
            arrangement.replenisher.stop()
            arrangement.replenisher.stop()
            arrangement.replenisher.start()
            runCurrent()

            advanceTimeBy(CHECK_INTERVAL)
            runCurrent()

            coVerify(exactly = 3) {
                arrangement.mlsApiClient.getAvailableKeyPackageCount(CIPHER_SUITE)
            }
        }

    private fun replenisherTest(block: suspend TestScope.(Arrangement) -> Unit) =
        runTest {
            val arrangement = Arrangement(this)
            try {
                block(arrangement)
            } finally {
                arrangement.replenisher.close()
            }
        }

    private class Arrangement(testScope: TestScope) {
        val mlsApiClient = mockk<MlsApiClient>(relaxed = true)
        val cryptoClient = mockk<CryptoClient> {
            every { cipherSuite } returns CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519
        }
        private val keyPackage = mockk<KeyPackage> {
            every { serialize() } returns byteArrayOf(0x01)
        }
        val replenisher = KeyPackageReplenisher(
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
        val REFILL_THRESHOLD = (CryptoClient.DEFAULT_KEYPACKAGE_COUNT / 2u).toInt()
        val DEFAULT_KEY_PACKAGE_COUNT = CryptoClient.DEFAULT_KEYPACKAGE_COUNT.toInt()
    }
}
