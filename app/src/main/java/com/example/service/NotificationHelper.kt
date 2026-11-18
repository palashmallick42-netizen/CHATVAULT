package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object NotificationHelper {
    const val CHANNEL_DELETED_ALERTS = "vault_deleted_alerts"
    const val CHANNEL_DIAGNOSTIC_TEST = "vault_diagnostic_test"
    const val DIAGNOSTIC_TAG = "chatvault_diagnostic"

    fun isListenerEnabled(context: Context): Boolean {
        return NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun requestListenerRebindIfEnabled(context: Context) {
        if (isListenerEnabled(context)) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, WhatsAppNotificationListenerService::class.java)
                )
            }
        }
    }

    fun openListenerSettings(context: Context) {
        val primaryIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(primaryIntent)
        }.onFailure {
            openAppInfoSettings(context)
        }
    }

    fun openAppInfoSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun hasPostNotificationsPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val alertChannel = NotificationChannel(
                CHANNEL_DELETED_ALERTS,
                "Deleted Message Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when ChatVault catches a deleted WhatsApp message"
            }
            val diagnosticChannel = NotificationChannel(
                CHANNEL_DIAGNOSTIC_TEST,
                "Listener Self-Test",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Diagnostic notifications used to verify active NotificationListenerService capture"
            }
            manager.createNotificationChannel(alertChannel)
            manager.createNotificationChannel(diagnosticChannel)
        }
    }

    fun postDeletedAlert(context: Context, chatTitle: String, originalPreview: String) {
        if (!hasPostNotificationsPermission(context)) return
        ensureChannels(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            2001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_DELETED_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_chatvault)
            .setContentTitle("Deleted message caught from $chatTitle")
            .setContentText(originalPreview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(originalPreview))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context)
                .notify((System.currentTimeMillis() % 100_000).toInt() + 2000, notification)
        } catch (_: SecurityException) {
            // Permission may have been revoked between check and notify
        }
    }

    fun postDiagnosticTestNotification(context: Context, isDeleteSignal: Boolean) {
        if (!hasPostNotificationsPermission(context)) return
        ensureChannels(context)
        val text = if (isDeleteSignal) {
            "This message was deleted"
        } else {
            "Diagnostic check at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}: Live listener pipeline verified."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DIAGNOSTIC_TEST)
            .setSmallIcon(R.drawable.ic_stat_chatvault)
            .setContentTitle("Diagnostic Test Chat")
            .setContentText(text)
            .setSubText(DIAGNOSTIC_TAG)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(9901, notification)
        } catch (_: SecurityException) {
            // Permission missing
        }
    }
}
