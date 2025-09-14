/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.integrations.jvm.calling.callbacks.implementations

import com.sun.jna.Pointer
import com.wire.integrations.jvm.calling.CallingHttpClient
import com.wire.integrations.jvm.calling.CallingAvsClient
import com.wire.integrations.jvm.calling.callbacks.SFTRequestHandler
import com.wire.integrations.jvm.calling.types.AvsCallBackError
import com.wire.integrations.jvm.calling.types.AvsSFTError
import com.wire.integrations.jvm.calling.types.Handle
import com.wire.integrations.jvm.calling.types.SizeNative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class OnSFTRequest(
    private val handle: Deferred<Handle>,
    private val callingAvsClient: CallingAvsClient,
    private val callingHttpClient: CallingHttpClient,
    private val callingScope: CoroutineScope
) : SFTRequestHandler {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun onSFTRequest(
        ctx: Pointer?,
        url: String,
        data: Pointer?,
        length: SizeNative,
        arg: Pointer?
    ): Int {
        val dataString = data?.getString(0, UTF8_ENCODING)
        logger.info("[OnSFTRequest] -> Connecting to SFT Server $url with data $dataString")

        callingScope.launch {
            dataString?.let {
                val responseData = callingHttpClient.connectToSFT(
                    url = url,
                    data = dataString
                )

                onSFTResponse(data = responseData, context = ctx)
            }
        }

        logger.info("[OnSFTRequest] -> sftRequestHandler called")
        return AvsCallBackError.NONE.value
    }

    private suspend fun onSFTResponse(
        data: ByteArray?,
        context: Pointer?
    ) {
        logger.info("[OnSFTRequest] -> Sending SFT Response")
        val responseData = data ?: byteArrayOf()
        callingAvsClient.wcall_sft_resp(
            inst = handle.await(),
            error = data?.let { AvsSFTError.NONE.value } ?: AvsSFTError.NO_RESPONSE_DATA.value,
            data = responseData,
            length = responseData.size,
            ctx = context
        )
        logger.info("[OnSFTRequest] -> wcall_sft_resp() called")
    }

    private companion object {
        const val UTF8_ENCODING = "UTF-8"
    }
}
