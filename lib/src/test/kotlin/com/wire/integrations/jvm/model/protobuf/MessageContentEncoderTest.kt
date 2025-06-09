package com.wire.integrations.jvm.model.protobuf

import com.wire.integrations.jvm.model.QualifiedId
import com.wire.integrations.jvm.model.WireMessage
import com.wire.integrations.jvm.utils.toInternalHexString
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test

class MessageContentEncoderTest {
    @Test
    fun givenAMessageBodyWithEmoji_whenEncoding_ThenResultHasExpectedHexResult() =
        runTest {
            // given / when
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = textWithEmoji.first.first
            ).copy(timestamp = textWithEmoji.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.asHexString, textWithEmoji.second.first)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenAMessageBodyWithUrl_whenEncoding_ThenResultHasExpectedHexResult() =
        runTest {
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = url.first.first
            ).copy(timestamp = url.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.asHexString, url.second.first)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenAMessageBodyWithArabic_whenEncoding_ThenResultHasExpectedHexResult() =
        runTest {
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = arabic.first.first
            ).copy(timestamp = arabic.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.asHexString, arabic.second.first)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenAMessageBodyWithMarkDown_whenEncoding_ThenResultHasExpectedHexResult() =
        runTest {
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = markDown.first.first
            ).copy(timestamp = markDown.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.asHexString, markDown.second.first)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenAMessageBodyWithEmoji_whenEncoding_ThenResultHasExpectedSHA256HashResult() =
        runTest {
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = textWithEmoji.first.first
            ).copy(timestamp = textWithEmoji.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            // given / when
            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.sha256Digest.toInternalHexString(), textWithEmoji.second.second)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenAMessageBodyWithUrl_whenEncoding_ThenResultHasExpectedSHA256HashResult() =
        runTest {
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = url.first.first
            ).copy(timestamp = url.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.sha256Digest.toInternalHexString(), url.second.second)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenAMessageBodyWithArabic_whenEncoding_ThenResultHasExpectedSHA256HashResult() =
        runTest {
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = arabic.first.first
            ).copy(timestamp = arabic.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.sha256Digest.toInternalHexString(), arabic.second.second)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenAMessageBodyWithMarkDown_whenEncoding_ThenResultHasExpectedSHA256HashResult() =
        runTest {
            val originalMessage = WireMessage.Text.create(
                conversationId = CONVERSATION_ID,
                text = markDown.first.first
            ).copy(timestamp = markDown.first.second)

            val replyMessage = WireMessage.Text.createReply(
                conversationId = CONVERSATION_ID,
                text = DEFAULT_REPLY_TEXT,
                originalMessage = originalMessage
            )

            val result = MessageContentEncoder.encodeMessageContent(
                message = originalMessage
            )

            // then
            assertNotNull(replyMessage.quotedMessageSha256)
            assertNotNull(result)
            assertEquals(result.sha256Digest.toInternalHexString(), markDown.second.second)
            assertTrue(replyMessage.quotedMessageSha256.contentEquals(result.sha256Digest))
        }

    @Test
    fun givenALocationMessage_whenEncoding_ThenResultHasExpectedSHA256HashResult() =
        runTest {
            val (locationMessage, messageDate, expectedHash) = location
            val result = MessageContentEncoder.encodeMessageContent(
                message = locationMessage.copy(
                    timestamp = messageDate
                )
            )

            // then
            assertNotNull(result)
            assertEquals(expectedHash, result.sha256Digest.toInternalHexString())
        }

    private companion object {
        const val DEFAULT_REPLY_TEXT = "Default reply text"
        val CONVERSATION_ID = QualifiedId(
            id = UUID.randomUUID(),
            domain = UUID.randomUUID().toString()
        )

        val textWithEmoji =
            (
                "Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68" +
                    "\u200D\uD83D\uDC69\u200D\uD83D\uDC67!"
                    to Instant.parse("2018-10-22T15:09:29.000+02:00")
            ) to (
                "feff00480065006c006c006f0020d83ddc69200dd83ddcbbd83dd" +
                    "c68200dd83ddc69200dd83ddc670021000000005bcdcc09"
                    to "4f8ee55a8b71a7eb7447301d1bd0c8429971583b15a91594b45dee16f208afd5"
            )

        val url =
            (
                "https://www.youtube.com/watch?v=DLzxrzFCyOs" to
                    Instant.parse("2018-10-22T15:09:29.000+02:00")
            ) to (
                "feff00680074007400700073003a002f002f007700770077002e" +
                    "0079006f00750074007500620065002e0063006f006d002f007700610" +
                    "07400630068003f0076003d0044004c007a00780072007a0046004300" +
                    "79004f0073000000005bcdcc09" to
                    "ef39934807203191c404ebb3acba0d33ec9dce669f9acec49710d520c365b657"
            )

        val arabic =
            (
                "بغداد" to Instant.parse("2018-10-22T15:12:45.000+02:00")
            ) to (
                "feff0628063a062f0627062f000000005bcdcccd" to
                    "5830012f6f14c031bf21aded5b07af6e2d02d01074f137d106d4645e4dc539ca"
            )

        val markDown =
            (
                "This has **markdown**" to Instant.parse("2018-10-22T15:12:45.000+02:00")
            ) to (
                "feff005400680069007300200068006100730020002a" +
                    "002a006d00610072006b0064006f0077006e002a002a00000" +
                    "0005bcdcccd" to
                    "f25a925d55116800e66872d2a82d8292adf1d4177195703f976bc884d32b5c94"
            )

        val location = Triple(
            WireMessage.Location.create(
                conversationId = CONVERSATION_ID,
                latitude = 52.516666f,
                longitude = 13.4f,
                name = "someLocation",
                zoom = 10
            ),
            Instant.parse("2018-10-22T15:09:29.000+02:00"),
            "56a5fa30081bc16688574fdfbbe96c2eee004d1fb37dc714eec6efb340192816"
        )
    }
}
