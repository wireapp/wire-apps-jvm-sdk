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

package com.wire.sdk.config

import com.wire.sdk.client.MlsApiClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class KeyPackageInitializationTest {
    @Test
    fun `when initial key package upload fails, then initialization can continue`() =
        runTest {
            val mlsApiClient = mockk<MlsApiClient> {
                coEvery { uploadMlsKeyPackages(KEY_PACKAGES) } throws
                    IllegalStateException("Backend unavailable")
            }

            uploadInitialMlsKeyPackages(mlsApiClient, KEY_PACKAGES)

            coVerify(exactly = 1) { mlsApiClient.uploadMlsKeyPackages(KEY_PACKAGES) }
        }

    @Test
    fun `when initial key package upload is cancelled, then cancellation propagates`() =
        runTest {
            val mlsApiClient = mockk<MlsApiClient> {
                coEvery { uploadMlsKeyPackages(KEY_PACKAGES) } throws CancellationException()
            }

            assertFailsWith<CancellationException> {
                uploadInitialMlsKeyPackages(mlsApiClient, KEY_PACKAGES)
            }
        }

    private companion object {
        val KEY_PACKAGES = listOf(byteArrayOf(0x01))
    }
}
