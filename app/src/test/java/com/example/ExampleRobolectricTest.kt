package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CapturedMessageEntity
import com.example.data.local.ChatThreadEntity
import com.example.data.local.MessageType
import com.example.data.local.RecoveryEventEntity
import com.example.data.local.RecoveryEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches ChatVault`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ChatVault", appName)
    }

    @Test
    fun `app database singleton provider returns same instance`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = AppDatabase.getInstance(context)
        val second = com.example.data.local.DatabaseProvider.get(context)
        org.junit.Assert.assertSame(first, second)
        val container = com.example.di.DefaultAppContainer(context)
        org.junit.Assert.assertSame(first, container.database)
    }

    @Test
    fun `room schema stores thread metadata, messages, deletion state, and recovery logs`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.messageDao()
            val metadataDao = db.recoveryMetadataDao()
            val chatKey = "com.whatsapp|Rafi"

            val thread = ChatThreadEntity(
                chatKey = chatKey,
                packageName = "com.whatsapp",
                chatTitle = "Rafi",
                isGroupChat = false,
                firstCapturedAt = 1_700_000_000_000L,
                lastActivityAt = 1_700_000_000_000L,
                lastMessagePreview = "Meet at 6 PM near Dhanmondi"
            )
            val msg = CapturedMessageEntity(
                chatKey = chatKey,
                packageName = "com.whatsapp",
                chatTitle = "Rafi",
                senderName = "Rafi",
                messageText = "Meet at 6 PM near Dhanmondi",
                messageType = MessageType.TEXT,
                timestamp = 1_700_000_000_000L,
                notificationPostTime = 1_700_000_000_000L
            )

            val insertedId = dao.upsertThreadAndInsertMessage(thread, msg)
            dao.markMessageDeleted(insertedId, 1_700_000_050_000L)
            dao.setThreadPinned(chatKey, true)

            metadataDao.insertEvent(
                RecoveryEventEntity(
                    eventType = RecoveryEventType.DELETION_CAUGHT,
                    chatKey = chatKey,
                    summary = "Caught deleted message from Rafi",
                    eventTimestamp = 1_700_000_050_000L
                )
            )

            val relational = dao.observeThreadWithMessages(chatKey).first()
            assertNotNull(relational)
            assertEquals(1, relational!!.messages.size)
            assertTrue(relational.thread.isPinned)
            assertTrue(relational.messages.first().isDeleted)
            assertEquals(MessageType.TEXT, relational.messages.first().messageType)

            val summaries = dao.observeChatSummaries().first()
            assertEquals(1, summaries.size)
            assertEquals(1, summaries.first().deletedCount)
            assertTrue(summaries.first().isPinned)

            val searchHits = dao.searchMessages("Dhanmondi").first()
            assertEquals(1, searchHits.size)

            val events = metadataDao.observeRecentEvents().first()
            assertEquals(1, events.size)
            assertEquals(RecoveryEventType.DELETION_CAUGHT, events.first().eventType)

            // Verify CASCADE delete removes child messages when thread is deleted
            dao.deleteChatThread(chatKey)
            val afterDelete = dao.getMessagesForChatOnce(chatKey)
            assertTrue(afterDelete.isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `html report builder sanitizes markup and formats deleted messages clearly`() {
        val sample = listOf(
            CapturedMessageEntity(
                id = 1L,
                chatKey = "com.whatsapp|Rafi",
                packageName = "com.whatsapp",
                chatTitle = "Rafi",
                senderName = "Rafi <Admin>",
                messageText = "Secret <script>alert(1)</script> meeting at 6pm",
                messageType = MessageType.TEXT,
                timestamp = 1_700_000_000_000L,
                isDeleted = true,
                deletedAt = 1_700_000_050_000L
            )
        )
        val html = com.example.data.repository.HtmlReportBuilder.buildHtmlReport(
            reportTitle = "Deleted WhatsApp Chats",
            messages = sample,
            clarifiedSummary = "Rafi mentioned a meeting at 6pm."
        )
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("DELETED MESSAGE CAUGHT"))
        assertTrue(html.contains("AI CLARIFIED SUMMARY"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(!html.contains("<script>"))
    }

    @Test
    fun `whatsapp number lookup matches digits and links saved contacts`() = runTest {
        val repoMatch = com.example.data.repository.MessageVaultRepository.phoneDigitsMatch(
            "01712345678",
            "+880 1712-345678"
        )
        assertTrue(repoMatch)

        val mismatch = com.example.data.repository.MessageVaultRepository.phoneDigitsMatch(
            "01712345678",
            "+880 1999-000111"
        )
        assertTrue(!mismatch)

        assertEquals(
            "+880 1712-345678",
            com.example.service.WhatsAppNotificationListenerService.extractPhoneNumberIfTitleLooksLikePhone(
                "+880 1712-345678"
            )
        )
        assertEquals(
            null,
            com.example.service.WhatsAppNotificationListenerService.extractPhoneNumberIfTitleLooksLikePhone(
                "Rafi Ahmed"
            )
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.messageDao()
            val metadataDao = db.recoveryMetadataDao()
            val chatKey = "com.whatsapp|Rafi"
            dao.upsertThreadAndInsertMessage(
                ChatThreadEntity(
                    chatKey = chatKey,
                    packageName = "com.whatsapp",
                    chatTitle = "Rafi",
                    lastMessagePreview = "Hello"
                ),
                CapturedMessageEntity(
                    chatKey = chatKey,
                    packageName = "com.whatsapp",
                    chatTitle = "Rafi",
                    senderName = "Rafi",
                    messageText = "Hello"
                )
            )
            dao.setThreadPhoneNumber(chatKey, "+8801712345678")
            val summary = dao.observeChatSummaries().first().first()
            assertEquals("+8801712345678", summary.linkedPhoneNumber)

            // Verify sender-scoped active lookup and timestamp window dedup
            val now = 1_700_000_100_000L
            dao.insertMessage(
                CapturedMessageEntity(
                    chatKey = chatKey,
                    packageName = "com.whatsapp",
                    chatTitle = "Rafi",
                    senderName = "Rafi",
                    messageText = "Second msg",
                    timestamp = now
                )
            )
            val matched = dao.findMatchingMessageInWindow(
                chatKey = chatKey,
                senderName = "Rafi",
                messageText = "Second msg",
                minTimestamp = now - 5_000L,
                maxTimestamp = now + 5_000L
            )
            assertNotNull(matched)

            val activeBySender = dao.findLatestActiveMessageBySender(
                chatKey = chatKey,
                senderName = "Rafi",
                maxTimestamp = now + 2_000L
            )
            assertEquals("Second msg", activeBySender?.messageText)

            metadataDao.insertEvent(
                RecoveryEventEntity(
                    eventType = RecoveryEventType.DELETION_CAUGHT,
                    chatKey = chatKey,
                    summary = "Deletion caught",
                    eventTimestamp = now + 1_000L
                )
            )
            assertTrue(
                metadataDao.hasDeletionEventInWindow(
                    chatKey = chatKey,
                    minTimestamp = now - 2_000L,
                    maxTimestamp = now + 4_000L
                )
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun `gemini history sanitizer drops orphaned user and model turns`() {
        val raw = listOf(
            com.example.data.local.AiChatMessageEntity(id = 1, role = "model", text = "stray model", modelId = "m"),
            com.example.data.local.AiChatMessageEntity(id = 2, role = "user", text = "failed prompt", modelId = "m"),
            com.example.data.local.AiChatMessageEntity(id = 3, role = "user", text = "hello", modelId = "m"),
            com.example.data.local.AiChatMessageEntity(id = 4, role = "model", text = "hi there", modelId = "m"),
            com.example.data.local.AiChatMessageEntity(id = 5, role = "user", text = "another failed", modelId = "m")
        )
        val sanitized = com.example.data.repository.AiRepository.sanitizeHistoryForGemini(raw)
        assertEquals(2, sanitized.size)
        assertEquals("user", sanitized[0].role)
        assertEquals("hello", sanitized[0].text)
        assertEquals("model", sanitized[1].role)
        assertEquals("hi there", sanitized[1].text)
    }

    @Test
    fun `country prefix helper replaces existing prefix cleanly`() {
        assertEquals(
            "+911712345678",
            com.example.ui.screens.applyCountryPrefix("+8801712345678", "+91")
        )
        assertEquals(
            "+8801712345678",
            com.example.ui.screens.applyCountryPrefix("1712345678", "+880")
        )
        assertEquals(
            "+8801712345678",
            com.example.ui.screens.applyCountryPrefix("+8801712345678", "+880")
        )
    }
}
