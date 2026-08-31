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

package com.wire.sdk.sample

import com.wire.sdk.WireEventsHandlerSuspending
import com.wire.sdk.model.AssetResource
import com.wire.sdk.model.QualifiedId
import com.wire.sdk.model.TeamId
import com.wire.sdk.model.WireMessage
import com.wire.sdk.model.WireMessage.Asset.AssetMetadata
import com.wire.sdk.model.WireUser
import com.wire.sdk.model.asset.AssetRetention
import com.wire.sdk.model.http.conversation.ConversationRole
import kotlinx.coroutines.delay
import java.io.File
import java.util.*
import org.slf4j.LoggerFactory

class SampleEventsHandler : WireEventsHandlerSuspending() {
    private val logger = LoggerFactory.getLogger("SampleEventsHandler")

    override suspend fun onTextMessageReceived(wireMessage: WireMessage.Text) {
        logger.info("Received Text Message : $wireMessage")

        if (isAddMembersToConversation(text = wireMessage.text)) {
            processAddMembersToConversation(wireMessage = wireMessage)
            return
        }

        if (isRemoveMembersFromConversation(text = wireMessage.text)) {
            processRemoveMembersFromConversation(wireMessage = wireMessage)
            return
        }

        if (isUpdateMemberRole(text = wireMessage.text)) {
            processUpdateMemberRole(wireMessage = wireMessage)
            return
        }

        if (isGetUserData(text = wireMessage.text)) {
            processGetUserData(wireMessage = wireMessage)
            return
        }

        if (isGetUsers(text = wireMessage.text)) {
            processGetUsers(wireMessage = wireMessage)
            return
        }

        if (isGetConversations(text = wireMessage.text)) {
            processGetConversations(wireMessage = wireMessage)
            return
        }

        if (isGetConversationMembers(text = wireMessage.text)) {
            processGetConversationMembers(wireMessage = wireMessage)
            return
        }

        if (isCreateOneToOneConversation(text = wireMessage.text)) {
            processCreateOneToOneConversation(wireMessage = wireMessage)
            return
        }

        if (isCreateGroupConversation(text = wireMessage.text)) {
            processCreateGroupConversation(wireMessage = wireMessage)
            return
        }

        if (isLeaveGroupConversation(text = wireMessage.text)) {
            processLeaveGroupConversation(wireMessage = wireMessage)
            return
        }

        if (isDeleteGroupConversation(text = wireMessage.text)) {
            processDeleteGroupConversation(wireMessage = wireMessage)
            return
        }

        if (isCreateChannelConversation(text = wireMessage.text)) {
            processCreateChannelConversation(wireMessage = wireMessage)
            return
        }

        if (isSendAssetImage(text = wireMessage.text)) {
            processSendAssetImage(wireMessage = wireMessage)
            return
        }

        if (isSendAssetAudio(text = wireMessage.text)) {
            processSendAssetAudio(wireMessage = wireMessage)
            return
        }

        if (isSendAssetVideo(text = wireMessage.text)) {
            processSendAssetVideo(wireMessage = wireMessage)
            return
        }

        if (isAssetPDFDocumentTestMessage(text = wireMessage.text)) {
            replyWithSamplePDFDocument(wireMessage = wireMessage)
            return
        }

        if (isSearchUser(text = wireMessage.text)) {
            processSearchUser(wireMessage = wireMessage)
            return
        }

        if (isTestDeletedMessage(text = wireMessage.text)) {
            processTestDeletedMessage(wireMessage = wireMessage)
            return
        }

        if (isTestEditText(text = wireMessage.text)) {
            processTestEditText(wireMessage = wireMessage)
            return
        }

        if (isTestEditComposite(text = wireMessage.text)) {
            processTestEditComposite(wireMessage = wireMessage)
            return
        }

        if (isSendCompositeMessage(text = wireMessage.text)) {
            processSendCompositeMessage(wireMessage = wireMessage)
            return
        }

        if (isSendEphemeralText(text = wireMessage.text)) {
            processSendEphemeralText(wireMessage = wireMessage)
            return
        }

        if (isSendEphemeralPing(text = wireMessage.text)) {
            processSendEphemeralPing(wireMessage = wireMessage)
            return
        }

        if (isSendLocationMessage(text = wireMessage.text)) {
            processSendLocationMessage(wireMessage = wireMessage)
            return
        }

        if (isSendEphemeralLocationMessage(text = wireMessage.text)) {
            processSendEphemeralLocationMessage(wireMessage = wireMessage)
            return
        }

        // Sends an Ephemeral message if received message is Ephemeral
        wireMessage.expiresAfterMillis?.let {
            val ephemeralMessage = WireMessage.Text.create(
                conversationId = wireMessage.conversationId,
                text = "${wireMessage.text} -- Ephemeral Message sent from the Sample-Kotlin App",
                mentions = wireMessage.mentions,
                expiresAfterMillis = 10_000
            )

            manager.sendMessageSuspending(message = ephemeralMessage)
            return
        }

        val message = WireMessage.Text.createReply(
            text = "${wireMessage.text} -- Sent from the Sample-Kotlin App",
            mentions = wireMessage.mentions,
            originalMessage = wireMessage
        )

        // Sending a Read Receipt for the received message
        val receipt = WireMessage.Receipt.create(
            conversationId = wireMessage.conversationId,
            type = WireMessage.Receipt.Type.READ,
            messages = listOf(wireMessage.id.toString())
        )

        // Add a reaction emoji to the received message
        val reaction = WireMessage.Reaction.create(
            originalMessage = wireMessage,
            emojiSet = setOf("🧩")
        )

        manager.sendMessageSuspending(message = message)
        manager.sendMessageSuspending(message = receipt)
        manager.sendMessageSuspending(message = reaction)
    }

