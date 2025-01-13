package com.wire.integrations.jvm.config

import com.wire.integrations.jvm.persistence.TeamSqlLiteStorage
import com.wire.integrations.jvm.persistence.TeamStorage
import com.wire.integrations.jvm.service.WireTeamManager
import org.koin.dsl.module

val sdkModule = module {
    single<TeamStorage> { TeamSqlLiteStorage() }
    single { WireTeamManager(get()) }
}
