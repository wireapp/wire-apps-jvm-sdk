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

package com.wire.sdk.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.crypto.ConversationId
import com.wire.sdk.AppsSdkDatabase
import com.wire.sdk.model.ConversationEntity
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.http.conversation.ReceiptMode
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class ConversationSqlLiteStorageTest {
    @Test
    fun `receipt mode is persisted and updated`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppsSdkDatabase.Schema.create(driver)
            val storage = ConversationSqlLiteStorage(AppsSdkDatabase(driver))
            val conversationId = QualifiedId(UUID.randomUUID(), "wire.test")
            val conversation = ConversationEntity(
                id = conversationId,
                name = "Test conversation",
                teamId = TeamId(UUID.randomUUID()),
                mlsGroupId = ConversationId(UUID.randomUUID().toString().toByteArray()),
                type = ConversationEntity.Type.GROUP,
                receiptMode = ReceiptMode.ENABLED
            )

            storage.save(conversation)
            assertEquals(ReceiptMode.ENABLED, storage.getById(conversationId)?.receiptMode)

            storage.save(conversation.copy(receiptMode = ReceiptMode.DISABLED))
            assertEquals(ReceiptMode.DISABLED, storage.getById(conversationId)?.receiptMode)

            storage.updateReceiptMode(conversationId, ReceiptMode.ENABLED)
            assertEquals(ReceiptMode.ENABLED, storage.getById(conversationId)?.receiptMode)
        } finally {
            driver.close()
        }
    }
}
