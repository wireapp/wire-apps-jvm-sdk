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

public enum TestCommand {
    CREATE_ONE_TO_ONE_CONVERSATION("create-one2one-conversation"),
    CREATE_GROUP_CONVERSATION("create-group-conversation"),
    CREATE_CHANNEL_CONVERSATION("create-channel-conversation"),
    ASSET_IMAGE("asset-image"),
    ASSET_AUDIO("asset-audio"),
    ASSET_VIDEO("asset-video"),
    ASSET_PDF_DOCUMENT("asset-document-pdf");

    private final String commandStr;

    TestCommand(String commandString) {
        this.commandStr = commandString;
    }

    public static TestCommand getCommand(String commandString) {
        for (TestCommand command : values()) {
            if (command.commandStr.equalsIgnoreCase(commandString)) {
                return command;
            }
        }
        return null;
    }
}
