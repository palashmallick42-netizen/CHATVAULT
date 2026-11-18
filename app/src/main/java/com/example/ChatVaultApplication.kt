package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer

/**
 * Application subclass exposing the lazy manual DI container.
 * Note: Firebase / App Check initialization intentionally remains in MainActivity.onCreate().
 */
class ChatVaultApplication : Application() {
    val container: AppContainer by lazy {
        DefaultAppContainer(this)
    }
}
