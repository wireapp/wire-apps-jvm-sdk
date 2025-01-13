package com.wire.integrations.jvm.model

class Team {
    // Data classes might have issues in library development, check if secondary constructor is the best option
    constructor(id: String) {
        this.id = id
    }

    val id: String
}
