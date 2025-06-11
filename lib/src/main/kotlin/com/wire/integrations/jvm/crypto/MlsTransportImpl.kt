/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.integrations.jvm.crypto

import com.wire.crypto.CommitBundle
import com.wire.crypto.MlsTransport
import com.wire.crypto.MlsTransportResponse
import com.wire.integrations.jvm.client.BackendClient

internal class MlsTransportImpl(
    private val backendClient: BackendClient
) : MlsTransport {
    override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse {
        backendClient.uploadCommitBundle(
            commitBundle = parseBundleIntoSingleByteArray(
                bundle = commitBundle
            )
        )

        return MlsTransportResponse.Success
    }

    override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse {
        backendClient.sendMessage(mlsMessage)
        return MlsTransportResponse.Success
    }

    /**
     * Return the CommitBundle data as a single byte array, in a specific order.
     * The order is: commit, groupInfo, welcome (optional).
     * The created bundle will be pushed in this format to the backend when joining a conversation.
     *
     * @param bundle the CommitBundle to parse
     */
    private fun parseBundleIntoSingleByteArray(bundle: CommitBundle): ByteArray {
        return bundle.commit.value +
            bundle.groupInfoBundle.payload.value +
            (bundle.welcome?.value ?: ByteArray(0))
    }
}
