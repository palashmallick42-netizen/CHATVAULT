package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ai.configureAppCheck
import com.example.service.NotificationHelper
import com.example.ui.ChatVaultApp
import com.example.ui.theme.ChatVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before UI or ViewModel creation.
        configureAppCheck(this, intent)

        NotificationHelper.ensureChannels(this)
        enableEdgeToEdge()

        setContent {
            ChatVaultTheme {
                ChatVaultApp()
            }
        }
    }
}
