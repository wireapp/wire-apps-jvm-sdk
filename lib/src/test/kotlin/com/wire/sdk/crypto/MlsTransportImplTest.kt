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

package com.wire.sdk.crypto

import com.wire.crypto.CommitBundle
import com.wire.crypto.GroupInfoBundle
import com.wire.crypto.MlsGroupInfoEncryptionType
import com.wire.crypto.MlsRatchetTreeType
import com.wire.sdk.client.MlsApiClient
import com.wire.sdk.exception.WireException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertSame
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MlsTransportImplTest {
    @Test
    fun `sendCommitBundle uploads commit and group info payload`() =
        runTest {
            val mlsApiClient = mockk<MlsApiClient> {
                coEvery { uploadCommitBundle(any()) } returns Unit
            }
            val transport = MlsTransportImpl(mlsApiClient)

            transport.sendCommitBundle(createCommitBundle())

            coVerify(exactly = 1) {
                mlsApiClient.uploadCommitBundle(
                    withArg {
                        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), it)
                    }
                )
            }
        }

    @Test
    fun `sendCommitBundle propagates upload failures`() =
        runTest {
            val expectedException = WireException.UnknownError("Could not upload commit bundle")
            val mlsApiClient = mockk<MlsApiClient> {
                coEvery { uploadCommitBundle(any()) } throws expectedException
            }
            val transport = MlsTransportImpl(mlsApiClient)

            val actualException = assertFailsWith<WireException.UnknownError> {
                transport.sendCommitBundle(createCommitBundle())
            }

            assertSame(expectedException, actualException)
        }

    private fun createCommitBundle(): CommitBundle =
        CommitBundle(
            welcome = null,
            commit = byteArrayOf(0x01, 0x02),
            groupInfo = GroupInfoBundle(
                encryptionType = MlsGroupInfoEncryptionType.PLAINTEXT,
                ratchetTreeType = MlsRatchetTreeType.FULL,
                payload = byteArrayOf(0x03, 0x04)
            ),
            encryptedMessage = byteArrayOf()
        )
}