    override suspend fun onAssetMessageReceived(assetMessage: WireMessage.Asset) {
        logger.info("Received Asset Message : $assetMessage")

        val message = WireMessage.Text.createReply(
            text = "Received Asset : ${assetMessage.name}",
            originalMessage = assetMessage
        )

        manager.sendMessageSuspending(message = message)

        assetMessage.remoteData?.let { remoteData ->
            val asset = manager.downloadAssetSuspending(remoteData)
            val fileName = assetMessage.name
                .takeUnless { it.isNullOrBlank() }
                ?: "unknown-${UUID.randomUUID()}"
            val outputDir = File("build/downloaded_assets").apply { mkdirs() }
            val outputFile = File(outputDir, fileName)
            outputFile.writeBytes(asset.value)
            logger.info("Downloaded asset with size: ${asset.value.size} bytes, saved to: ${outputFile.absolutePath}")
        }
    }

    override suspend fun onButtonClicked(buttonAction: WireMessage.ButtonAction) {
        logger.info("Received ButtonAction Message : $buttonAction")
    }

    override suspend fun onPingReceived(pingMessage: WireMessage.Ping) {
        logger.info("Received Ping: $pingMessage")

        val ping = WireMessage.Ping.create(
            conversationId = pingMessage.conversationId
        )
        delay(10000L)
        logger.info("Sending back Ping: $pingMessage")
        manager.sendMessageSuspending(message = ping)
    }

    override suspend fun onLocationMessageReceived(locationMessage: WireMessage.Location) {
        logger.info("Received Location Message : $locationMessage")

        val message = WireMessage.Text.createReply(
            text = "Received Location\n\nLatitude: ${locationMessage.latitude}\n\nLongitude: ${locationMessage.longitude}\n\nName: ${locationMessage.name}\n\nZoom: ${locationMessage.zoom}",
            originalMessage = locationMessage
        )

        manager.sendMessageSuspending(message = message)
    }

    override suspend fun onMessageDeleted(deletedMessage: WireMessage.Deleted) {
        super.onMessageDeleted(deletedMessage)
    }

