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

import com.wire.integrations.jvm.WireAppSdk
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.cryptography.CryptoClient
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.test.junit5.KoinTestExtension
import java.util.UUID
import kotlin.test.assertEquals

class WireEventsTest : KoinTest {
    private val wireEventsHandler =
        object : WireEventsHandler() {
            override fun onNewConversation(value: String) {
                assertEquals(EXPECTED_NEW_CONVERSATION_VALUE.toString(), value)
            }

            override fun onNewMessage(value: String) {
                assertEquals(EXPECTED_NEW_MESSAGE_VALUE.toString(), value)
            }

            override fun onNewMLSMessage(value: String) {
                assertEquals(EXPECTED_NEW_MLS_MESSAGE_VALUE.toString(), value)
            }
        }

    @JvmField
    @RegisterExtension
    val koinTestExtension =
        KoinTestExtension.create {
            modules(
                module {
                    single<WireEventsHandler> { wireEventsHandler }
                    single<WireTeamEventsHandler> { WireTeamEventsHandler(get()) }
                }
            )
        }

    @Test
    fun givenWireEventsHandlerIsInjectedThenCallingItsMethodsSucceeds() {
        val wireEvents = get<WireEventsHandler>()

        wireEvents.onNewConversation(EXPECTED_NEW_CONVERSATION_VALUE.toString())
        wireEvents.onNewMessage(EXPECTED_NEW_MESSAGE_VALUE.toString())
        wireEvents.onNewMLSMessage(EXPECTED_NEW_MLS_MESSAGE_VALUE.toString())
    }

    @Test
    fun givenKoinInjectionsWhenCallingHandleEventsThenTheCorrectMethodIsCalled() {
        val wireEventsHandler = get<WireEventsHandler>()
        val wireTeamEventsHandler = get<WireTeamEventsHandler>()

        val wireSdkApp =
            WireAppSdk(
                applicationId = APPLICATION_ID,
                apiToken = API_TOKEN,
                apiHost = API_HOST,
                cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
                wireEventsHandler = wireEventsHandler
            )

        val cryptoClient = CryptoClient(team = TEAM)

        wireTeamEventsHandler.handleEvents(
            team = TEAM,
            event = EVENT_RESPONSE,
            cryptoClient = cryptoClient
        )

        cryptoClient.close()
    }

    companion object {
        private val APPLICATION_ID = UUID.randomUUID()
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "localhost:8080"
        private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "dummyPassword"
        private val EXPECTED_NEW_CONVERSATION_VALUE = Instant.DISTANT_FUTURE
        private val EXPECTED_NEW_MESSAGE_VALUE = Instant.DISTANT_PAST
        private val EXPECTED_NEW_MLS_MESSAGE_VALUE = Instant.DISTANT_PAST
        private val CONVERSATION_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
        private val USER_ID =
            QualifiedId(
                id = UUID.randomUUID(),
                domain = "wire.com"
            )
        private val TEAM =
            Team(
                id = UUID.randomUUID(),
                userId =
                    QualifiedId(
                        id = UUID.randomUUID(),
                        domain = "wire.com"
                    ),
                clientId = "client_id_1234",
                accessToken = "abcd",
                refreshToken = "1234"
            )
        private val EVENT_RESPONSE =
            EventResponse(
                id = "event_id1",
                payload =
                    listOf(
                        EventContentDTO.Conversation.NewConversationDTO(
                            qualifiedConversation = CONVERSATION_ID,
                            qualifiedFrom = USER_ID,
                            time = EXPECTED_NEW_CONVERSATION_VALUE,
                            data = ConversationResponse(dummyField = "dummyString")
                        )
                    ),
                transient = true
            )
    }
}
