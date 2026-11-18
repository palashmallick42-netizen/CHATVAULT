package com.example.data.local

import androidx.room.TypeConverter

enum class MessageType {
    TEXT,
    PHOTO,
    VIDEO,
    AUDIO,
    DOCUMENT,
    STICKER,
    LOCATION,
    UNKNOWN
}

enum class RecoveryEventType {
    MESSAGE_CAPTURED,
    DELETION_CAUGHT,
    MEDIA_CACHED,
    MEDIA_RECOVERED
}

class VaultConverters {
    @TypeConverter
    fun fromMessageType(type: MessageType): String = type.name

    @TypeConverter
    fun toMessageType(value: String): MessageType =
        runCatching { MessageType.valueOf(value) }.getOrDefault(MessageType.TEXT)

    @TypeConverter
    fun fromRecoveryEventType(type: RecoveryEventType): String = type.name

    @TypeConverter
    fun toRecoveryEventType(value: String): RecoveryEventType =
        runCatching { RecoveryEventType.valueOf(value) }.getOrDefault(RecoveryEventType.MESSAGE_CAPTURED)
}
