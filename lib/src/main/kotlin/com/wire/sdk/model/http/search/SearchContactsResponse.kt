/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.sdk.model.http.search

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.sdk.model.QualifiedId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchContactsResponse(
    @SerialName("documents")
    val documents: List<ContactDocument>,
    @SerialName("found")
    val found: Long,
    @SerialName("has_more")
    val hasMore: Boolean? = null, // TODO: Is it ok to have null as default? Actually seems better than true or false.
    @SerialName("paging_state")
    val pagingState: String?,
    @SerialName("returned")
    val returned: Long,
    @SerialName("search_policy")
    val searchPolicy: SearchPolicy,
    @SerialName("took")
    val took: Long
)

@Serializable
data class ContactDocument(
    @SerialName("accent_id")
    val accentId: Long?,
    @SerialName("handle")
    val handle: String?,
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("qualified_id")
    val qualifiedId: QualifiedId?,
    @SerialName("team")
    val team: String?,
    @SerialName("type")
    val type: UserType?
)

@Serializable
enum class SearchPolicy {
    @SerialName("no_search")
    NO_SEARCH,

    @SerialName("exact_handle_search")
    EXACT_HANDLE_SEARCH,

    @SerialName("full_search")
    FULL_SEARCH
}

@Serializable
enum class UserType {
    @SerialName("regular")
    REGULAR,

    @SerialName("bot")
    BOT,

    @SerialName("external")
    EXTERNAL,

    @SerialName("federated")
    FEDERATED,

    @SerialName("guest")
    GUEST
}
