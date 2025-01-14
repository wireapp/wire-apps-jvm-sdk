package com.wire.integrations.jvm.config

import org.koin.dsl.koinApplication

internal object IsolatedKoinContext {
    val koinApp =
        koinApplication {
            modules(sdkModule)
        }
}
