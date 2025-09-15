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

package com.wire.sdk.calling

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.wire.sdk.calling.callbacks.AnsweredCallHandler
import com.wire.sdk.calling.callbacks.CallConfigRequestHandler
import com.wire.sdk.calling.callbacks.CloseCallHandler
import com.wire.sdk.calling.callbacks.ConstantBitRateStateChangeHandler
import com.wire.sdk.calling.callbacks.EstablishedCallHandler
import com.wire.sdk.calling.callbacks.IncomingCallHandler
import com.wire.sdk.calling.callbacks.LogHandler
import com.wire.sdk.calling.callbacks.MetricsHandler
import com.wire.sdk.calling.callbacks.MissedCallHandler
import com.wire.sdk.calling.callbacks.ReadyHandler
import com.wire.sdk.calling.callbacks.SFTRequestHandler
import com.wire.sdk.calling.callbacks.SendHandler
import com.wire.sdk.calling.callbacks.VideoReceiveStateHandler
import com.wire.sdk.calling.types.Handle
import com.wire.sdk.calling.types.Uint32Native

@Suppress(
    "FunctionNaming",
    "LongParameterList",
    "TooManyFunctions",
    "FunctionParameterNaming",
    "MaxLineLength",
    "MagicNumber"
)
interface CallingAvsClient : Library {
    fun wcall_create(
        userId: String,
        clientId: String,
        readyHandler: ReadyHandler,
        sendHandler: SendHandler,
        sftRequestHandler: SFTRequestHandler,
        incomingCallHandler: IncomingCallHandler,
        missedCallHandler: MissedCallHandler,
        answeredCallHandler: AnsweredCallHandler,
        establishedCallHandler: EstablishedCallHandler,
        closeCallHandler: CloseCallHandler,
        metricsHandler: MetricsHandler,
        callConfigRequestHandler: CallConfigRequestHandler,
        constantBitRateStateChangeHandler: ConstantBitRateStateChangeHandler,
        videoReceiveStateHandler: VideoReceiveStateHandler,
        arg: Pointer?
    ): Handle

    fun wcall_setup()

    fun wcall_setup_ex(flags: Int)

    fun wcall_run()

    fun wcall_start(
        inst: Handle,
        conversationId: String,
        callType: Int,
        convType: Int,
        audioCbr: Int
    ): Int

    fun wcall_answer(
        inst: Handle,
        conversationId: String,
        callType: Int,
        cbrEnabled: Boolean
    )

    fun wcall_audio_record(
        inst: Handle,
        userId: String,
        filePath: String
    )

    fun wcall_reject(
        inst: Handle,
        conversationId: String
    )

    fun wcall_config_update(
        inst: Handle,
        error: Int,
        jsonString: String
    )

    fun wcall_library_version(): String

    fun wcall_init(env: Int): Int

    fun wcall_set_log_handler(
        logHandler: LogHandler,
        arg: Pointer?
    )

    fun wcall_end(
        inst: Handle,
        conversationId: String
    )

    fun wcall_set_mute(
        inst: Handle,
        muted: Int
    )

    fun wcall_sft_resp(
        inst: Handle,
        error: Int,
        data: ByteArray,
        length: Int,
        ctx: Pointer?
    )

    fun wcall_recv_msg(
        inst: Handle,
        msg: ByteArray,
        len: Int,
        curr_time: Uint32Native,
        msg_time: Uint32Native,
        convId: String,
        userId: String,
        clientId: String,
        convType: Int
    ): Int

    fun wcall_resp(
        inst: Handle,
        status: Int,
        reason: String,
        arg: Pointer?
    ): Int

    fun wcall_request_video_streams(
        inst: Handle,
        conversationId: String,
        mode: Int,
        json: String
    )

    fun wcall_set_video_send_state(
        inst: Handle,
        conversationId: String,
        state: Int
    )

    fun wcall_set_clients_for_conv(
        inst: Handle,
        convId: String,
        clientsJson: String
    )

    fun wcall_set_epoch_info(
        inst: Handle,
        conversationId: String,
        epoch: Uint32Native,
        clientsJson: String,
        keyData: String
    ): Int

    fun wcall_process_notifications(
        inst: Handle,
        isStarted: Boolean
    )

    fun kcall_init(env: Int)

    fun kcall_close()

    fun kcall_set_local_user(
        userid: String,
        clientid: String
    )

    fun kcall_set_wuser(inst: Handle)

    fun kcall_preview_start()

    fun kcall_preview_stop()

    fun kcall_set_user_vidstate(
        convid: String,
        userid: String,
        clientid: String,
        state: Int
    )

    companion object {
        val INSTANCE: CallingAvsClient by lazy {
            Native.load(
                "avs",
                CallingAvsClient::class.java
            )!!
        }
    }
}
