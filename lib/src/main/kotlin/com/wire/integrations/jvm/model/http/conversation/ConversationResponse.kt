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

package com.wire.integrations.jvm.model.http.conversation

import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.utils.UUIDSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/*
{
    "public_keys": {"removal": {
        "ecdsa_secp521r1_sha512": "BAGg1VUrOw3h/irMi5MqsGILNc09mbkRMUdFHjub/dVkdSamgVrJrhfGPMDTepTT0REM/1YMZL1n0IzZ3I9jN+u1dwAmMk+8YfEt0b1cCRJ4/09B+KJrf5eYup4ZMFZ/2wompqhE0DBWDb6f1Ty+Wv2eHDcXxhwj8NLq4xsUWr4HezptUg==",
        "ecdsa_secp256r1_sha256": "BD3NMPFNAtQ1vOghWZlRpLHiwM1o3K8i4JStvSOOEwbJUns3MbLkoIp7tFao0xiWr5yKGPX8841mDSGP1bADPak=",
        "ed25519": "jc4n7UMQiTrBG5txz8Twd4Ny4Qiz7qcQ8tGWRuerXt8=",
        "ecdsa_secp384r1_sha384": "BL1DDmTRBMOQTHvGSfM4P8AnCBvHgfgvNm2p2Y6Fvv2NmMyPWX9yiYweBXPSQM9pvwU054le9Tm3LZjgFFIE8zIaZHROygup0InXRNdJflNWTmlCrG6K/1oap65avCS7jQ=="
    }},
    "conversation": {
        "creator": null,
        "access": ["private"],
        "last_event_time": "1970-01-01T00:00:00.000Z",
        "access_role": [],
        "epoch": 0,
        "group_conv_type": "group_conversation",
        "message_timer": null,
        "team": null,
        "type": 2,
        "add_permission": null,
        "protocol": "mls",
        "group_id": "AAEAArfDl0XiFFBBk4rHDcqsOQoAc3RhZ2luZy56aW5mcmEuaW8=",
        "last_event": "0.0",
        "members": {
            "self": {
                "otr_archived": false,
                "otr_archived_ref": null,
                "otr_muted_ref": null,
                "hidden": false,
                "otr_muted_status": null,
                "status_ref": "0.0",
                "conversation_role": "wire_member",
                "service": null,
                "qualified_id": {
                    "domain": "staging.zinfra.io",
                    "id": "b82c3381-37b0-4545-b555-ca32a3a093d0"
                },
                "id": "b82c3381-37b0-4545-b555-ca32a3a093d0",
                "status_time": "1970-01-01T00:00:00.000Z",
                "hidden_ref": null,
                "status": 0
            },
            "others": []
        },
        "name": null,
        "receipt_mode": null,
        "qualified_id": {
            "domain": "staging.zinfra.io",
            "id": "b7c39745-e214-5041-938a-c70dcaac390a"
        },
        "id": "b7c39745-e214-5041-938a-c70dcaac390a",
        "cells_state": "disabled"
    }
}
*/

//  Fields [qualified_id, XX_group_id, epoch, members, type]

@Serializable
data class ConversationResponse(
    @SerialName("qualified_id")
    val id: QualifiedId,
    @Serializable(with = UUIDSerializer::class)
    @SerialName("team")
    val teamId: UUID?,
    @SerialName("group_id")
    val groupId: String,
    @SerialName("name")
    val name: String?,
    @SerialName("epoch")
    val epoch: Long,
    @SerialName("members")
    val members: ConversationMembers,
    @SerialName("type")
    val type: Type,
    @SerialName("public_keys")
    val publicKeys: MlsPublicKeysResponse? = null
) {
    @Serializable
    enum class Type {
        @SerialName("0")
        GROUP,

        @SerialName("1")
        SELF,

        @SerialName("2")
        ONE_TO_ONE
    }
}

@Serializable
data class OneToOneConversationResponse(
    @SerialName("public_keys")
    val publicKeys: MlsPublicKeysResponse? = null,
    @SerialName("conversation")
    val conversation: ConversationResponse
)

@Serializable
data class ConversationMembers(
    @SerialName("others")
    val others: List<ConversationMemberOther>
)

@Serializable
data class ConversationMemberOther(
    @SerialName("qualified_id")
    val id: QualifiedId,
    @SerialName("conversation_role")
    val conversationRole: ConversationRole
)
