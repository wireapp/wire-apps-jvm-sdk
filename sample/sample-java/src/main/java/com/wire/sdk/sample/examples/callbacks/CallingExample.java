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

package com.wire.sdk.sample.examples.callbacks;

import com.wire.sdk.WireEventsHandlerDefault;
import com.wire.sdk.exception.WireException;
import com.wire.sdk.model.QualifiedId;
import com.wire.sdk.model.WireMessage;
import com.wire.sdk.model.calling.SubconversationEpochInfo;

/**
 * Register this handler with WireAppSdk and supply an app-owned AVS adapter.
 * The adapter must consume or copy epoch keys before updateEpoch returns.
 */
public final class CallingExample extends WireEventsHandlerDefault {
    public interface Engine {
        void receive(WireMessage.Calling message);
        void updateEpoch(SubconversationEpochInfo info);
        void left(QualifiedId conversationId);
    }

    private final Engine engine;

    public CallingExample(Engine engine) {
        this.engine = engine;
    }

    @Override
    public void onCallingMessageReceived(WireMessage.Calling message) {
        engine.receive(message);
    }

    @Override
    public void onSubconversationEpochChanged(SubconversationEpochInfo info) {
        try (info) {
            engine.updateEpoch(info);
        }
    }

    @Override
    public void onSubconversationLeft(QualifiedId conversationId) {
        engine.left(conversationId);
    }

    /** Join an incoming call's existing conference before starting media in the engine. */
    public void joinCall(QualifiedId conversationId) throws WireException {
        try (var info = getManager().joinSubconversation(conversationId)) {
            engine.updateEpoch(info);
        }
    }

    public void sendSignaling(QualifiedId conversationId, String content) {
        getManager().sendMessage(WireMessage.Calling.create(conversationId, content));
    }

    public void leaveCall(QualifiedId conversationId) throws WireException {
        getManager().leaveSubconversation(conversationId);
    }
}