    override suspend fun onTeamMemberJoined(userId: QualifiedId, teamId: TeamId) {
        logger.info("Team member joined: userId=$userId, teamId=$teamId")
    }

    private fun getSampleAudioMetadata(): AssetMetadata.Audio {
        val base64Loudness = "/////////////////////////////////////8u+iP///8TCo///////l//////7" +
                "q3x6cXWAhIGOfn6KjouUi4SQlZGdkIeSm5OenoWFioqJnYZ/hIqOlJOIjZOanJSNkp2jqf///////" +
                "///////////////////////////////i3v///+ytIf/////1rfp/////8CWiHuDhYubk4SKi5GgnZ" +
                "COjJOlmpiQjJKmop6Jio2Pjp+MiYqKjpuQhIOFi5KUfoKKkJX/"

        return AssetMetadata.Audio(
            durationMs = 6000L,
            normalizedLoudness = Base64.getDecoder().decode(base64Loudness)
        )
    }

    private fun isAddMembersToConversation(text: String): Boolean =
        text.startsWith("add-members-to-conversation")

    private fun isRemoveMembersFromConversation(text: String): Boolean =
        text.startsWith("remove-members-from-conversation")

    private fun isUpdateMemberRole(text: String): Boolean =
        text.startsWith("update-member-role")

    private fun isGetUserData(text: String): Boolean =
        text.startsWith("get-user-data")

    private fun isGetUsers(text: String): Boolean =
        text.startsWith("get-users")

    private fun isGetConversations(text: String): Boolean =
        text.startsWith("get-conversations")

    private fun isGetConversationMembers(text: String): Boolean =
        text.startsWith("get-conversation-members")

    private fun isCreateOneToOneConversation(text: String): Boolean =
        text.startsWith("create-onetoone-conversation")

    private fun isCreateGroupConversation(text: String): Boolean =
        text.startsWith("create-group-conversation")

    private fun isLeaveGroupConversation(text: String): Boolean =
        text.startsWith("leave-group-conversation")

    private fun isDeleteGroupConversation(text: String): Boolean =
        text.startsWith("delete-group-conversation")

    private fun isCreateChannelConversation(text: String): Boolean =
        text.startsWith("create-channel-conversation")

    private fun isSendAssetImage(text: String): Boolean =
        text.startsWith("send-asset-image")

    private fun isSendAssetAudio(text: String): Boolean =
        text.startsWith("send-asset-audio")

    private fun isSendAssetVideo(text: String): Boolean =
        text.startsWith("send-asset-video")

    private fun isAssetPDFDocumentTestMessage(text: String): Boolean =
        text.startsWith("asset-document-pdf")

    private fun isSearchUser(text: String): Boolean =
        text.startsWith("search-user")

    private fun isTestDeletedMessage(text: String): Boolean =
        text.startsWith("test-deleted-message")

    private fun isTestEditText(text: String): Boolean =
        text.startsWith("test-edit-text")

    private fun isTestEditComposite(text: String): Boolean =
        text.startsWith("test-edit-composite")

    private fun isSendCompositeMessage(text: String): Boolean =
        text.startsWith("send-composite-message")

    private fun isSendEphemeralText(text: String): Boolean =
        text.startsWith("send-ephemeral-text")

    private fun isSendEphemeralPing(text: String): Boolean =
        text.startsWith("send-ephemeral-ping")

    private fun isSendLocationMessage(text: String): Boolean =
        text.startsWith("send-location-message")

    private fun isSendEphemeralLocationMessage(text: String): Boolean =
        text.startsWith("send-ephemeral-location-message")

    private suspend fun processAddMembersToConversation(wireMessage: WireMessage.Text) {
        // Expected message: `add-members-to-conversation [USER_ID] [DOMAIN]
        val split = wireMessage.text.split(" ")

        manager.addMembersToConversationSuspending(
            conversationId = wireMessage.conversationId,
            members = listOf(
                QualifiedId(
                    id = UUID.fromString(split[1]),
                    domain = split[2]
                )
            )
        )
    }

