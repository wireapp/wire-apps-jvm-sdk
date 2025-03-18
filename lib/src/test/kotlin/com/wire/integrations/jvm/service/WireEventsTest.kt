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

package com.wire.integrations.jvm.service

import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.utils.KtxSerializer
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

class WireEventsTest : KoinTest {

    @Test
    fun givenWireEventsHandlerIsInjectedThenCallingItsMethodsSucceeds() {
        val wireEvents = get<WireEventsHandler>()

        wireEvents.onNewConversation(EXPECTED_NEW_CONVERSATION_VALUE.toString())
        wireEvents.onNewMLSMessage(EXPECTED_NEW_MLS_MESSAGE_VALUE.toString())
    }

    @Test
    fun whenDeserializingConversationCreateEventThenItShouldMapCorrectlyToNewConversationDTO() {
        val event =
            KtxSerializer.json.decodeFromString<EventResponse>(
                DUMMY_CONVERSATION_CREATE_EVENT_RESPONSE
            )

        assertIs<EventContentDTO.Conversation.NewConversationDTO>(event.payload?.first())
    }

    companion object {
        private val EXPECTED_NEW_CONVERSATION_VALUE = Instant.DISTANT_FUTURE
        private val EXPECTED_NEW_MLS_MESSAGE_VALUE = Instant.DISTANT_PAST

        private const val DUMMY_CONVERSATION_CREATE_EVENT_RESPONSE =
            """{
                  "id": "4c2c48f6-84af-11ef-8001-860acb7b851a",
                  "payload": [
                    {
                      "conversation": "9bb5fc3f-a5fb-4783-ae88-00a07a39732d",
                      "from": "95d52e20-8428-4619-9a81-dbc2298a3f28",
                      "qualified_conversation": {
                        "domain": "anta.wire.link",
                        "id": "9bb5fc3f-a5fb-4783-ae88-00a07a39732d"
                      },
                      "qualified_from": {
                        "domain": "anta.wire.link",
                        "id": "95d52e20-8428-4619-9a81-dbc2298a3f28"
                      },
                      "time": "2024-10-07T13:23:10.386Z",
                      "team": "95d52e20-8428-4619-9a81-dbc2298a3f28",
                      "type": "conversation.create"
                    }
                  ]
                }
            """

        @JvmStatic
        @AfterAll
        fun teardown() {
            stopKoin()
        }

        @JvmStatic
        @BeforeAll
        fun setup() {
            val wireEventsHandler =
                object : WireEventsHandler() {
                    override fun onNewConversation(value: String) {
                        assertEquals(EXPECTED_NEW_CONVERSATION_VALUE.toString(), value)
                    }

                    override fun onNewMLSMessage(value: String) {
                        assertEquals(EXPECTED_NEW_MLS_MESSAGE_VALUE.toString(), value)
                    }
                }

            startKoin {
                modules(
                    module {
                        single<WireEventsHandler> { wireEventsHandler }
                    }
                )
            }
        }
    }
}
