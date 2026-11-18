package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.RecoveryEventEntity
import com.example.ui.components.RecoveryEventRow
import com.example.ui.viewmodel.VaultDashboardState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    dashboardState: VaultDashboardState,
    recentEvents: List<RecoveryEventEntity>,
    onBack: () -> Unit,
    onOpenListenerSettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRequestPostNotifications: () -> Unit,
    onLinkFolderClick: () -> Unit,
    onUnlinkFolderClick: () -> Unit,
    onToggleWhatsApp: (Boolean) -> Unit,
    onToggleWhatsAppBusiness: (Boolean) -> Unit,
    onToggleDiagnostic: (Boolean) -> Unit,
    onToggleDeletedAlerts: (Boolean) -> Unit,
    onSendTestMessageNotification: () -> Unit,
    onSendTestDeleteNotification: () -> Unit,
    onClearAllMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Device Recovery Permissions",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (dashboardState.isListenerEnabled) {
                                "Notification Listener: Active"
                            } else {
                                "Notification Listener: Disabled (Required to capture deleted WhatsApp messages)"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (dashboardState.isListenerEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tip: If you installed this APK directly on Android 13+ and Notification Access is greyed out, tap App Info -> ⋮ menu -> Allow restricted settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenListenerSettings,
                                modifier = Modifier.testTag("settings_listener_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Notification Access")
                            }
                            OutlinedButton(
                                onClick = onOpenAppInfo,
                                modifier = Modifier.testTag("settings_app_info_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("App Info")
                            }
                            if (!dashboardState.hasNotificationPermission) {
                                OutlinedButton(
                                    onClick = onRequestPostNotifications,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Allow Alerts")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Monitored Apps & Alerts",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingSwitchRow(
                            title = "WhatsApp (com.whatsapp)",
                            checked = dashboardState.preferences.monitorWhatsApp,
                            onCheckedChange = onToggleWhatsApp
                        )
                        SettingSwitchRow(
                            title = "WhatsApp Business (com.whatsapp.w4b)",
                            checked = dashboardState.preferences.monitorWhatsAppBusiness,
                            onCheckedChange = onToggleWhatsAppBusiness
                        )
                        SettingSwitchRow(
                            title = "Alert when deleted message is caught",
                            checked = dashboardState.preferences.notifyOnDeletedMessage,
                            onCheckedChange = onToggleDeletedAlerts
                        )
                        SettingSwitchRow(
                            title = "Capture Diagnostic Self-Test Channel",
                            checked = dashboardState.preferences.monitorDiagnosticChannel,
                            onCheckedChange = onToggleDiagnostic
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "WhatsApp Media SAF Folder",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dashboardState.preferences.linkedFolderName
                                ?: "No WhatsApp Media folder linked yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onLinkFolderClick,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Choose Folder")
                            }
                            if (dashboardState.preferences.linkedFolderUri != null) {
                                OutlinedButton(
                                    onClick = onUnlinkFolderClick,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Unlink")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Live Listener Diagnostic",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Post a real Android notification to test capture and deletion detection through NotificationListenerService.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onSendTestMessageNotification,
                                modifier = Modifier.testTag("settings_test_msg_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Send Test Msg")
                            }
                            OutlinedButton(
                                onClick = onSendTestDeleteNotification,
                                modifier = Modifier.testTag("settings_test_delete_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Send Delete Signal")
                            }
                        }
                    }
                }
            }

            if (recentEvents.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Recent Recovery Audit Log",
                                style = MaterialTheme.typography.titleMedium
                            )
                            recentEvents.take(5).forEach { event ->
                                RecoveryEventRow(event = event)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_clear_archive_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear All Archived Messages", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Entire Message Archive?") },
            text = { Text("All captured and deleted WhatsApp messages stored locally on this device will be permanently removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearAllMessages()
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