    private suspend fun processRemoveMembersFromConversation(wireMessage: WireMessage.Text) {
        // Expected message: `remove-members-from-conversation [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        val split = wireMessage.text.split(" ")
        val members = mutableListOf<QualifiedId>()

        // Start at index 1, increment by 2 to capture pairs of UUID and Domain
        for (i in 1 until split.size step 2) {
            // Basic check to ensure we have a Domain for the current UUID
            if (i + 1 < split.size) {
                members.add(QualifiedId(id = UUID.fromString(split[i]), domain = split[i + 1]))
            }
        }

        if (members.isNotEmpty()) {
            manager.removeMembersFromConversationSuspending(
                conversationId = wireMessage.conversationId,
                members = members
            )
        }
    }

    private suspend fun processUpdateMemberRole(wireMessage: WireMessage.Text) {
        // Expected message: `update-member-role [USER_ID] [DOMAIN] [ROLE]`
        val split = wireMessage.text.split(" ")
        if (split.size != 4) {
            sendText(wireMessage, "⚠️ Usage: update-member-role [USER_ID] [DOMAIN] [ROLE]")
            return
        }

        val newRole = ConversationRole.fromApi(split[3])
        if (newRole == ConversationRole.UNKNOWN) {
            sendText(
                wireMessage,
                "⚠️ Unknown role '${split[3]}'. Expected 'wire_admin' or 'wire_member'."
            )
            return
        }

        manager.updateConversationMemberRoleSuspending(
            conversationId = wireMessage.conversationId,
            userId = QualifiedId(id = UUID.fromString(split[1]), domain = split[2]),
            newRole = newRole
        )
    }

    private suspend fun processGetUserData(wireMessage: WireMessage.Text) {
        // Expected message: `get-user-data [USER_ID] [DOMAIN]`
        val split = wireMessage.text.split(" ")
        if (split.size != 3) {
            sendText(wireMessage, "⚠️ Usage: get-user-data [USER_ID] [DOMAIN]")
            return
        }

        val userId = QualifiedId(id = UUID.fromString(split[1]), domain = split[2])
        sendText(wireMessage, formatWireUser(manager.getUserSuspending(userId)))
    }

    private suspend fun processGetUsers(wireMessage: WireMessage.Text) {
        // Expected message: `get-users [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        val split = wireMessage.text.split(" ")
        if (split.size < 3 || split.size % 2 == 0) {
            sendText(wireMessage, "⚠️ Usage: get-users [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...")
            return
        }

        // The SDK has no bulk getter, so the users are fetched one by one
        val userIds = split.drop(1)
            .chunked(2)
            .filter { it.size == 2 }
            .map { (id, domain) -> QualifiedId(id = UUID.fromString(id), domain = domain) }

        val userDataList = userIds.map { userId -> formatWireUser(manager.getUserSuspending(userId)) }

        sendText(wireMessage, userDataList.joinToString("\n\n"))
    }

    private fun formatWireUser(user: WireUser): String =
        """
        👉 User data for ${user.id.toFullString()}:
                Name: ${user.name}
                Email: ${user.email ?: "N/A"}
                Handle: ${user.handle ?: "N/A"}
                Team: ${user.teamId ?: "N/A"}
                Deleted: ${user.deleted ?: false}
        """.trimIndent()

    private suspend fun processGetConversations(wireMessage: WireMessage.Text) {
        // Expected message: `get-conversations`
        val conversations = manager.getConversations()
        val conversationList = conversations.joinToString("\n") { conversation ->
            "- ${conversation.name ?: "Unnamed"} (${conversation.id.toFullString()})"
        }

        sendText(wireMessage, "Conversations (${conversations.size}):\n$conversationList")
    }

