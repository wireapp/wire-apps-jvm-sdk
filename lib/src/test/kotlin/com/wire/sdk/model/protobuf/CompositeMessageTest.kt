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

package com.wire.sdk.model.protobuf

import com.wire.integrations.protobuf.messages.Messages
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.WireMessage
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeMessageTest {
    @Test
    fun `buttonActionConfirmation does not include buttonId when it is null`() {
        val message = WireMessage.ButtonActionConfirmation.create(
            conversationId = QualifiedId(UUID.randomUUID(), "example.domain"),
            referencedMessageId = UUID.randomUUID().toString(),
            buttonId = null
        )

        val bytes = ProtobufSerializer.toGenericMessageByteArray(message)
        val proto = Messages.GenericMessage.parseFrom(bytes)

        assertTrue(
            proto.hasButtonActionConfirmation(),
            "GenericMessage should contain ButtonActionConfirmation"
        )
        assertFalse(
            proto.buttonActionConfirmation.hasButtonId(),
            "buttonId shouldn't be present when null"
        )
        assertEquals(
            message.referencedMessageId,
            proto.buttonActionConfirmation.referenceMessageId
        )
    }
}
