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
    CREATE_ONE_TO_ONE_CONVERSATION("create-onetoone-conversation"),
    CREATE_GROUP_CONVERSATION("create-group-conversation"),
    DELETE_GROUP_CONVERSATION("delete-group-conversation"),
    LEAVE_GROUP_CONVERSATION("leave-group-conversation"),
    CREATE_CHANNEL_CONVERSATION("create-channel-conversation"),
    ADD_MEMBER_IN_CONVERSATION("add-members-to-conversation"),
    REMOVE_MEMBER_FROM_CONVERSATION("remove-members-from-conversation"),
    UPDATE_MEMBER_ROLE("update-member-role"),
    GET_USER_DATA("get-user-data"),
    GET_USERS("get-users"),
    GET_CONVERSATIONS("get-conversations"),
    GET_CONVERSATION_MEMBERS("get-conversation-members"),
    SEND_ASSET_IMAGE("send-asset-image"),
    SEND_ASSET_AUDIO("send-asset-audio"),
    SEND_ASSET_VIDEO("send-asset-video"),
    ASSET_PDF_DOCUMENT("asset-document-pdf"),
    SEARCH_USER("search-user"),
    TEST_DELETED_MESSAGE("test-deleted-message"),
    TEST_EDIT_TEXT("test-edit-text"),
    TEST_EDIT_COMPOSITE("test-edit-composite"),
    SEND_EPHEMERAL_TEXT("send-ephemeral-text"),
    SEND_EPHEMERAL_PING("send-ephemeral-ping"),
    SEND_COMPOSITE_MESSAGE("send-composite-message"),
    SEND_LOCATION_MESSAGE("send-location-message"),
    SEND_EPHEMERAL_LOCATION_MESSAGE("send-ephemeral-location-message");

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
