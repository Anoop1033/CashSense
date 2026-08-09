package com.cashsense.app.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.service.UpiNotificationListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(repository: WalletRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }
    var listenerEnabled by remember { mutableStateOf(UpiNotificationListenerService.isEnabled(context)) }
    var listenerConnected by remember { mutableStateOf(UpiNotificationListenerService.isConnected) }

    // Re-check on every visit: the binding can drop while the screen is not in front of the user.
    LaunchedEffect(Unit) {
        UpiNotificationListenerService.requestRebindIfEnabled(context)
        repeat(4) {
            delay(700)
            listenerEnabled = UpiNotificationListenerService.isEnabled(context)
            listenerConnected = UpiNotificationListenerService.isConnected
        }
    }
    val autoApply by repository.autoApplyDetected.collectAsState(initial = true)

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
                        text = when {
                            !listenerEnabled ->
                                "Notification access is OFF. Grant it so CashSense can detect payments automatically."
                            // Permission granted but the system has not bound the listener —
                            // detection is dead even though everything looks fine.
                            !listenerConnected ->
                                "Notification access is granted, but CashSense is not receiving " +
                                    "notifications right now. This can happen after the app updates. " +
                                    "Tap Reconnect below."
                            else ->
                                "Working. UPI and bank payment notifications are read automatically."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text(if (listenerEnabled) "Manage notification access" else "Grant notification access")
                    }
                    if (listenerEnabled && !listenerConnected) {
                        OutlinedButton(onClick = {
                            UpiNotificationListenerService.requestRebindIfEnabled(context)
                            scope.launch {
                                // Binding is asynchronous; give it a moment before re-reading.
                                delay(1200)
                                listenerConnected = UpiNotificationListenerService.isConnected
                            }
                        }) {
                            Text("Reconnect")
                        }
                    }
                    TextButton(onClick = {
                        listenerEnabled = UpiNotificationListenerService.isEnabled(context)
                        listenerConnected = UpiNotificationListenerService.isConnected
                    }) {
                        Text("Refresh status")
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Update balance automatically",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoApply,
                            onCheckedChange = { scope.launch { repository.setAutoApplyDetected(it) } }
                        )
                    }
                    Text(
                        text = if (autoApply) {
                            "Detected payments go straight into your balance. Anything read wrongly can " +
                                "be removed from History — long-press a transaction."
                        } else {
                            "Detected payments wait on the Wallet screen for you to confirm before they " +
                                "change your balance."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
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
