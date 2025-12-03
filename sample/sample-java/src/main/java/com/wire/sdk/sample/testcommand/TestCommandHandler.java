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

package com.wire.sdk.sample.testcommand;

import com.wire.sdk.model.WireMessage;
import com.wire.sdk.service.WireApplicationManager;

public class TestCommandHandler {

    private final TestCommandProcessor processor;

    public TestCommandHandler(WireApplicationManager applicationManager) {
        this.processor = new TestCommandProcessor(applicationManager);
    }

    public void handle(TestCommand command, WireMessage.Text message) {
        if (command != null) {
            processor.process(command, message);
        }
    }

}
