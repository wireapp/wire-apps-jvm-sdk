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

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
}
