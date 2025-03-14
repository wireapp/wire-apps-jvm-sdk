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

package com.wire.integrations.jvm.persistence

import com.wire.integrations.jvm.App
import com.wire.integrations.jvm.AppQueries
import com.wire.integrations.jvm.AppsSdkDatabase
import com.wire.integrations.jvm.model.AppClientId
import com.wire.integrations.jvm.model.AppData

private const val CLIENT_ID = "client_id"

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

    override fun getById(key: String): AppData =
        appQueries.selectByKey(key).executeAsOne().let { appMapper(it) }

    override fun getClientId(): AppClientId? =
        runCatching { AppClientId(getById(CLIENT_ID).value) }.getOrNull()

    override fun saveClientId(appClientId: String) = save(CLIENT_ID, appClientId)

    private fun appMapper(app: App) =
        AppData(
            key = app.key,
            value = app.value_
        )
}
