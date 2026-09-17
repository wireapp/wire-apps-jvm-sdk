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

package com.wire.sdk.client

import com.wire.sdk.model.QualifiedId
import io.ktor.http.HttpMethod
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CallingApiClientTest {
    private val id = QualifiedId(UUID.randomUUID(), "wire.test")
    private val path = "/conversations/${id.domain}/${id.id}/subconversations/conference"

    @Test
    fun `conference details include epoch group and member devices`() =
        runTest {
            val json = """
            {"group_id":"AQID","epoch":7,"members":[
            {"user_id":"${id.id}","domain":"wire.test","client_id":"device"}
            ],"subconv_id":"conference","cipher_suite":1}
            """.trimIndent()
            createMockHttpClient(json) {
                assertEquals(path, it.url.encodedPath)
                assertEquals(HttpMethod.Get, it.method)
            }.use { backend ->
                val result = ConversationsApiClient(backend).getConference(id)
                assertEquals(7uL, result.epoch)
                assertEquals("AQID", result.groupId)
                assertEquals("device", result.members.single().clientId)
            }
        }

    @Test
    fun `group info uses conference path and MLS accept header`() =
        runTest {
            createMockHttpClient(byteArrayOf(1, 2, 3)) {
                assertEquals("$path/groupinfo", it.url.encodedPath)
                assertEquals("message/mls", it.headers["Accept"])
            }.use { backend ->
                assertContentEquals(
                    byteArrayOf(1, 2, 3),
                    ConversationsApiClient(backend).getGroupInfo(id)
                )
            }
        }

    @Test
    fun `leave targets this client conference membership`() =
        runTest {
            createMockHttpClient {
                assertEquals("$path/self", it.url.encodedPath)
                assertEquals(HttpMethod.Delete, it.method)
            }.use { backend ->
                ConversationsApiClient(backend).leaveConference(id)
            }
        }

    @Test
    fun `configuration is returned unchanged`() =
        runTest {
            val config = """
                {"sft_servers":[{"urls":["https://sft.wire.test"]}],"new_field":true}
            """.trimIndent()
            createMockHttpClient(config) {
                assertEquals("/calls/config/v2", it.url.encodedPath)
            }.use { backend ->
                assertEquals(config, ConversationsApiClient(backend).getConfiguration())
            }
        }
}
