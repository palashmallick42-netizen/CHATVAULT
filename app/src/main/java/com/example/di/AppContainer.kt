package com.example.di

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.prefs.VaultPreferencesRepository
import com.example.data.repository.AiRepository
import com.example.data.repository.MediaVaultRepository
import com.example.data.repository.MessageVaultRepository

interface AppContainer {
    val database: AppDatabase
    val preferencesRepository: VaultPreferencesRepository
    val messageVaultRepository: MessageVaultRepository
    val mediaVaultRepository: MediaVaultRepository
    val aiRepository: AiRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext

    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(appContext)
    }

    override val preferencesRepository: VaultPreferencesRepository by lazy {
        VaultPreferencesRepository(appContext)
    }

    override val messageVaultRepository: MessageVaultRepository by lazy {
        MessageVaultRepository(
            context = appContext,
            messageDao = database.messageDao(),
            metadataDao = database.recoveryMetadataDao()
        )
    }

    override val mediaVaultRepository: MediaVaultRepository by lazy {
        MediaVaultRepository(
            context = appContext,
            mediaDao = database.mediaDao(),
            prefsRepo = preferencesRepository
        )
    }

    override val aiRepository: AiRepository by lazy {
        AiRepository(aiChatDao = database.aiChatDao())
    }
}
