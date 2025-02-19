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

package com.wire.integrations.jvm.config

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.integrations.jvm.AppsSdkDatabase
import com.wire.integrations.jvm.client.BackendClient
import com.wire.integrations.jvm.client.BackendClientDemo
import com.wire.integrations.jvm.persistence.TeamSqlLiteStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.jvm.service.EventsRouter
import com.wire.integrations.jvm.service.WireApplicationManager
import com.wire.integrations.jvm.service.WireTeamRegistrator
import org.koin.dsl.module

val sdkModule =
    module {
        single<SqlDriver> {
            val driver: SqlDriver = JdbcSqliteDriver(getProperty("database-jdbc-url"))
            AppsSdkDatabase.Schema.create(driver)
            driver
        }
        single<TeamStorage> { TeamSqlLiteStorage(AppsSdkDatabase(get())) }
        single { EventsRouter(get()) }
        single<BackendClient> { BackendClientDemo(get()) }
        single { WireApplicationManager(get(), get(), get(), get()) }
        single { WireTeamRegistrator(get(), get(), get(), get()) }
    }
