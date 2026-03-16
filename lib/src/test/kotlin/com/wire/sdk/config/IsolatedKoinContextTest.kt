/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.sdk.config

import com.wire.sdk.model.QualifiedId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IsolatedKoinContextTest {
    @BeforeEach
    fun setUp() {
        IsolatedKoinContext.start()
    }

    @AfterEach
    fun tearDown() {
        IsolatedKoinContext.stop()
    }

    @Test
    fun `koinApp is accessible after start`() {
        assertNotNull(IsolatedKoinContext.koinApp)
    }

    @Test
    fun `koin is accessible after start`() {
        assertNotNull(IsolatedKoinContext.koin)
    }

    @Test
    fun `koinApp throws after stop`() {
        IsolatedKoinContext.stop()
        assertThrows<IllegalStateException> {
            IsolatedKoinContext.koinApp
        }
    }

    @Test
    fun `start closes previous instance and creates a new one`() {
        val firstApp = IsolatedKoinContext.koinApp
        IsolatedKoinContext.start()
        val secondApp = IsolatedKoinContext.koinApp
        assertNotSame(firstApp, secondApp)
    }

    @Test
    fun `stop clears applicationUser cache`() {
        val id = UUID.randomUUID()
        IsolatedKoinContext.setApplicationId(id)
        IsolatedKoinContext.setBackendDomain("wire.example.com")
        IsolatedKoinContext.getApplicationUser() // populate cache

        IsolatedKoinContext.stop()
        IsolatedKoinContext.start()

        // After a fresh start the cached user must be gone
        assertThrows<IllegalStateException> {
            IsolatedKoinContext.getApplicationUser()
        }
    }

    @Test
    fun `getApplicationUser returns correct QualifiedId when both properties are set`() {
        val id = UUID.randomUUID()
        val domain = "wire.example.com"

        IsolatedKoinContext.setApplicationId(id)
        IsolatedKoinContext.setBackendDomain(domain)

        val user: QualifiedId = IsolatedKoinContext.getApplicationUser()

        assertEquals(id, user.id)
        assertEquals(domain, user.domain)
    }

    @Test
    fun `getApplicationUser throws when applicationId is not set`() {
        IsolatedKoinContext.setBackendDomain("wire.example.com")

        assertThrows<IllegalStateException> {
            IsolatedKoinContext.getApplicationUser()
        }
    }

    @Test
    fun `getApplicationUser throws when backendDomain is not set`() {
        IsolatedKoinContext.setApplicationId(UUID.randomUUID())

        assertThrows<IllegalStateException> {
            IsolatedKoinContext.getApplicationUser()
        }
    }

    @Test
    fun `getApplicationUser throws when neither applicationId nor backendDomain are set`() {
        assertThrows<IllegalStateException> {
            IsolatedKoinContext.getApplicationUser()
        }
    }

    @Test
    fun `getApplicationUser returns cached instance on repeated calls`() {
        IsolatedKoinContext.setApplicationId(UUID.randomUUID())
        IsolatedKoinContext.setBackendDomain("wire.example.com")

        val first = IsolatedKoinContext.getApplicationUser()
        val second = IsolatedKoinContext.getApplicationUser()

        assertSame(first, second)
    }

    @Test
    fun `setApplicationId clears cached applicationUser`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val domain = "wire.example.com"

        IsolatedKoinContext.setApplicationId(id1)
        IsolatedKoinContext.setBackendDomain(domain)
        val firstUser = IsolatedKoinContext.getApplicationUser()
        assertEquals(id1, firstUser.id)

        IsolatedKoinContext.setApplicationId(id2)
        val secondUser = IsolatedKoinContext.getApplicationUser()
        assertEquals(id2, secondUser.id)
        assertNotEquals(firstUser, secondUser)
    }

    @Test
    fun `setBackendDomain clears cached applicationUser`() {
        val id = UUID.randomUUID()

        IsolatedKoinContext.setApplicationId(id)
        IsolatedKoinContext.setBackendDomain("first.example.com")
        val firstUser = IsolatedKoinContext.getApplicationUser()
        assertEquals("first.example.com", firstUser.domain)

        IsolatedKoinContext.setBackendDomain("second.example.com")
        val secondUser = IsolatedKoinContext.getApplicationUser()
        assertEquals("second.example.com", secondUser.domain)
        assertNotEquals(firstUser, secondUser)
    }

    @Test
    fun `getApiHost returns value that was set`() {
        val host = "https://api.wire.example.com"
        IsolatedKoinContext.setApiHost(host)
        assertEquals(host, IsolatedKoinContext.getApiHost())
    }

    @Test
    fun `getApiHost throws when not set`() {
        assertThrows<IllegalStateException> {
            IsolatedKoinContext.getApiHost()
        }
    }

    @Test
    fun `setApiHost overwrites a previously set value`() {
        IsolatedKoinContext.setApiHost("https://old.example.com")
        IsolatedKoinContext.setApiHost("https://new.example.com")
        assertEquals("https://new.example.com", IsolatedKoinContext.getApiHost())
    }

    @Test
    fun `getCryptographyStorageKey returns value that was set`() {
        val key = ByteArray(32) { it.toByte() }
        IsolatedKoinContext.setCryptographyStorageKey(key)
        assertArrayEquals(key, IsolatedKoinContext.getCryptographyStorageKey())
    }

    @Test
    fun `getCryptographyStorageKey throws when not set`() {
        assertThrows<IllegalStateException> {
            IsolatedKoinContext.getCryptographyStorageKey()
        }
    }

    @Test
    fun `setCryptographyStorageKey overwrites a previously set value`() {
        val key1 = ByteArray(32) { 0x00 }
        val key2 = ByteArray(32) { 0xFF.toByte() }
        IsolatedKoinContext.setCryptographyStorageKey(key1)
        IsolatedKoinContext.setCryptographyStorageKey(key2)
        assertArrayEquals(key2, IsolatedKoinContext.getCryptographyStorageKey())
    }

    @Test
    fun `setCryptographyStorageKey accepts empty byte array`() {
        val key = ByteArray(0)
        IsolatedKoinContext.setCryptographyStorageKey(key)
        assertArrayEquals(key, IsolatedKoinContext.getCryptographyStorageKey())
    }

    @Test
    fun `getApplicationUser is safe under concurrent access`() {
        val id = UUID.randomUUID()
        val domain = "wire.example.com"
        IsolatedKoinContext.setApplicationId(id)
        IsolatedKoinContext.setBackendDomain(domain)

        val threadCount = 20
        val latch = CountDownLatch(threadCount)
        val executor = Executors.newFixedThreadPool(threadCount)
        val results = mutableListOf<QualifiedId>()
        val lock = Any()

        repeat(threadCount) {
            executor.submit {
                try {
                    val user = IsolatedKoinContext.getApplicationUser()
                    synchronized(lock) { results.add(user) }
                } finally {
                    latch.countDown()
                }
            }
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        executor.shutdown()

        assertEquals(threadCount, results.size)
        val first = results.first()
        results.forEach { assertSame(first, it) }
    }

    @Test
    fun `getApplicationUser reflects latest applicationId after cache is cleared`() {
        val initialId = UUID.randomUUID()
        val updatedId = UUID.randomUUID()
        val domain = "wire.example.com"

        IsolatedKoinContext.setApplicationId(initialId)
        IsolatedKoinContext.setBackendDomain(domain)
        IsolatedKoinContext.getApplicationUser() // populate cache

        IsolatedKoinContext.setApplicationId(updatedId) // clears cache, updates Koin

        val user = IsolatedKoinContext.getApplicationUser()
        assertEquals(
            updatedId,
            user.id,
            "User must reflect the updated applicationId, not the cached stale one"
        )
        assertEquals(domain, user.domain)
    }
}
