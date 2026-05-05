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

package com.wire.sdk.persistence

import com.wire.sdk.App
import com.wire.sdk.AppQueries
import com.wire.sdk.AppsSdkDatabase
import com.wire.sdk.config.IsolatedKoinContext
import com.wire.sdk.model.AppData
import com.wire.sdk.utils.AESDecrypt
import com.wire.sdk.utils.AESEncrypt
import java.util.Base64

private const val DEVICE_ID = "device_id"
private const val BACKEND_COOKIE = "backend_cookie"
private const val ACCESS_TOKEN = "access_token"
private const val SHOULD_REJOIN_CONVERSATIONS = "should_rejoin_conversations"
private const val LAST_NOTIFICATION_ID = "last_notification_id"

@Suppress("TooManyFunctions")
class AppSqlLiteStorage(db: AppsSdkDatabase) : AppStorage {
    private val appQueries: AppQueries = db.appQueries

    override fun save(
        key: String,
        value: String
    ) {
        appQueries.insert(
            key = key,
            value_ = value
        )
    }

    override fun delete(key: String) {
        appQueries.delete(key)
    }

    override fun getAll(): List<AppData> =
        appQueries.selectAll().executeAsList().map { appMapper(it) }

    override fun getByKey(key: String): AppData =
        appQueries.selectByKey(key).executeAsOne().let { appMapper(it) }

    override fun getDeviceId(): String? = runCatching { getByKey(DEVICE_ID).value }.getOrNull()

    override fun saveDeviceId(deviceId: String) = save(DEVICE_ID, deviceId)

    override fun getBackendCookie(): String? =
        runCatching {
            val encryptedBytes = Base64.getDecoder().decode(getByKey(BACKEND_COOKIE).value)
            val key = IsolatedKoinContext.getCryptographyStorageKey()
            AESDecrypt.decryptData(encryptedBytes, key).toString(Charsets.UTF_8)
        }.getOrNull()

    override fun saveBackendCookie(cookie: String) {
        val key = IsolatedKoinContext.getCryptographyStorageKey()
        val encryptedBytes = AESEncrypt.encryptData(cookie.toByteArray(Charsets.UTF_8), key)
        save(BACKEND_COOKIE, Base64.getEncoder().encodeToString(encryptedBytes))
    }

    override fun deleteBackendCookie() = delete(BACKEND_COOKIE)

    override fun getAccessToken(): String? =
        runCatching {
            val encryptedBytes = Base64.getDecoder().decode(getByKey(ACCESS_TOKEN).value)
            val key = IsolatedKoinContext.getCryptographyStorageKey()
            AESDecrypt.decryptData(encryptedBytes, key).toString(Charsets.UTF_8)
        }.getOrNull()

    override fun saveAccessToken(accessToken: String) {
        val key = IsolatedKoinContext.getCryptographyStorageKey()
        val encryptedBytes = AESEncrypt.encryptData(accessToken.toByteArray(Charsets.UTF_8), key)
        save(ACCESS_TOKEN, Base64.getEncoder().encodeToString(encryptedBytes))
    }

    override fun deleteAccessToken() = delete(ACCESS_TOKEN)

    override fun getShouldRejoinConversations(): Boolean? =
        runCatching {
            getByKey(SHOULD_REJOIN_CONVERSATIONS).value.toBoolean()
        }.getOrNull()

    override fun setShouldRejoinConversations(should: Boolean) =
        save(SHOULD_REJOIN_CONVERSATIONS, should.toString())

    override fun getLastNotificationId(): String? =
        runCatching {
            getByKey(LAST_NOTIFICATION_ID).value
        }.getOrNull()

    override fun setLastNotificationId(lastNotificationId: String) =
        save(LAST_NOTIFICATION_ID, lastNotificationId)

    private fun appMapper(app: App) =
        AppData(
            key = app.key,
            value = app.value_
        )
}
