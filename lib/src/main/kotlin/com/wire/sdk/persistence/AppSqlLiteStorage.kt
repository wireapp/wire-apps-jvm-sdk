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
import com.wire.sdk.model.AppData

private const val DEVICE_ID = "device_id"
private const val SHOULD_REJOIN_CONVERSATIONS = "should_rejoin_conversations"
private const val LAST_NOTIFICATION_ID = "last_notification_id"

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

    override fun getAll(): List<AppData> =
        appQueries.selectAll().executeAsList().map { appMapper(it) }

    override fun getByKey(key: String): AppData =
        appQueries.selectByKey(key).executeAsOne().let { appMapper(it) }

    override fun getDeviceId(): String? = runCatching { getByKey(DEVICE_ID).value }.getOrNull()

    override fun saveDeviceId(deviceId: String) = save(DEVICE_ID, deviceId)

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
