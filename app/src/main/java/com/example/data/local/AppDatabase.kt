package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ChatThreadEntity::class,
        CapturedMessageEntity::class,
        RecoveryEventEntity::class,
        RecoveredMediaEntity::class,
        AiChatMessageEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(VaultConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun recoveryMetadataDao(): RecoveryMetadataDao
    abstract fun mediaDao(): MediaDao
    abstract fun aiChatDao(): AiChatDao

    companion object {
        private const val DATABASE_NAME = "chat_vault_db"

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_threads ADD COLUMN linkedPhoneNumber TEXT")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}

object DatabaseProvider {
    fun get(context: Context): AppDatabase = AppDatabase.getInstance(context)
}
