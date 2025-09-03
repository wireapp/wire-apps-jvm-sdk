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

package com.wire.sdk.utils

import com.wire.crypto.MlsTransport
import com.wire.crypto.Welcome
import com.wire.crypto.CommitBundle
import com.wire.crypto.HistorySecret
import com.wire.crypto.MlsTransportData
import com.wire.crypto.MlsTransportResponse

/**
 * A simple implementation of [MlsTransport] that stores the last welcome message,
 * only for testing purposes. In a real scenario, the welcome message would come from the backend.
 */
class MlsTransportLastWelcome : MlsTransport {
    private var groupWelcomeMap: Welcome? = null

    override suspend fun sendCommitBundle(commitBundle: CommitBundle): MlsTransportResponse {
        commitBundle.welcome?.let {
            groupWelcomeMap = Welcome(it.value)
        }

        return MlsTransportResponse.Success
    }

    override suspend fun sendMessage(mlsMessage: ByteArray): MlsTransportResponse =
        MlsTransportResponse.Success

    fun getLastWelcome(): Welcome =
        groupWelcomeMap ?: throw IllegalArgumentException("No welcome for group")

    override suspend fun prepareForTransport(historySecret: HistorySecret): MlsTransportData {
        TODO("Not yet implemented")
    }
}
