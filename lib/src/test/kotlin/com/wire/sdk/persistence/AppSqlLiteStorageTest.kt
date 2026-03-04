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

import com.wire.sdk.TestUtils
import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.config.IsolatedKoinContext
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppSqlLiteStorageTest {
    @Test
    fun givenNewCookie_thenAesDecryptionWorks() =
        runTest {
            val cookie = UUID.randomUUID().toString()
            val eventsHandler = object : WireEventsHandlerSuspending() {}
            TestUtils.setupSdk(eventsHandler)

            val appStorage = IsolatedKoinContext.koinApp.koin.get<AppStorage>()
            val initCookie = appStorage.getBackendCookie()
            assertNotNull(initCookie)
            assertTrue { initCookie != cookie }

            appStorage.saveBackendCookie(cookie)
            val secondCookie = appStorage.getBackendCookie()
            assertTrue { secondCookie == cookie }
        }

    companion object {
        @JvmStatic
        @BeforeAll
        fun before() {
            IsolatedKoinContext.start()
        }

        @JvmStatic
        @AfterAll
        fun after() {
            IsolatedKoinContext.stop()
        }
    }
}
