package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query(
        """
        SELECT
            t.chatKey AS chatKey,
            t.chatTitle AS chatTitle,
            t.packageName AS packageName,
            t.isGroupChat AS isGroupChat,
            t.isPinned AS isPinned,
            COALESCE(
                (SELECT cm.messageText FROM captured_messages cm
                 WHERE cm.chatKey = t.chatKey
                 ORDER BY cm.timestamp DESC, cm.id DESC LIMIT 1),
                t.lastMessagePreview
            ) AS lastMessage,
            COALESCE(MAX(m.timestamp), t.lastActivityAt) AS lastTimestamp,
            COUNT(m.id) AS totalCount,
            SUM(CASE WHEN m.isDeleted = 1 THEN 1 ELSE 0 END) AS deletedCount,
            t.linkedPhoneNumber AS linkedPhoneNumber
        FROM chat_threads t
        LEFT JOIN captured_messages m ON t.chatKey = m.chatKey
        GROUP BY t.chatKey
        ORDER BY t.isPinned DESC, lastTimestamp DESC
        """
    )
    fun observeChatSummaries(): Flow<List<ChatSummary>>

    @Transaction
    @Query("SELECT * FROM chat_threads WHERE chatKey = :chatKey LIMIT 1")
    fun observeThreadWithMessages(chatKey: String): Flow<ChatThreadWithMessages?>

    @Query("SELECT * FROM captured_messages WHERE chatKey = :chatKey ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForChat(chatKey: String): Flow<List<CapturedMessageEntity>>

    @Query("SELECT * FROM captured_messages ORDER BY timestamp DESC, id DESC")
    fun observeAllMessages(): Flow<List<CapturedMessageEntity>>

    @Query("SELECT * FROM captured_messages WHERE chatKey = :chatKey ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForChatOnce(chatKey: String): List<CapturedMessageEntity>

    @Query("SELECT * FROM captured_messages WHERE isDeleted = 1 ORDER BY deletedAt DESC, timestamp DESC")
    fun observeAllDeletedMessages(): Flow<List<CapturedMessageEntity>>

    @Query(
        """
        SELECT * FROM captured_messages
        WHERE messageText LIKE '%' || :query || '%'
           OR senderName LIKE '%' || :query || '%'
           OR chatTitle LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        """
    )
    fun searchMessages(query: String): Flow<List<CapturedMessageEntity>>

    @Query("SELECT COUNT(*) FROM captured_messages WHERE isDeleted = 1")
    fun observeDeletedMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM captured_messages")
    fun observeTotalMessageCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM captured_messages
        WHERE chatKey = :chatKey AND senderName = :senderName AND messageText = :messageText
          AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
        ORDER BY timestamp DESC LIMIT 1
        """
    )
    suspend fun findMatchingMessageInWindow(
        chatKey: String,
        senderName: String,
        messageText: String,
        minTimestamp: Long,
        maxTimestamp: Long
    ): CapturedMessageEntity?

    @Query(
        """
        SELECT * FROM captured_messages
        WHERE chatKey = :chatKey AND isDeleted = 0
          AND senderName = :senderName
          AND timestamp <= :maxTimestamp
        ORDER BY timestamp DESC, id DESC LIMIT 1
        """
    )
    suspend fun findLatestActiveMessageBySender(
        chatKey: String,
        senderName: String,
        maxTimestamp: Long
    ): CapturedMessageEntity?

    @Query(
        """
        SELECT * FROM captured_messages
        WHERE chatKey = :chatKey AND isDeleted = 0
          AND timestamp <= :maxTimestamp
        ORDER BY timestamp DESC, id DESC LIMIT 1
        """
    )
    suspend fun findLatestActiveMessageInChat(
        chatKey: String,
        maxTimestamp: Long = Long.MAX_VALUE
    ): CapturedMessageEntity?

    @Query("SELECT * FROM chat_threads WHERE chatKey = :chatKey LIMIT 1")
    suspend fun getThreadByKey(chatKey: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertThreadIfAbsent(thread: ChatThreadEntity): Long

    @Update
    suspend fun updateThread(thread: ChatThreadEntity)

    @Query(
        """
        UPDATE chat_threads
        SET lastActivityAt = :lastActivityAt,
            lastMessagePreview = :lastMessagePreview,
            chatTitle = :chatTitle,
            isGroupChat = :isGroupChat
        WHERE chatKey = :chatKey
        """
    )
    suspend fun refreshThreadMetadata(
        chatKey: String,
        chatTitle: String,
        isGroupChat: Boolean,
        lastActivityAt: Long,
        lastMessagePreview: String
    )

    @Query("UPDATE chat_threads SET isPinned = :pinned WHERE chatKey = :chatKey")
    suspend fun setThreadPinned(chatKey: String, pinned: Boolean)

    @Query("UPDATE chat_threads SET linkedPhoneNumber = :phone WHERE chatKey = :chatKey")
    suspend fun setThreadPhoneNumber(chatKey: String, phone: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: CapturedMessageEntity): Long

    @Transaction
    suspend fun upsertThreadAndInsertMessage(
        thread: ChatThreadEntity,
        message: CapturedMessageEntity
    ): Long {
        val insertedRow = insertThreadIfAbsent(thread)
        if (insertedRow == -1L) {
            refreshThreadMetadata(
                chatKey = thread.chatKey,
                chatTitle = thread.chatTitle,
                isGroupChat = thread.isGroupChat,
                lastActivityAt = thread.lastActivityAt,
                lastMessagePreview = thread.lastMessagePreview
            )
        }
        return insertMessage(message)
    }

    @Query("UPDATE captured_messages SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :id")
    suspend fun markMessageDeleted(id: Long, deletedAt: Long)

    @Query("DELETE FROM chat_threads WHERE chatKey = :chatKey")
    suspend fun deleteChatThread(chatKey: String)

    @Query("DELETE FROM chat_threads")
    suspend fun clearAllMessages()
}

@Dao
interface RecoveryMetadataDao {
    @Query("SELECT * FROM recovery_metadata_logs ORDER BY eventTimestamp DESC LIMIT :limit")
    fun observeRecentEvents(limit: Int = 50): Flow<List<RecoveryEventEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM recovery_metadata_logs
            WHERE eventType = 'DELETION_CAUGHT'
              AND chatKey = :chatKey
              AND eventTimestamp BETWEEN :minTimestamp AND :maxTimestamp
        )
        """
    )
    suspend fun hasDeletionEventInWindow(
        chatKey: String,
        minTimestamp: Long,
        maxTimestamp: Long
    ): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: RecoveryEventEntity): Long

    @Query("DELETE FROM recovery_metadata_logs")
    suspend fun clearEvents()
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM recovered_media ORDER BY capturedAt DESC, id DESC")
    fun observeAllMedia(): Flow<List<RecoveredMediaEntity>>

    @Query("SELECT * FROM recovered_media")
    suspend fun getAllMediaOnce(): List<RecoveredMediaEntity>

    @Query("SELECT COUNT(*) FROM recovered_media WHERE isDeletedFromSource = 1")
    fun observeRecoveredDeletedMediaCount(): Flow<Int>

    @Query("SELECT * FROM recovered_media WHERE sourceUri = :sourceUri LIMIT 1")
    suspend fun findBySourceUri(sourceUri: String): RecoveredMediaEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMedia(item: RecoveredMediaEntity): Long

    @Query(
        """
        UPDATE recovered_media
        SET isDeletedFromSource = 1, deletedDetectedAt = :detectedAt
        WHERE id = :id AND isDeletedFromSource = 0
        """
    )
    suspend fun markMediaDeletedFromSource(id: Long, detectedAt: Long)

    @Query("DELETE FROM recovered_media WHERE id = :id")
    suspend fun deleteMediaById(id: Long)
}

@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(sessionId: String = "default"): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesOnce(sessionId: String = "default"): List<AiChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AiChatMessageEntity): Long

    @Query("DELETE FROM ai_chat_messages WHERE sessionId = :sessionId")
    suspend fun clearSession(sessionId: String = "default")
}
