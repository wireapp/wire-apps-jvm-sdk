package com.wire.integrations.sample

import com.wire.integrations.jvm.WireBotListener
import com.wire.integrations.jvm.WireBotSdk

fun main() {
    val wireBotSdk = WireBotSdk(object : WireBotListener {
        override fun onEvent(event: String) {
            println(event)
        }
    })
    val credentialsManager = wireBotSdk.getTeamManager()

    println("SDK initialized")
    credentialsManager.getTeams().forEach {
        println("Team: ${it.id}")
    }
}
