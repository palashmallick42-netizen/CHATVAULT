package com.example.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "chat_threads",
    indices = [
        Index(value = ["lastActivityAt"]),
        Index(value = ["isPinned"])
    ]
)
data class ChatThreadEntity(
    @PrimaryKey val chatKey: String,
    val packageName: String,
    val chatTitle: String,
    val isGroupChat: Boolean = false,
    val isPinned: Boolean = false,
    val firstCapturedAt: Long = System.currentTimeMillis(),
    val lastActivityAt: Long = System.currentTimeMillis(),
    val lastMessagePreview: String = "",
    val linkedPhoneNumber: String? = null
)

@Entity(
    tableName = "captured_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["chatKey"],
            childColumns = ["chatKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["chatKey", "timestamp"]),
        Index(value = ["isDeleted"]),
        Index(value = ["messageType"])
    ]
)
data class CapturedMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val chatKey: String,
    val packageName: String,
    val chatTitle: String,
    val senderName: String,
    val messageText: String,
    val messageType: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val notificationPostTime: Long = 0L,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val notificationKey: String = "",
    val linkedMediaId: Long? = null,
    val isTestDiagnostic: Boolean = false
)

data class ChatThreadWithMessages(
    @Embedded val thread: ChatThreadEntity,
    @Relation(
        parentColumn = "chatKey",
        entityColumn = "chatKey"
    )
    val messages: List<CapturedMessageEntity>
)

data class ChatSummary(
    val chatKey: String,
    val chatTitle: String,
    val packageName: String,
    val isGroupChat: Boolean,
    val isPinned: Boolean,
    val lastMessage: String,
    val lastTimestamp: Long,
    val totalCount: Int,
    val deletedCount: Int,
    val linkedPhoneNumber: String? = null
)

@Entity(
    tableName = "recovery_metadata_logs",
    indices = [Index(value = ["eventTimestamp"]), Index(value = ["eventType"])]
)
data class RecoveryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val eventType: RecoveryEventType,
    val chatKey: String? = null,
    val summary: String,
    val eventTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "recovered_media",
    indices = [
        Index(value = ["sourceUri"], unique = true),
        Index(value = ["isDeletedFromSource"])
    ]
)
data class RecoveredMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fileName: String,
    val sourceUri: String,
    val localVaultPath: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val capturedAt: Long = System.currentTimeMillis(),
    val isDeletedFromSource: Boolean = false,
    val deletedDetectedAt: Long? = null
)

@Entity(
    tableName = "ai_chat_messages",
    indices = [Index(value = ["sessionId", "timestamp"])]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sessionId: String = "default",
    val role: String, // "user" or "model"
    val text: String,
    val modelId: String,
    val timestamp: Long = System.currentTimeMillis()
)
