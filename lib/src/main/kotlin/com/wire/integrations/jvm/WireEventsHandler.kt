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

package com.wire.integrations.jvm

import com.wire.integrations.jvm.config.IsolatedKoinContext
import com.wire.integrations.jvm.service.WireApplicationManager

/**
 * Abstract class exposed by the SDK to handle events. This subclasses cannot be subclassed
 * directly, you should subclass [WireEventsHandlerDefault] or [WireEventsHandlerSuspending].
 */
sealed class WireEventsHandler {
    /**
     * The [WireApplicationManager] is used to manage the Wire application lifecycle and
     * communication with the backend.
     * NOTE: Do not use manager in the constructor of this class, as it will be null at that time.
     * Use it only inside the event handling methods.
     */
    val manager: WireApplicationManager by lazy {
        IsolatedKoinContext.koinApp.koin.get<WireApplicationManager>()
    }
}
