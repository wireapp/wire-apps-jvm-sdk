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

package com.wire.sdk.model.protobuf

import com.google.protobuf.kotlin.toByteString
import com.wire.sdk.model.WireMessage
import com.wire.integrations.protobuf.messages.Messages

object MessageLinkPreviewMapper {
    fun fromProtobuf(linkPreview: Messages.LinkPreview): WireMessage.LinkPreview =
        WireMessage.LinkPreview(
            summary = linkPreview.summary,
            title = linkPreview.title,
            url = linkPreview.url,
            urlOffset = linkPreview.urlOffset,
            permanentUrl = linkPreview.permanentUrl,
            image = if (linkPreview.hasImage()) {
                WireMessage.LinkPreview.LinkPreviewAsset(
                    assetDataSize = linkPreview.image?.original?.size ?: 0,
                    mimeType = linkPreview.image?.original?.mimeType ?: "*/*",
                    assetName = linkPreview.image?.original?.name,
                    assetHeight = if (linkPreview.hasImage()) {
                        linkPreview.image.original.image.height
                    } else {
                        0
                    },
                    assetWidth = if (linkPreview.hasImage()) {
                        linkPreview.image.original.image.width
                    } else {
                        0
                    },
                    assetDataPath = null,
                    assetToken = linkPreview.image?.uploaded?.assetToken,
                    assetDomain = linkPreview.image?.uploaded?.assetDomain,
                    assetKey = linkPreview.image?.uploaded?.assetId
                )
            } else {
                null
            }
        )

    fun toProtobuf(linkPreview: WireMessage.LinkPreview): Messages.LinkPreview =
        Messages.LinkPreview.newBuilder()
            .setUrl(linkPreview.url)
            .setUrlOffset(linkPreview.urlOffset)
            .setPermanentUrl(linkPreview.permanentUrl)
            .setTitle(linkPreview.title)
            .setSummary(linkPreview.summary)
            .apply {
                linkPreview.image?.let { image ->
                    val original = Messages.Asset.Original.newBuilder()
                        .setMimeType(image.mimeType)
                        .setSize(image.assetDataSize)
                        .setImage(
                            Messages.Asset.ImageMetaData.newBuilder()
                                .setWidth(image.assetWidth)
                                .setHeight(image.assetHeight)
                                .build()
                        )
                        .setName(image.name)
                        .build()

                    val remoteData = Messages.Asset.RemoteData.newBuilder()
                        .setOtrKey(image.otrKey.toByteString())
                        .setSha256(image.sha256Key.toByteString())
                        .setAssetId(image.assetKey)
                        .setAssetToken(image.assetToken)
                        .setAssetDomain(image.assetDomain)
                        .setEncryption(
                            EncryptionAlgorithmMapper.toProtoBufModel(
                                encryptionAlgorithm = image.encryptionAlgorithm
                            )
                        )
                        .build()

                    setImage(
                        Messages.Asset.newBuilder()
                            .setOriginal(original)
                            .setUploaded(remoteData)
                            .build()
                    )
                }
            }
            .build()
}
