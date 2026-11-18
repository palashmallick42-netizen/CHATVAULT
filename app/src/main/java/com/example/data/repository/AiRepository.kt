package com.example.data.repository

import android.graphics.Bitmap
import com.example.data.local.AiChatDao
import com.example.data.local.AiChatMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout

enum class GeminiChatProfile(
    val modelId: String,
    val label: String,
    val badge: String,
    val roleTitle: String,
    val systemPrompt: String
) {
    GENERAL(
        modelId = "gemini-3.8-flash",
        label = "General",
        badge = "gemini-3.8-flash",
        roleTitle = "ChatVault Recovery Assistant",
        systemPrompt = "You are ChatVault Recovery Assistant, a helpful Android messaging and media recovery specialist. Help users understand how deleted WhatsApp messages and photos are recovered locally via NotificationListenerService and Storage Access Framework (SAF), translate or summarize recovered chats in English or Bengali/Banglish when asked, and keep answers concise, accurate, and privacy-respecting."
    ),
    FAST(
        modelId = "gemini-3.5-flash-lite",
        label = "Fast",
        badge = "gemini-3.5-flash-lite",
        roleTitle = "Quick Summary Bot",
        systemPrompt = "You are ChatVault Quick Summary Bot. Give ultra-fast, crisp, bulleted summaries, quick translations, and instant troubleshooting steps with minimal fluff."
    ),
    DEEP_REASONING(
        modelId = "gemini-4-argon",
        label = "Deep Reasoning",
        badge = "gemini-4-argon",
        roleTitle = "Forensic & Privacy Analyst",
        systemPrompt = "You are ChatVault Forensic & Privacy Analyst. Perform deep step-by-step reasoning on recovered message timelines, identify potential social engineering or scam indicators, reconstruct missing context from partial notification logs, and explain Android scoped-storage recovery mechanics thoroughly."
    )
}

enum class StudioImageSize(val label: String, val sdkSize: ImageSize) {
    SIZE_1K("1K", ImageSize.SIZE_1K),
    SIZE_2K("2K", ImageSize.SIZE_2K),
    SIZE_4K("4K", ImageSize.SIZE_4K)
}

enum class StudioAspectRatio(val label: String, val sdkRatio: AspectRatio) {
    SQUARE("1:1", AspectRatio.SQUARE_1x1),
    LANDSCAPE_4_3("4:3", AspectRatio.LANDSCAPE_4x3),
    LANDSCAPE_16_9("16:9", AspectRatio.LANDSCAPE_16x9),
    PORTRAIT_3_4("3:4", AspectRatio.PORTRAIT_3x4),
    PORTRAIT_9_16("9:16", AspectRatio.PORTRAIT_9x16)
}

class AiRepository(private val aiChatDao: AiChatDao) {

    fun observeChatHistory(sessionId: String = "default"): Flow<List<AiChatMessageEntity>> =
        aiChatDao.observeMessages(sessionId)

    suspend fun clearChatHistory(sessionId: String = "default") {
        aiChatDao.clearSession(sessionId)
    }

    suspend fun persistMessage(
        role: String,
        text: String,
        modelId: String,
        sessionId: String = "default"
    ) {
        aiChatDao.insertMessage(
            AiChatMessageEntity(
                sessionId = sessionId,
                role = role,
                text = text,
                modelId = modelId
            )
        )
    }

    suspend fun streamChatReply(
        profile: GeminiChatProfile,
        history: List<AiChatMessageEntity>,
        userMessage: String,
        onChunk: (String) -> Unit
    ): String {
        val model = Firebase.ai.generativeModel(
            modelName = profile.modelId,
            systemInstruction = content { text(profile.systemPrompt) }
        )

        val sdkHistory: List<Content> = sanitizeHistoryForGemini(history).map { item ->
            content(role = item.role) { text(item.text) }
        }

        val chat = model.startChat(history = sdkHistory)
        val accumulated = StringBuilder()

        withTimeout(60_000L) {
            chat.sendMessageStream(userMessage).collect { chunk ->
                val partText = chunk.text.orEmpty()
                if (partText.isNotEmpty()) {
                    accumulated.append(partText)
                    onChunk(accumulated.toString())
                }
            }
        }
        return accumulated.toString()
    }

    suspend fun generateStudioImage(
        prompt: String,
        imageSize: StudioImageSize,
        aspectRatio: StudioAspectRatio,
        referenceBitmap: Bitmap? = null
    ): Bitmap? {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(aspectRatio.sdkRatio)
                .setImageSize(imageSize.sdkSize)
                .build()
        }

        val model = Firebase.ai.generativeModel(
            modelName = IMAGE_MODEL_ID,
            generationConfig = config
        )

        return withTimeout(60_000L) {
            val response = if (referenceBitmap != null) {
                model.generateContent(
                    content {
                        image(referenceBitmap)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }

            response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
        }
    }

    suspend fun clarifyDeletedChats(messages: List<com.example.data.local.CapturedMessageEntity>): String {
        if (messages.isEmpty()) {
            return "No deleted messages are available to clarify yet."
        }
        val model = Firebase.ai.generativeModel(
            modelName = GeminiChatProfile.GENERAL.modelId,
            systemInstruction = content {
                text(
                    "You are ChatVault Message Clarifier. Read recovered and deleted WhatsApp chat messages, organize them chronologically by sender/chat, fix broken line wraps or shorthand, translate non-English/Banglish phrases into clear English while preserving original meaning, and output a clean, readable plain-text summary without inventing details."
                )
            }
        )
        val prioritized = messages
            .sortedWith(compareByDescending<com.example.data.local.CapturedMessageEntity> { it.isDeleted }.thenByDescending { it.timestamp })
            .take(80)
            .sortedBy { it.timestamp }
        val transcript = buildString {
            prioritized.forEach { msg ->
                val flag = if (msg.isDeleted) "[DELETED]" else "[ARCHIVED]"
                appendLine("$flag Chat: ${msg.chatTitle} | Sender: ${msg.senderName} | Text: ${msg.messageText}")
            }
        }
        val response = withTimeout(60_000L) {
            model.generateContent(
                "Please read these recovered WhatsApp messages and make them crystal clear and easy to follow:\n\n$transcript"
            )
        }
        return response.text?.takeIf { it.isNotBlank() }
            ?: "Unable to generate clarified summary from Gemini."
    }

    companion object {
        const val IMAGE_MODEL_ID = "gemini-nano-banana-2.1"

        /**
         * Ensures chat history passed to startChat() strictly alternates user/model,
         * starts with a user turn, and never ends on an unanswered user turn
         * (e.g. if a previous inference call failed).
         */
        fun sanitizeHistoryForGemini(history: List<AiChatMessageEntity>): List<AiChatMessageEntity> {
            val pairs = mutableListOf<AiChatMessageEntity>()
            var pendingUser: AiChatMessageEntity? = null
            for (msg in history) {
                if (msg.text.isBlank()) continue
                when (msg.role) {
                    "user" -> pendingUser = msg
                    "model" -> {
                        val u = pendingUser
                        if (u != null) {
                            pairs.add(u)
                            pairs.add(msg)
                            pendingUser = null
                        }
                    }
                }
            }
            return pairs
        }
    }
}
