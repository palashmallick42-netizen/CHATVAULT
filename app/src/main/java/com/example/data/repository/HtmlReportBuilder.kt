package com.example.data.repository

import android.text.TextUtils
import com.example.data.local.CapturedMessageEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HtmlReportBuilder {

    fun cleanReadableText(raw: String): String {
        return raw
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace(Regex("\\s+\\n"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun buildHtmlReport(
        reportTitle: String,
        messages: List<CapturedMessageEntity>,
        clarifiedSummary: String? = null,
        darkTheme: Boolean = true
    ): String {
        val fmt = SimpleDateFormat("MMM d, yyyy · hh:mm:ss a", Locale.getDefault())
        val generatedAt = fmt.format(Date())
        val deletedCount = messages.count { it.isDeleted }
        val uniqueChats = messages.map { it.chatKey }.distinct().size

        val bg = if (darkTheme) "#091013" else "#F3F8F6"
        val surface = if (darkTheme) "#0F191E" else "#FFFFFF"
        val surfaceAlt = if (darkTheme) "#17252C" else "#E6F0EC"
        val textPrimary = if (darkTheme) "#E7F2EE" else "#10201B"
        val textMuted = if (darkTheme) "#96B0A8" else "#4D665E"
        val accent = if (darkTheme) "#10E8A6" else "#008761"
        val deletedBg = if (darkTheme) "#381220" else "#FFE4E6"
        val deletedBorder = if (darkTheme) "#FB7185" else "#E11D48"
        val deletedText = if (darkTheme) "#FFE4E6" else "#881337"
        val border = if (darkTheme) "#2B424D" else "#BBD1C8"

        val safeTitle = TextUtils.htmlEncode(reportTitle)
        val safeGeneratedAt = TextUtils.htmlEncode(generatedAt)

        val aiSectionHtml = if (!clarifiedSummary.isNullOrBlank()) {
            val encodedSummary = TextUtils.htmlEncode(cleanReadableText(clarifiedSummary))
                .replace("\n", "<br/>")
            """
            <section class="ai-card">
                <div class="section-label">AI CLARIFIED SUMMARY</div>
                <div class="ai-body">$encodedSummary</div>
            </section>
            """.trimIndent()
        } else {
            ""
        }

        val grouped = messages.groupBy { it.chatTitle }
        val messagesHtml = if (messages.isEmpty()) {
            """
            <div class="empty-card">
                <h2>No Deleted Messages Found Yet</h2>
                <p>Once WhatsApp notifications are captured and a sender deletes a message, it will appear here in a clean, readable HTML view.</p>
            </div>
            """.trimIndent()
        } else {
            buildString {
                for ((chatTitle, chatMessages) in grouped) {
                    val safeChatTitle = TextUtils.htmlEncode(chatTitle)
                    appendLine("""<section class="chat-group">""")
                    appendLine("""<div class="chat-group-header">$safeChatTitle <span class="count-pill">${chatMessages.size} msg(s)</span></div>""")
                    for (msg in chatMessages) {
                        val safeSender = TextUtils.htmlEncode(msg.senderName)
                        val cleanBody = cleanReadableText(msg.messageText)
                        val safeBody = TextUtils.htmlEncode(cleanBody).replace("\n", "<br/>")
                        val safeTime = TextUtils.htmlEncode(fmt.format(Date(msg.timestamp)))
                        val safeType = TextUtils.htmlEncode(msg.messageType.name)
                        val cardClass = if (msg.isDeleted) "msg-card deleted" else "msg-card"

                        appendLine("""<article class="$cardClass">""")
                        appendLine("""<div class="msg-meta">""")
                        appendLine("""<span class="sender">$safeSender</span>""")
                        appendLine("""<span class="type-pill">$safeType</span>""")
                        appendLine("""<span class="time">$safeTime</span>""")
                        appendLine("""</div>""")
                        appendLine("""<div class="msg-body">$safeBody</div>""")
                        if (msg.isDeleted) {
                            val deletedAtStr = msg.deletedAt?.let { " · Deleted at ${fmt.format(Date(it))}" }.orEmpty()
                            val safeDeletedNote = TextUtils.htmlEncode("DELETED MESSAGE CAUGHT$deletedAtStr")
                            appendLine("""<div class="deleted-badge">$safeDeletedNote</div>""")
                        }
                        appendLine("""</article>""")
                    }
                    appendLine("""</section>""")
                }
            }
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>$safeTitle</title>
                <style>
                    * { box-sizing: border-box; }
                    body {
                        margin: 0;
                        padding: 16px;
                        background: $bg;
                        color: $textPrimary;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        line-height: 1.55;
                    }
                    .header-card {
                        background: $surface;
                        border: 1px solid $border;
                        border-radius: 16px;
                        padding: 18px;
                        margin-bottom: 16px;
                    }
                    .brand {
                        color: $accent;
                        font-size: 12px;
                        font-weight: 700;
                        letter-spacing: 0.08em;
                        text-transform: uppercase;
                        margin-bottom: 4px;
                    }
                    h1 {
                        margin: 0 0 6px 0;
                        font-size: 20px;
                        line-height: 1.25;
                    }
                    .timestamp {
                        color: $textMuted;
                        font-size: 12px;
                        margin-bottom: 12px;
                    }
                    .stats {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 8px;
                    }
                    .stat-pill {
                        background: $surfaceAlt;
                        border: 1px solid $border;
                        border-radius: 10px;
                        padding: 6px 10px;
                        font-size: 12px;
                        font-weight: 600;
                    }
                    .stat-pill.alert {
                        background: $deletedBg;
                        border-color: $deletedBorder;
                        color: $deletedText;
                    }
                    .ai-card {
                        background: $surface;
                        border: 1px solid $accent;
                        border-radius: 16px;
                        padding: 16px;
                        margin-bottom: 16px;
                    }
                    .section-label {
                        color: $accent;
                        font-size: 11px;
                        font-weight: 700;
                        letter-spacing: 0.08em;
                        margin-bottom: 8px;
                    }
                    .ai-body {
                        font-size: 14px;
                        color: $textPrimary;
                    }
                    .chat-group {
                        margin-bottom: 18px;
                    }
                    .chat-group-header {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        font-size: 15px;
                        font-weight: 700;
                        margin: 0 0 10px 4px;
                        color: $accent;
                    }
                    .count-pill {
                        font-size: 11px;
                        color: $textMuted;
                        background: $surfaceAlt;
                        padding: 3px 8px;
                        border-radius: 999px;
                    }
                    .msg-card {
                        background: $surface;
                        border: 1px solid $border;
                        border-radius: 14px;
                        padding: 14px;
                        margin-bottom: 10px;
                    }
                    .msg-card.deleted {
                        background: $deletedBg;
                        border-color: $deletedBorder;
                    }
                    .msg-meta {
                        display: flex;
                        flex-wrap: wrap;
                        align-items: center;
                        gap: 8px;
                        margin-bottom: 8px;
                    }
                    .sender {
                        font-weight: 700;
                        font-size: 14px;
                        color: $accent;
                    }
                    .type-pill {
                        font-size: 10px;
                        font-weight: 700;
                        padding: 2px 6px;
                        border-radius: 6px;
                        background: $surfaceAlt;
                        color: $textMuted;
                    }
                    .time {
                        margin-left: auto;
                        font-size: 11px;
                        color: $textMuted;
                    }
                    .msg-body {
                        font-size: 15px;
                        word-wrap: break-word;
                    }
                    .deleted-badge {
                        display: inline-block;
                        margin-top: 10px;
                        padding: 4px 8px;
                        border-radius: 8px;
                        font-size: 11px;
                        font-weight: 700;
                        background: rgba(251, 113, 133, 0.18);
                        color: $deletedText;
                        border: 1px solid $deletedBorder;
                    }
                    .empty-card {
                        background: $surface;
                        border: 1px solid $border;
                        border-radius: 16px;
                        padding: 24px;
                        text-align: center;
                        color: $textMuted;
                    }
                    .empty-card h2 {
                        color: $textPrimary;
                        margin-top: 0;
                        font-size: 17px;
                    }
                </style>
            </head>
            <body>
                <header class="header-card">
                    <div class="brand">ChatVault · Clear HTML Recovery View</div>
                    <h1>$safeTitle</h1>
                    <div class="timestamp">Generated: $safeGeneratedAt</div>
                    <div class="stats">
                        <span class="stat-pill">Chats: $uniqueChats</span>
                        <span class="stat-pill">Messages: ${messages.size}</span>
                        <span class="stat-pill alert">Deleted Caught: $deletedCount</span>
                    </div>
                </header>
                $aiSectionHtml
                $messagesHtml
            </body>
            </html>
        """.trimIndent()
    }
}
