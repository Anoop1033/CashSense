package com.cashsense.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.service.UpiNotificationListenerService
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: WalletRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }
    var listenerEnabled by remember { mutableStateOf(UpiNotificationListenerService.isEnabled(context)) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatic transaction detection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (listenerEnabled) {
                            "Notification access is ON. UPI and bank payment notifications will be parsed and queued for your confirmation."
                        } else {
                            "Notification access is OFF. Grant it so CashSense can detect UPI payments automatically."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text(if (listenerEnabled) "Manage notification access" else "Grant notification access")
                    }
                    TextButton(onClick = { listenerEnabled = UpiNotificationListenerService.isEnabled(context) }) {
                        Text("Refresh status")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reset wallet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Clears all transactions and takes you back to the starting-balance setup.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { showResetConfirm = true }) {
                        Text("Reset")
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset wallet?") },
            text = { Text("This deletes all transaction history and cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.resetWallet() }
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