    private suspend fun processGetConversationMembers(wireMessage: WireMessage.Text) {
        // Expected message: `get-conversation-members [CONVERSATION_ID] [DOMAIN]`
        val split = wireMessage.text.split(" ")
        if (split.size != 3) {
            sendText(wireMessage, "⚠️ Usage: get-conversation-members [CONVERSATION_ID] [DOMAIN]")
            return
        }

        val conversationId = QualifiedId(id = UUID.fromString(split[1]), domain = split[2])
        val members = manager.getConversationMembers(conversationId)
        val memberList = members.joinToString("\n") { member ->
            "- ${member.userId.toFullString()} (${member.role})"
        }

        sendText(
            wireMessage,
            "Members in conversation ${conversationId.toFullString()} " +
                "(${members.size}):\n$memberList"
        )
    }

    private suspend fun sendText(wireMessage: WireMessage.Text, text: String) {
        manager.sendMessageSuspending(
            message = WireMessage.Text.create(
                conversationId = wireMessage.conversationId,
                text = text
            )
        )
    }

    private suspend fun processCreateOneToOneConversation(wireMessage: WireMessage.Text) {
        // Expected message: `create-onetoone-conversation [USER_ID] [DOMAIN]
        val split = wireMessage.text.split(" ")
        val one2oneUser = QualifiedId(
            id = UUID.fromString(split[1]),
            domain = split[2]
        )

        manager.createOneToOneConversationSuspending(one2oneUser)

        val newOne2OneConversation = manager.getConversations()
            .find { it.name == one2oneUser.toFullString()}

        manager.sendMessageSuspending(
            WireMessage.Text.create(
                conversationId = newOne2OneConversation?.id
                    ?: throw IllegalStateException("New one-to-one conversation not found"),
                text = "Hello! This is a message from the SDK in your new one-to-one conversation."
            )
        )
    }

    private suspend fun processCreateGroupConversation(wireMessage: WireMessage.Text) {
        // Expected message: `create-group-conversation [NAME] [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        val split = wireMessage.text.split(" ")

        val userIds = split.drop(2)
            .chunked(2)
            .filter { it.size == 2 }
            .map { (id, domain) -> QualifiedId(id = UUID.fromString(id), domain = domain) }

        val conversationId = manager.createGroupConversationSuspending(
            name = split[1],
            userIds = userIds
        )

        manager.updateConversationMemberRole(
            conversationId = conversationId,
            userId = wireMessage.sender,
            newRole = ConversationRole.ADMIN
        )
    }

    private suspend fun processLeaveGroupConversation(wireMessage: WireMessage.Text) {
        // Expected message: `leave-group-conversation`
        manager.leaveConversationSuspending(wireMessage.conversationId)
    }

    private suspend fun processDeleteGroupConversation(wireMessage: WireMessage.Text) {
        // Expected message: `delete-group-conversation`
        manager.deleteConversationSuspending(wireMessage.conversationId)
    }

    private suspend fun processCreateChannelConversation(wireMessage: WireMessage.Text) {
        // Expected message: `create-channel-conversation [NAME] [USER_ID] [DOMAIN] [USER_ID] [DOMAIN] ...`
        val split = wireMessage.text.split(" ")

        val userIds = split.drop(2)
            .chunked(2)
            .filter { it.size == 2 }
            .map { (id, domain) -> QualifiedId(id = UUID.fromString(id), domain = domain) }

        manager.createChannelConversationSuspending(
            name = split[1],
            userIds = userIds
        )
    }

    private suspend fun processSendAssetImage(wireMessage: WireMessage.Text) {
        val resourcePath = javaClass.classLoader.getResource("banana-icon.png")?.path
            ?: throw IllegalStateException("Test resource 'banana-icon.png' not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            name = asset.name,
            mimeType = "image/png",
            retention = AssetRetention.VOLATILE
        )
    }

