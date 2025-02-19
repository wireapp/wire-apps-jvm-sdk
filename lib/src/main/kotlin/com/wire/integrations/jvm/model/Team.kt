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

package com.wire.integrations.jvm.model

import java.util.UUID

typealias ClientId = String

class Team {
    // Data classes might have issues in library development, check if secondary constructor is the best option
    constructor(
        id: UUID,
        userId: QualifiedId,
        clientId: ClientId
    ) {
        this.id = id
        this.userId = userId
        this.clientId = clientId
    }

    val id: UUID
    val userId: QualifiedId
    val clientId: ClientId

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Team) return false

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "Team(id=$id, userId=$userId, clientId='$clientId')"
    }
}
