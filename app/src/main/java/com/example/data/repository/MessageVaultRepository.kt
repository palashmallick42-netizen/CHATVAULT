package com.example.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.local.CapturedMessageEntity
import com.example.data.local.ChatSummary
import com.example.data.local.ChatThreadWithMessages
import com.example.data.local.MessageDao
import com.example.data.local.RecoveryEventEntity
import com.example.data.local.RecoveryMetadataDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageVaultRepository(
    private val context: Context,
    private val messageDao: MessageDao,
    private val metadataDao: RecoveryMetadataDao? = null
) {
    val chatSummaries: Flow<List<ChatSummary>> = messageDao.observeChatSummaries()
    val deletedCount: Flow<Int> = messageDao.observeDeletedMessageCount()
    val totalCount: Flow<Int> = messageDao.observeTotalMessageCount()
    val deletedMessagesFeed: Flow<List<CapturedMessageEntity>> = messageDao.observeAllDeletedMessages()
    val allMessages: Flow<List<CapturedMessageEntity>> = messageDao.observeAllMessages()

    fun observeRecentEvents(limit: Int = 50): Flow<List<RecoveryEventEntity>>? =
        metadataDao?.observeRecentEvents(limit)

    fun observeThreadWithMessages(chatKey: String): Flow<ChatThreadWithMessages?> =
        messageDao.observeThreadWithMessages(chatKey)

    fun observeMessages(chatKey: String): Flow<List<CapturedMessageEntity>> =
        messageDao.observeMessagesForChat(chatKey)

    fun searchMessages(query: String): Flow<List<CapturedMessageEntity>> =
        messageDao.searchMessages(query)

    suspend fun getMessagesOnce(chatKey: String): List<CapturedMessageEntity> =
        messageDao.getMessagesForChatOnce(chatKey)

    suspend fun setThreadPinned(chatKey: String, pinned: Boolean) {
        messageDao.setThreadPinned(chatKey, pinned)
    }

    suspend fun setThreadPhoneNumber(chatKey: String, rawPhoneNumber: String?) {
        val cleaned = rawPhoneNumber?.trim()?.takeIf { it.isNotEmpty() }
        messageDao.setThreadPhoneNumber(chatKey, cleaned)
    }

    suspend fun deleteThread(chatKey: String) {
        messageDao.deleteChatThread(chatKey)
    }

    suspend fun clearAll() {
        messageDao.clearAllMessages()
        metadataDao?.clearEvents()
    }

    suspend fun shareHtmlReport(htmlContent: String, fileNameSlug: String): Intent =
        withContext(Dispatchers.IO) {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = fileNameSlug.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
            val file = File(exportsDir, "chatvault_${safeName}.html")
            file.writeText(htmlContent)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_SUBJECT, "ChatVault HTML Recovery Report")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

    suspend fun buildExportTranscript(chatKey: String, chatTitle: String): String {
        val thread = messageDao.getThreadByKey(chatKey)
        val messages = messageDao.getMessagesForChatOnce(chatKey)
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("ChatVault Recovered Transcript — $chatTitle")
            appendLine("Source: ${thread?.packageName ?: "WhatsApp"} | Group: ${thread?.isGroupChat ?: false}")
            appendLine("Exported: ${fmt.format(Date())}")
            appendLine("Total Messages: ${messages.size} | Deleted Caught: ${messages.count { it.isDeleted }}")
            appendLine("--------------------------------------------------")
            messages.forEach { msg ->
                val deletedTag = if (msg.isDeleted) " [DELETED]" else ""
                val typeTag = "[${msg.messageType.name}]"
                appendLine("[${fmt.format(Date(msg.timestamp))}] $typeTag$deletedTag ${msg.senderName}: ${msg.messageText}")
            }
        }
    }

    fun buildTranscriptForMessages(label: String, messages: List<CapturedMessageEntity>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return buildString {
            appendLine("ChatVault Recovered Transcript — $label")
            appendLine("Exported: ${fmt.format(Date())}")
            appendLine("Total Messages: ${messages.size} | Deleted Caught: ${messages.count { it.isDeleted }}")
            appendLine("--------------------------------------------------")
            messages.sortedBy { it.timestamp }.forEach { msg ->
                val deletedTag = if (msg.isDeleted) " [DELETED]" else ""
                val typeTag = "[${msg.messageType.name}]"
                appendLine("[${fmt.format(Date(msg.timestamp))}] (${msg.chatTitle}) $typeTag$deletedTag ${msg.senderName}: ${msg.messageText}")
            }
        }
    }

    fun buildOpenWhatsAppIntent(rawPhone: String): Intent? {
        val digits = normalizePhoneDigits(rawPhone)
        if (digits.length < 7) return null
        return Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://wa.me/$digits")
        )
    }

    companion object {
        fun normalizePhoneDigits(raw: String): String = raw.filter { it.isDigit() }

        fun phoneDigitsMatch(queryDigits: String, candidate: String?): Boolean {
            if (queryDigits.length < 4 || candidate.isNullOrBlank()) return false
            val candidateDigits = normalizePhoneDigits(candidate)
            if (candidateDigits.isEmpty()) return false
            if (candidateDigits.contains(queryDigits) || queryDigits.contains(candidateDigits)) {
                return candidateDigits.length >= 6 && queryDigits.length >= 6 ||
                    candidateDigits == queryDigits
            }
            if (queryDigits.length >= 7 && candidateDigits.length >= 7) {
                val tailLen = minOf(queryDigits.length, candidateDigits.length, 10)
                if (tailLen >= 7 && queryDigits.takeLast(tailLen) == candidateDigits.takeLast(tailLen)) {
                    return true
                }
            }
            return false
        }
    }

    suspend fun shareChatTranscript(chatKey: String, chatTitle: String): Intent =
        withContext(Dispatchers.IO) {
            val transcript = buildExportTranscript(chatKey, chatTitle)
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeName = chatTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32)
            val file = File(exportsDir, "chatvault_${safeName}.txt")
            file.writeText(transcript)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "ChatVault Export: $chatTitle")
                putExtra(Intent.EXTRA_TEXT, transcript)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
}