    private suspend fun processSendAssetAudio(wireMessage: WireMessage.Text) {
        val resourcePath = javaClass.classLoader.getResource("sample_audio_6s.mp3")?.path
            ?: throw IllegalStateException("Test resource 'sample_audio_6s.mp3' not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            metadata = getSampleAudioMetadata(),
            name = asset.name,
            mimeType = "audio/mp3",
            retention = AssetRetention.VOLATILE
        )
    }

    private suspend fun processSendAssetVideo(wireMessage: WireMessage.Text) {
        val resourcePath = javaClass.classLoader.getResource("sample_video_5s.mp4")?.path
            ?: throw IllegalStateException("Test resource 'sample_video_5s.mp4' not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            metadata = AssetMetadata.Video(
                width = 1920,
                height = 1080,
                durationMs = 6000L
            ),
            name = asset.name,
            mimeType = "video/mp4",
            retention = AssetRetention.VOLATILE
        )
    }

    private suspend fun processSearchUser(wireMessage: WireMessage.Text) {
        // Expected message: `search-user [queryString]`
        val split = wireMessage.text.split(" ", limit = 2)
        if (split.size < 2 || split[1].isBlank()) {
            manager.sendMessageSuspending(
                WireMessage.Text.create(
                    conversationId = wireMessage.conversationId,
                    text = "⚠️ Usage: search-user [queryString]  (e.g. search-user alex)"
                )
            )
            return
        }

        val query = split[1].trim()
        val users = manager.searchUsersSuspending(
            query = query,
            domain = wireMessage.sender.domain,
            numberOfResults = 100
        )

        val sb = StringBuilder()
        sb.append("🔍 Search results for \"$query\" ")
            .append("(${users.size} found):\n\n")

        if (users.isEmpty()) {
            sb.append("No users found.")
        } else {
            users.forEach { user ->
                sb.append("👤 ${user.name}")
                user.handle?.let { sb.append(" (@$it)") }
                sb.append("\n   ID: ${user.id.id} @ ${user.id.domain}")
                user.teamId?.let { sb.append("\n   Team: $it") }
                user.email?.let { sb.append("\n   Email: $it") }
                sb.append("\n\n")
            }
        }

        manager.sendMessageSuspending(
            WireMessage.Text.create(
                conversationId = wireMessage.conversationId,
                text = sb.toString()
            )
        )
    }

    private suspend fun replyWithSamplePDFDocument(wireMessage: WireMessage.Text) {
        val fileName = "sample-pdf-1.pdf"
        val resourcePath = javaClass.classLoader.getResource(fileName)?.path
            ?: throw IllegalStateException("Test resource $fileName not found")
        val asset = File(resourcePath)
        val originalData = asset.readBytes()

        manager.sendAssetSuspending(
            conversationId = wireMessage.conversationId,
            asset = AssetResource(originalData),
            metadata = null,
            name = asset.name,
            mimeType = "application/pdf",
            retention = AssetRetention.VOLATILE
        )
    }

    private suspend fun processTestDeletedMessage(wireMessage: WireMessage.Text) {
        // Expected message: `test-deleted-message`
        // Sends a text message and then deletes it after 3 seconds.
        val message = WireMessage.Text.create(
            conversationId = wireMessage.conversationId,
            text = "This message will be deleted in 3 seconds"
        )

        val messageId = manager.sendMessageSuspending(message = message)

        delay(MESSAGE_UPDATE_DELAY_MILLIS)

        manager.sendMessageSuspending(
            message = WireMessage.Deleted.create(
                conversationId = wireMessage.conversationId,
                messageId = messageId
            )
        )
    }

    private suspend fun processTestEditText(wireMessage: WireMessage.Text) {
        // Expected message: `test-edit-text`
        // Sends a text message and then edits it after 3 seconds.
        val messageId = manager.sendMessageSuspending(
            message = WireMessage.Text.create(
                conversationId = wireMessage.conversationId,
                text = "This message will be edited in 3 seconds"
            )
        )

        delay(MESSAGE_UPDATE_DELAY_MILLIS)

        manager.sendMessageSuspending(
            message = WireMessage.TextEdited.create(
                replacingMessageId = messageId,
                conversationId = wireMessage.conversationId,
                text = "This message got edited"
            )
        )
    }

    private suspend fun processTestEditComposite(wireMessage: WireMessage.Text) {
        // Expected message: `test-edit-composite`
        // Sends a composite message and then removes its buttons one by one, every 3 seconds.
        val buttons = mutableListOf(
            WireMessage.Button(text = "Button item that will be removed in 6 seconds"),
            WireMessage.Button(text = "Button item that will be removed in 3 seconds")
        )

        var latestMessageId = manager.sendMessageSuspending(
            message = WireMessage.Composite.create(
                conversationId = wireMessage.conversationId,
                text = COMPOSITE_TITLE,
                buttonList = buttons.toList()
            )
        )

        while (buttons.isNotEmpty()) {
            delay(MESSAGE_UPDATE_DELAY_MILLIS)
            buttons.removeAt(buttons.lastIndex)

            latestMessageId = manager.sendMessageSuspending(
                message = WireMessage.CompositeEdited.create(
                    replacingMessageId = latestMessageId,
                    conversationId = wireMessage.conversationId,
                    text = COMPOSITE_TITLE,
                    buttonList = buttons.toList()
                )
            )
        }
    }

    private suspend fun processSendCompositeMessage(wireMessage: WireMessage.Text) {
        // Expected message: `send-composite-message`
        manager.sendMessageSuspending(
            message = WireMessage.Composite.create(
                conversationId = wireMessage.conversationId,
                text = COMPOSITE_TITLE,
                buttonList = listOf(
                    WireMessage.Button(text = "Button-001"),
                    WireMessage.Button(text = "Button-002")
                )
            )
        )
    }

    private suspend fun processSendEphemeralText(wireMessage: WireMessage.Text) {
        // Expected message: `send-ephemeral-text`
        manager.sendMessageSuspending(
            message = WireMessage.Text.create(
                conversationId = wireMessage.conversationId,
                text = "This is an Ephemeral Text message",
                expiresAfterMillis = EPHEMERAL_MSG_EXPIRE_MILLIS
            )
        )
    }

    private suspend fun processSendEphemeralPing(wireMessage: WireMessage.Text) {
        // Expected message: `send-ephemeral-ping`
        manager.sendMessageSuspending(
            message = WireMessage.Ping.create(
                conversationId = wireMessage.conversationId,
                expiresAfterMillis = EPHEMERAL_MSG_EXPIRE_MILLIS
            )
        )
    }

    private suspend fun processSendLocationMessage(wireMessage: WireMessage.Text) {
        // Expected message: `send-location-message`
        manager.sendMessageSuspending(
            message = WireMessage.Location.create(
                conversationId = wireMessage.conversationId,
                latitude = 52.52527f,
                longitude = 13.36923f,
                name = "Berlin Hauptbahnhof, 10557 Berlin",
                zoom = 50
            )
        )
    }

    private suspend fun processSendEphemeralLocationMessage(wireMessage: WireMessage.Text) {
        // Expected message: `send-ephemeral-location-message`
        manager.sendMessageSuspending(
            message = WireMessage.Location.create(
                conversationId = wireMessage.conversationId,
                latitude = 52.51615f,
                longitude = 13.37827f,
                name = "Pariser Platz, 10117 Berlin",
                zoom = 50,
                expiresAfterMillis = EPHEMERAL_MSG_EXPIRE_MILLIS
            )
        )
    }

    private companion object {
        private const val EPHEMERAL_MSG_EXPIRE_MILLIS = 10_000L
        private const val MESSAGE_UPDATE_DELAY_MILLIS = 3_000L
        private const val COMPOSITE_TITLE = "Composite Title"
    }
}
