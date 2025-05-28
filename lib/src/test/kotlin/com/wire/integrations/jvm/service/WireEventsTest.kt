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

import com.wire.crypto.MLSGroupId
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.WireEventsHandlerSuspending
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.ConversationData
import com.wire.integrations.jvm.model.ConversationMember
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.utils.KtxSerializer
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.dsl.module

class WireEventsTest {
    @Test
    fun givenWireEventsHandlerIsInjectedThenCallingItsMethodsSucceeds() =
        runBlocking {
            val wireEvents = IsolatedKoinContext
                .koinApp
                .koin
                .get<WireEventsHandler>() as WireEventsHandlerSuspending

            wireEvents.onConversationJoin(
                conversation = ConversationData(
                    id = CONVERSATION_ID,
                    name = "Test conversation",
                    teamId = null,
                    mlsGroupId = MLSGroupId(ByteArray(32) { 1 })
                ),
                members = emptyList()
            )

            wireEvents.onMessage(
                wireMessage = WireMessage.Text(
                    id = UUID.randomUUID(),
                    conversationId = CONVERSATION_ID,
                    sender = QualifiedId(
                        id = UUID.randomUUID(),
                        domain = "anta.wire.link"
                    ),
                    text = EXPECTED_NEW_MLS_MESSAGE_VALUE
                )
            )
        }

    @Test
    fun givenWireEventsHandlerIsInjectedThenCallingNewAssetMethodItSucceeds() =
        runBlocking {
            val wireEvents = IsolatedKoinContext
                .koinApp
                .koin
                .get<WireEventsHandler>() as WireEventsHandlerSuspending

            wireEvents.onAsset(
                wireMessage = WireMessage.Asset(
                    id = UUID.randomUUID(),
                    conversationId = CONVERSATION_ID,
                    sender = QualifiedId(UUID.randomUUID(), "anta.wire.link"),
                    sizeInBytes = 1000L,
                    name = EXPECTED_NEW_MLS_MESSAGE_VALUE,
                    mimeType = "*/*"
                )
            )
        }

    @Test
    fun whenDeserializingConversationCreateEventThenItShouldMapCorrectlyToNewConversationDTO() {
        val event =
            KtxSerializer.json.decodeFromString<EventResponse>(
                DUMMY_CONVERSATION_CREATE_EVENT_RESPONSE
            )

        assertIs<EventContentDTO.Conversation.NewConversationDTO>(event.payload?.first())
    }

    @Test
    fun givenWireEventsHandlerIsInjectedThenCallingNewKnockMethodItSucceeds() =
        runBlocking {
            val wireEvents = IsolatedKoinContext
                .koinApp
                .koin
                .get<WireEventsHandler>() as WireEventsHandlerSuspending

            wireEvents.onKnock(
                wireMessage = WireMessage.Knock(
                    id = UUID.randomUUID(),
                    conversationId = CONVERSATION_ID,
                    sender = QualifiedId(UUID.randomUUID(), "anta.wire.link"),
                    hotKnock = true
                )
            )
        }

    @Test
    fun givenWireEventsHandlerIsInjectedThenCallingNewLocationMethodItSucceeds() =
        runBlocking {
            val wireEvents = IsolatedKoinContext
                .koinApp
                .koin
                .get<WireEventsHandler>() as WireEventsHandlerSuspending

            wireEvents.onLocation(
                wireMessage = WireMessage.Location(
                    id = UUID.randomUUID(),
                    conversationId = CONVERSATION_ID,
                    sender = QualifiedId(UUID.randomUUID(), "anta.wire.link"),
                    latitude = EXPECTED_LOCATION_LATITUDE,
                    longitude = EXPECTED_LOCATION_LONGITUDE
                )
            )
        }

    companion object {
        private val EXPECTED_NEW_MLS_MESSAGE_VALUE = UUID.randomUUID().toString()
        private val CONVERSATION_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = "anta.wire.link"
        )

        private val EXPECTED_LOCATION_LATITUDE = 11.12345F
        private val EXPECTED_LOCATION_LONGITUDE = 12.12345F

        private val DUMMY_CONVERSATION_CREATE_EVENT_RESPONSE =
            """{
                  "id": "4c2c48f6-84af-11ef-8001-860acb7b851a",
                  "payload": [
                    {
                      "conversation": ${CONVERSATION_ID.id},
                      "from": "95d52e20-8428-4619-9a81-dbc2298a3f28",
                      "data": {
                        "name": "Test conversation",
                        "members": {
                            "others": [
                                {
                                    "conversation_role": "wire_admin",
                                    "id": "0281c0d1-a37b-490e-ab89-4846f12069ed",
                                    "qualified_id": {
                                        "domain": "wire.com",
                                        "id": "0281c0d1-a37b-490e-ab89-4846f12069ed"
                                    },
                                    "status": 0
                                }
                            ],
                            "self": {
                                "conversation_role": "wire_member",
                                "hidden": false,
                                "hidden_ref": null,
                                "id": "0c5fbeaf-dcb0-4c03-ad67-4198f7457196",
                                "otr_archived": false,
                                "otr_archived_ref": null,
                                "otr_muted_ref": null,
                                "otr_muted_status": null,
                                "qualified_id": {
                                    "domain": "wire.com",
                                    "id": "0c5fbeaf-dcb0-4c03-ad67-4198f7457196"
                                },
                                "service": null,
                                "status": 0,
                                "status_ref": "0.0",
                                "status_time": "1970-01-01T00:00:00.000Z"
                            }
                        }
                      },
                      "qualified_conversation": {
                        "domain": "${CONVERSATION_ID.domain}",
                        "id": "${CONVERSATION_ID.id}"
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

        private val wireEventsHandler =
            object : WireEventsHandlerSuspending() {
                override suspend fun onConversationJoin(
                    conversation: ConversationData,
                    members: List<ConversationMember>
                ) {
                    assertEquals(CONVERSATION_ID, conversation.id)
                }

                override suspend fun onMessage(wireMessage: WireMessage.Text) {
                    assertEquals(
                        EXPECTED_NEW_MLS_MESSAGE_VALUE,
                        wireMessage.text
                    )
                }

                override suspend fun onAsset(wireMessage: WireMessage.Asset) {
                    assertEquals(
                        EXPECTED_NEW_MLS_MESSAGE_VALUE.toString(),
                        wireMessage.name
                    )
                }

                override suspend fun onKnock(wireMessage: WireMessage.Knock) {
                    assertTrue { wireMessage.hotKnock }
                }

                override suspend fun onLocation(wireMessage: WireMessage.Location) {
                    assertEquals(EXPECTED_LOCATION_LATITUDE, wireMessage.latitude)
                    assertEquals(EXPECTED_LOCATION_LONGITUDE, wireMessage.longitude)
                }
            }

        @JvmStatic
        @BeforeAll
        fun before() {
            val modules = module {
                single<WireEventsHandler> { wireEventsHandler }
                single<EventsRouter> {
                    EventsRouter(get(), get(), get(), get(), get(), get())
                }
            }
            IsolatedKoinContext.start()
            IsolatedKoinContext.koin.loadModules(listOf(modules))
        }

        @JvmStatic
        @AfterAll
        fun after() {
            IsolatedKoinContext.stop()
        }
    }
}
