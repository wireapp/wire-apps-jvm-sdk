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

import com.wire.sdk.client.BackendClient.Companion.CLIENT_QUERY_KEY
import com.wire.sdk.persistence.AppStorage
import io.ktor.http.HttpMethod
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationsApiClientTest {
    private fun notificationsClient(
        responseBody: String = "",
        assertRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {}
    ) = NotificationsApiClient(
        createMockHttpClient(
            responseBody = responseBody,
            assertRequest = assertRequest
        ),
        APP_STORAGE
    )

    @Test
    fun `when getLastNotification, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            notificationsClient(EVENT_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getLastNotification()
            assertEquals("/v15/notifications/last", capturedPath)
        }

    @Test
    fun `when getLastNotification, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            notificationsClient(EVENT_RESPONSE_JSON) { capturedMethod = it.method }
                .getLastNotification()
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when getLastNotification, then client query param set`() =
        runTest {
            var capturedParam: String? = null
            notificationsClient(EVENT_RESPONSE_JSON) {
                capturedParam =
                    it.url.parameters[CLIENT_QUERY_KEY]
            }
                .getLastNotification()
            assertEquals(DEVICE_ID, capturedParam)
        }

    @Test
    fun `given null deviceId, when getLastNotification, then client query param not set`() =
        runTest {
            val storageWithNullDevice = mockk<AppStorage> { every { getDeviceId() } returns null }
            var capturedParam: String? = "was-set"
            NotificationsApiClient(
                createMockHttpClient(EVENT_RESPONSE_JSON) {
                    capturedParam =
                        it.url.parameters[CLIENT_QUERY_KEY]
                },
                storageWithNullDevice
            ).getLastNotification()
            assertNull(capturedParam)
        }

    @Test
    fun `when getLastNotification, then response deserialized`() =
        runTest {
            val result = notificationsClient(EVENT_RESPONSE_JSON).getLastNotification()
            assertEquals("event-id-1", result.id)
            assertEquals(false, result.transient)
        }

    @Test
    fun `when getPaginatedNotifications, then correct URL`() =
        runTest {
            var capturedPath: String? = null
            notificationsClient(NOTIFICATIONS_RESPONSE_JSON) { capturedPath = it.url.encodedPath }
                .getPaginatedNotifications(querySince = null)
            assertEquals("/v15/notifications", capturedPath)
        }

    @Test
    fun `when getPaginatedNotifications, then GET method`() =
        runTest {
            var capturedMethod: HttpMethod? = null
            notificationsClient(NOTIFICATIONS_RESPONSE_JSON) { capturedMethod = it.method }
                .getPaginatedNotifications(querySince = null)
            assertEquals(HttpMethod.Get, capturedMethod)
        }

    @Test
    fun `when getPaginatedNotifications, then size query param set`() =
        runTest {
            var capturedSize: String? = null
            notificationsClient(NOTIFICATIONS_RESPONSE_JSON) {
                capturedSize = it.url.parameters["size"]
            }
                .getPaginatedNotifications(querySize = 200, querySince = null)
            assertEquals("200", capturedSize)
        }

    @Test
    fun `when getPaginatedNotifications, then default size is 100`() =
        runTest {
            var capturedSize: String? = null
            notificationsClient(NOTIFICATIONS_RESPONSE_JSON) {
                capturedSize = it.url.parameters["size"]
            }
                .getPaginatedNotifications(querySince = null)
            assertEquals("100", capturedSize)
        }

    @Test
    fun `when getPaginatedNotifications, then client query param set`() =
        runTest {
            var capturedParam: String? = null
            notificationsClient(NOTIFICATIONS_RESPONSE_JSON) {
                capturedParam =
                    it.url.parameters[CLIENT_QUERY_KEY]
            }
                .getPaginatedNotifications(querySince = null)
            assertEquals(DEVICE_ID, capturedParam)
        }

    @Test
    fun `given querySince, when getPaginatedNotifications, then since param set`() =
        runTest {
            var capturedSince: String? = null
            notificationsClient(NOTIFICATIONS_RESPONSE_JSON) {
                capturedSince =
                    it.url.parameters["since"]
            }
                .getPaginatedNotifications(querySince = "event-id-99")
            assertEquals("event-id-99", capturedSince)
        }

    @Test
    fun `given null querySince, when getPaginatedNotifications, then since param not set`() =
        runTest {
            var capturedSince: String? = "was-set"
            notificationsClient(NOTIFICATIONS_RESPONSE_JSON) {
                capturedSince =
                    it.url.parameters["since"]
            }
                .getPaginatedNotifications(querySince = null)
            assertNull(capturedSince)
        }

    @Test
    fun `when getPaginatedNotifications, then response deserialized`() =
        runTest {
            val result = notificationsClient(NOTIFICATIONS_RESPONSE_JSON)
                .getPaginatedNotifications(querySince = null)
            assertEquals(false, result.hasMore)
            assertEquals(2, result.events.size)
            assertEquals("event-id-1", result.events[0].id)
            assertEquals("event-id-2", result.events[1].id)
        }

    companion object {
        private const val DEVICE_ID = "device-id-123"

        private val APP_STORAGE = mockk<AppStorage> {
            every { getDeviceId() } returns DEVICE_ID
        }

        private val EVENT_RESPONSE_JSON = """
            {
                "id": "event-id-1",
                "payload": null,
                "transient": false
            }
        """.trimIndent()

        private val NOTIFICATIONS_RESPONSE_JSON = """
            {
                "has_more": false,
                "notifications": [
                    { "id": "event-id-1", "payload": null, "transient": false },
                    { "id": "event-id-2", "payload": null, "transient": true }
                ],
                "time": "2026-01-01T00:00:00.000Z"
            }
        """.trimIndent()
    }
}
