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

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.wire.integrations.jvm.WireAppSdk
import com.wire.integrations.jvm.WireEventsHandler
import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.model.ClientId
import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.Team
import com.wire.integrations.jvm.model.TeamId
import com.wire.integrations.jvm.model.http.EventContentDTO
import com.wire.integrations.jvm.model.http.EventResponse
import com.wire.integrations.jvm.model.http.conversation.ConversationResponse
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.koin.core.Koin
import org.koin.test.KoinTest
import org.koin.test.get
import java.util.UUID
import kotlin.test.assertTrue

class WireEventsIntegrationTest : KoinTest {
    // Override the Koin instance as we use an isolated context
    override fun getKoin(): Koin = IsolatedKoinContext.koinApp.koin

    @Test
    fun givenKoinInjectionsWhenCallingHandleEventsThenTheCorrectMethodIsCalled() {
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlMatching("/v7/clients")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "id": "dummyClientId"
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.post(WireMock.urlMatching("/v7/login")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "access_token": "demoAccessToken"
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.get(WireMock.urlMatching("/v7/feature-configs")).willReturn(
                WireMock.okJson(
                    """
                    {
                        "mls": {
                            "config": {
                                "allowedCipherSuites": [1],
                                "defaultCipherSuite": 1,
                                "defaultProtocol": "mls",
                                "supportedProtocols": ["mls", "proteus"]
                            },
                            "status": "enabled"
                        }
                    }
                    """.trimIndent()
                )
            )
        )
        wireMockServer.stubFor(
            WireMock.put(WireMock.urlPathTemplate("/v7/clients/{clientId}")).willReturn(
                ok()
            )
        )
        wireMockServer.stubFor(
            WireMock.post(
                WireMock.urlPathTemplate("/v7/mls/key-packages/self/{clientId}")
            ).willReturn(ok())
        )

        WireAppSdk(
            applicationId = APPLICATION_ID,
            apiToken = API_TOKEN,
            apiHost = API_HOST,
            cryptographyStoragePassword = CRYPTOGRAPHY_STORAGE_PASSWORD,
            object : WireEventsHandler() {
                override fun onEvent(event: String) {
                    println(event)
                }
            }
        )

        val eventsRouter = get<EventsRouter>()
        eventsRouter.routeEvents(
            event = NEW_TEAM_INVITE_EVENT
        )
        eventsRouter.routeEvents(
            event = NEW_CONVERSATION_EVENT
        )

        assertTrue { eventsRouter.teamClients.size == 1 }
    }

    companion object {
        private val APPLICATION_ID = UUID.randomUUID()
        private const val API_TOKEN = "dummyToken"
        private const val API_HOST = "localhost:8086"
        private const val CRYPTOGRAPHY_STORAGE_PASSWORD = "dummyPassword"
        private val EXPECTED_NEW_CONVERSATION_VALUE = Instant.DISTANT_FUTURE
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
                id = TeamId(UUID.randomUUID()),
                userId =
                    QualifiedId(
                        id = UUID.randomUUID(),
                        domain = "wire.com"
                    ),
                clientId = ClientId(UUID.randomUUID().toString())
            )
        private val NEW_TEAM_INVITE_EVENT =
            EventResponse(
                id = "event_id1",
                payload =
                    listOf(
                        EventContentDTO.TeamInvite(
                            teamId = TEAM.id.value
                        )
                    ),
                transient = true
            )
        private val NEW_CONVERSATION_EVENT =
            EventResponse(
                id = "event_id2",
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
        private val wireMockServer = WireMockServer(8086)

        @JvmStatic
        @BeforeAll
        fun before() {
            wireMockServer.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            wireMockServer.stop()
        }
    }
}
