package com.wire.integrations.jvm.persistence

import com.wire.integrations.jvm.model.Team

interface TeamStorage {
    fun getAll(): List<Team>
}
