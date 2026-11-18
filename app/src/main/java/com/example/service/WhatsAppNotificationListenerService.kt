package com.example.service

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.os.BundleCompat
import com.example.data.local.CapturedMessageEntity
import com.example.data.local.ChatThreadEntity
import com.example.data.local.DatabaseProvider
import com.example.data.local.MessageType
import com.example.data.local.RecoveryEventEntity
import com.example.data.local.RecoveryEventType
import com.example.data.prefs.VaultPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WhatsAppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class ExtractedPayload(
        val sender: String,
        val body: String,
        val messageTimeMs: Long
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val pkg = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val isDiagnostic = pkg == applicationContext.packageName &&
            subText == NotificationHelper.DIAGNOSTIC_TAG

        val isWhatsApp = pkg == WHATSAPP_PACKAGE || pkg == WHATSAPP_BUSINESS_PACKAGE
        if (!isWhatsApp && !isDiagnostic) return

        serviceScope.launch {
            try {
                val container = (applicationContext as? com.example.ChatVaultApplication)?.container
                val prefsRepo = container?.preferencesRepository
                    ?: VaultPreferencesRepository(applicationContext)
                val prefs = prefsRepo.getSnapshot()
                if (pkg == WHATSAPP_PACKAGE && !prefs.monitorWhatsApp) return@launch
                if (pkg == WHATSAPP_BUSINESS_PACKAGE && !prefs.monitorWhatsAppBusiness) return@launch
                if (isDiagnostic && !prefs.monitorDiagnosticChannel) return@launch

                val db = container?.database ?: DatabaseProvider.get(applicationContext)
                processNotification(db, sbn, extras, pkg, isDiagnostic, prefs.notifyOnDeletedMessage)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from $pkg", e)
            }
        }
    }

    private suspend fun processNotification(
        db: com.example.data.local.AppDatabase,
        sbn: StatusBarNotification,
        extras: Bundle,
        pkg: String,
        isDiagnostic: Boolean,
        notifyOnDeleted: Boolean
    ) {
        val dao = db.messageDao()
        val metadataDao = db.recoveryMetadataDao()

        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()?.trim().orEmpty()

        val chatTitle = conversationTitle ?: title.ifEmpty { "WhatsApp Chat" }
        val extractedMessages = extractMessagePayloads(extras, title, chatTitle)
        if (extractedMessages.isEmpty()) return

        val isGroupChat = (conversationTitle != null && !conversationTitle.equals(title, ignoreCase = true)) ||
            extractedMessages.any { !it.sender.equals(chatTitle, ignoreCase = true) }

        val chatKey = "$pkg|$chatTitle"
        val postTime = sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        for (payload in extractedMessages) {
            val cleanBody = payload.body.trim()
            if (cleanBody.isEmpty() || isNoiseMetaText(cleanBody)) continue

            val effectiveTime = payload.messageTimeMs.takeIf { it > 0L } ?: postTime

            if (isDeletionNotice(cleanBody)) {
                // Skip historical deletion notices re-delivered inside MessagingStyle EXTRA_MESSAGES
                if (payload.messageTimeMs > 0L &&
                    metadataDao.hasDeletionEventInWindow(
                        chatKey = chatKey,
                        minTimestamp = effectiveTime - 3_000L,
                        maxTimestamp = effectiveTime + 3_000L
                    )
                ) {
                    continue
                }
                val maxTargetTime = if (payload.messageTimeMs > 0L) effectiveTime + 2_000L else Long.MAX_VALUE
                val latestActive = dao.findLatestActiveMessageBySender(chatKey, payload.sender, maxTargetTime)
                    ?: dao.findLatestActiveMessageInChat(chatKey, maxTargetTime)

                if (latestActive != null) {
                    val deletedAt = if (payload.messageTimeMs > 0L) effectiveTime else System.currentTimeMillis()
                    dao.markMessageDeleted(latestActive.id, deletedAt)
                    metadataDao.insertEvent(
                        RecoveryEventEntity(
                            eventType = RecoveryEventType.DELETION_CAUGHT,
                            chatKey = chatKey,
                            summary = "Caught deleted message in $chatTitle from ${latestActive.senderName}",
                            eventTimestamp = deletedAt
                        )
                    )
                    if (notifyOnDeleted && !isDiagnostic) {
                        NotificationHelper.postDeletedAlert(
                            applicationContext,
                            chatTitle,
                            latestActive.messageText
                        )
                    }
                }
                continue
            }

            // If MessagingStyle supplied an exact message timestamp, dedup cleanly around that timestamp
            // so re-emitted older messages in EXTRA_MESSAGES never duplicate even hours later.
            val dedupHalfWindowMs = if (payload.messageTimeMs > 0L) 5_000L else 120_000L
            val existing = dao.findMatchingMessageInWindow(
                chatKey = chatKey,
                senderName = payload.sender,
                messageText = cleanBody,
                minTimestamp = effectiveTime - dedupHalfWindowMs,
                maxTimestamp = effectiveTime + dedupHalfWindowMs
            )
            if (existing != null) {
                continue
            }

            val messageType = inferMessageType(cleanBody)
            val inferredPhone = extractPhoneNumberIfTitleLooksLikePhone(chatTitle)
            val thread = ChatThreadEntity(
                chatKey = chatKey,
                packageName = pkg,
                chatTitle = chatTitle,
                isGroupChat = isGroupChat,
                firstCapturedAt = effectiveTime,
                lastActivityAt = effectiveTime,
                lastMessagePreview = cleanBody,
                linkedPhoneNumber = inferredPhone
            )
            val message = CapturedMessageEntity(
                chatKey = chatKey,
                packageName = pkg,
                chatTitle = chatTitle,
                senderName = payload.sender,
                messageText = cleanBody,
                messageType = messageType,
                timestamp = effectiveTime,
                notificationPostTime = sbn.postTime,
                isDeleted = false,
                notificationKey = sbn.key.orEmpty(),
                isTestDiagnostic = isDiagnostic
            )

            dao.upsertThreadAndInsertMessage(thread, message)
            metadataDao.insertEvent(
                RecoveryEventEntity(
                    eventType = RecoveryEventType.MESSAGE_CAPTURED,
                    chatKey = chatKey,
                    summary = "Archived ${messageType.name.lowercase()} message in $chatTitle",
                    eventTimestamp = effectiveTime
                )
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun extractMessagePayloads(
        extras: Bundle,
        fallbackTitle: String,
        chatTitle: String
    ): List<ExtractedPayload> {
        val results = mutableListOf<ExtractedPayload>()

        val rawMessages: Array<Parcelable>? = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!rawMessages.isNullOrEmpty()) {
            for (item in rawMessages) {
                val bundle = item as? Bundle ?: continue
                val text = bundle.getCharSequence("text")?.toString().orEmpty()
                if (text.isBlank()) continue

                val personName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    BundleCompat.getParcelable(bundle, "sender_person", Person::class.java)
                        ?.name?.toString()?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                val legacySender = bundle.getCharSequence("sender")?.toString()
                    ?.takeIf { it.isNotBlank() }
                val sender = personName ?: legacySender ?: fallbackTitle.ifBlank { chatTitle }
                val msgTime = bundle.getLong("time", 0L).coerceAtLeast(0L)
                results.add(ExtractedPayload(sender = sender, body = text, messageTimeMs = msgTime))
            }
        }

        if (results.isEmpty()) {
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val chosen = (bigText ?: text).orEmpty().trim()
            if (chosen.isNotBlank() && !isNoiseMetaText(chosen)) {
                val sender = if (fallbackTitle.isNotBlank()) fallbackTitle else chatTitle
                results.add(ExtractedPayload(sender = sender, body = chosen, messageTimeMs = 0L))
            } else {
                val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                if (!lines.isNullOrEmpty()) {
                    for (line in lines) {
                        val lineText = line?.toString()?.trim().orEmpty()
                        if (lineText.isBlank() || isNoiseMetaText(lineText)) continue
                        val colonIdx = lineText.indexOf(": ")
                        if (colonIdx in 1..40) {
                            val lineSender = lineText.substring(0, colonIdx).trim()
                            val lineBody = lineText.substring(colonIdx + 2).trim()
                            if (lineBody.isNotBlank()) {
                                results.add(ExtractedPayload(sender = lineSender, body = lineBody, messageTimeMs = 0L))
                            }
                        } else {
                            val sender = if (fallbackTitle.isNotBlank()) fallbackTitle else chatTitle
                            results.add(ExtractedPayload(sender = sender, body = lineText, messageTimeMs = 0L))
                        }
                    }
                }
            }
        }
        return results
    }

    private fun inferMessageType(body: String): MessageType {
        val lower = body.lowercase()
        return when {
            lower.startsWith("📷") || lower == "photo" || lower.contains("📷 photo") -> MessageType.PHOTO
            lower.startsWith("🎥") || lower == "video" || lower.contains("🎥 video") -> MessageType.VIDEO
            lower.startsWith("🎤") || lower.contains("voice message") || lower.contains("audio") -> MessageType.AUDIO
            lower.startsWith("📄") || lower.contains("document") || lower.endsWith(".pdf") -> MessageType.DOCUMENT
            lower.startsWith("🎭") || lower == "sticker" -> MessageType.STICKER
            lower.startsWith("📍") || lower.contains("live location") -> MessageType.LOCATION
            else -> MessageType.TEXT
        }
    }

    private fun isNoiseMetaText(text: String): Boolean {
        val lower = text.lowercase()
        return lower == "checking for new messages" ||
            lower == "whatsapp web is currently active" ||
            lower.matches(Regex("^\\d+ new messages?$")) ||
            lower.matches(Regex("^\\d+ messages? from \\d+ chats?$"))
    }

    private fun isDeletionNotice(text: String): Boolean {
        val lower = text.lowercase()
        return DELETION_PHRASES.any { phrase -> lower.contains(phrase) }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "WhatsAppListenerSvc"
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

        fun extractPhoneNumberIfTitleLooksLikePhone(title: String): String? {
            val trimmed = title.trim()
            if (trimmed.isEmpty()) return null
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length < 7) return null
            val nonPhoneChars = trimmed.count { !it.isDigit() && it !in setOf('+', '-', ' ', '(', ')') }
            return if (nonPhoneChars == 0) trimmed else null
        }

        private val DELETION_PHRASES = listOf(
            "this message was deleted",
            "you deleted this message",
            "message was deleted",
            "message deleted",
            "এই মেসেজটি মুছে ফেলা হয়েছে",
            "আপনি এই মেসেজটি মুছে ফেলেছেন",
            "यह संदेश मिटा दिया गया",
            "este mensaje fue eliminado",
            "ce message a été supprimé",
            "diese nachricht wurde gelöscht"
        )
    }
}
