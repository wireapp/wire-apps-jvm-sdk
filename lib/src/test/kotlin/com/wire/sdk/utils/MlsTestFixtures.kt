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

package com.wire.sdk.utils

import com.wire.crypto.GroupInfo
import com.wire.crypto.toGroupInfo
import com.wire.sdk.crypto.MlsCryptoClient
import com.wire.sdk.model.CryptoClientId
import com.wire.sdk.model.QualifiedId
import java.util.Base64
import java.util.UUID

internal object MlsTestFixtures {
    private const val GROUP_INFO_RESOURCE = "/groupInfo.bin"
    private const val EXTERNAL_SENDER_KEY_BASE64 = "3AEFMpXsnJ28RcyA7CIRuaDL7L0vGmKaGjD206SANZw="

    fun groupInfoBytes(): ByteArray =
        requireNotNull(javaClass.getResourceAsStream(GROUP_INFO_RESOURCE)) {
            "Test resource $GROUP_INFO_RESOURCE not found"
        }.use { it.readBytes() }

    fun groupInfo(): GroupInfo = groupInfoBytes().toGroupInfo()

    suspend fun generateWelcomeMessage(): String {
        val transport = MlsTransportLastWelcome()
        val bobClient = MlsCryptoClient.create(
            appId = UUID.randomUUID(),
            ciphersuiteCode = 1
        )
        val aliceClient = MlsCryptoClient.create(
            appId = UUID.randomUUID(),
            ciphersuiteCode = 1
        )

        try {
            bobClient.initializeMlsClient(
                cryptoClientId = CryptoClientId.create(
                    applicationQualifiedId = QualifiedId(UUID.randomUUID(), "wire.com"),
                    deviceId = "0001"
                ),
                mlsTransport = transport
            )
            aliceClient.initializeMlsClient(
                cryptoClientId = CryptoClientId.create(
                    applicationQualifiedId = QualifiedId(UUID.randomUUID(), "wire.com"),
                    deviceId = "0002"
                ),
                mlsTransport = transport
            )
            bobClient.createConversation(
                mlsGroupId = MockCoreCryptoClient.MLS_GROUP_ID,
                externalSenders = Base64.getDecoder().decode(EXTERNAL_SENDER_KEY_BASE64)
            )
            bobClient.addClientsToMlsConversation(
                mlsGroupId = MockCoreCryptoClient.MLS_GROUP_ID,
                keyPackages = aliceClient.mlsGenerateKeyPackages(1u)
            )

            return Base64.getEncoder().encodeToString(transport.getLastWelcome().serialize())
        } finally {
            bobClient.close()
            aliceClient.close()
        }
    }
}
