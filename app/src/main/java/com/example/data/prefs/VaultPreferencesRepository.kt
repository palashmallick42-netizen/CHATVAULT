package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_vault_prefs")

data class VaultPreferences(
    val linkedFolderUri: String? = null,
    val linkedFolderName: String? = null,
    val monitorWhatsApp: Boolean = true,
    val monitorWhatsAppBusiness: Boolean = true,
    val monitorDiagnosticChannel: Boolean = true,
    val notifyOnDeletedMessage: Boolean = true,
    val lastMediaSyncAt: Long = 0L
)

class VaultPreferencesRepository(private val context: Context) {

    private object Keys {
        val LINKED_FOLDER_URI = stringPreferencesKey("linked_folder_uri")
        val LINKED_FOLDER_NAME = stringPreferencesKey("linked_folder_name")
        val MONITOR_WHATSAPP = booleanPreferencesKey("monitor_whatsapp")
        val MONITOR_WHATSAPP_BUSINESS = booleanPreferencesKey("monitor_whatsapp_business")
        val MONITOR_DIAGNOSTIC = booleanPreferencesKey("monitor_diagnostic")
        val NOTIFY_ON_DELETED = booleanPreferencesKey("notify_on_deleted")
        val LAST_MEDIA_SYNC_AT = longPreferencesKey("last_media_sync_at")
    }

    val preferencesFlow: Flow<VaultPreferences> = context.vaultDataStore.data.map { prefs ->
        VaultPreferences(
            linkedFolderUri = prefs[Keys.LINKED_FOLDER_URI],
            linkedFolderName = prefs[Keys.LINKED_FOLDER_NAME],
            monitorWhatsApp = prefs[Keys.MONITOR_WHATSAPP] ?: true,
            monitorWhatsAppBusiness = prefs[Keys.MONITOR_WHATSAPP_BUSINESS] ?: true,
            monitorDiagnosticChannel = prefs[Keys.MONITOR_DIAGNOSTIC] ?: true,
            notifyOnDeletedMessage = prefs[Keys.NOTIFY_ON_DELETED] ?: true,
            lastMediaSyncAt = prefs[Keys.LAST_MEDIA_SYNC_AT] ?: 0L
        )
    }

    suspend fun getSnapshot(): VaultPreferences = preferencesFlow.first()

    suspend fun setLinkedFolder(uri: String?, displayName: String?) {
        context.vaultDataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(Keys.LINKED_FOLDER_URI)
                prefs.remove(Keys.LINKED_FOLDER_NAME)
            } else {
                prefs[Keys.LINKED_FOLDER_URI] = uri
                prefs[Keys.LINKED_FOLDER_NAME] = displayName ?: "WhatsApp Media"
            }
        }
    }

    suspend fun setMonitorWhatsApp(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.MONITOR_WHATSAPP] = enabled }
    }

    suspend fun setMonitorWhatsAppBusiness(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.MONITOR_WHATSAPP_BUSINESS] = enabled }
    }

    suspend fun setMonitorDiagnostic(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.MONITOR_DIAGNOSTIC] = enabled }
    }

    suspend fun setNotifyOnDeleted(enabled: Boolean) {
        context.vaultDataStore.edit { it[Keys.NOTIFY_ON_DELETED] = enabled }
    }

    suspend fun setLastMediaSyncAt(timestamp: Long) {
        context.vaultDataStore.edit { it[Keys.LAST_MEDIA_SYNC_AT] = timestamp }
    }
}
