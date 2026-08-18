package com.cashsense.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.service.UpiNotificationListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(repository: WalletRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }
    var listenerEnabled by remember { mutableStateOf(UpiNotificationListenerService.isEnabled(context)) }
    var listenerConnected by remember { mutableStateOf(UpiNotificationListenerService.isConnected) }
    var actualBalanceText by remember { mutableStateOf("") }
    var correctionMessage by remember { mutableStateOf<String?>(null) }
    val balancePaise by repository.balancePaise.collectAsState(initial = 0L)
    val detectionGap by repository.detectionGap.collectAsState(initial = null)
    var batteryExempt by remember {
        mutableStateOf(UpiNotificationListenerService.isExemptFromBatteryOptimisation(context))
    }

    // Re-check on every visit: the binding can drop while the screen is not in front of the user.
    LaunchedEffect(Unit) {
        UpiNotificationListenerService.requestRebindIfEnabled(context)
        repeat(4) {
            delay(700)
            listenerEnabled = UpiNotificationListenerService.isEnabled(context)
            listenerConnected = UpiNotificationListenerService.isConnected
            // Re-read on return from the system settings screen, so the warning clears itself
            // once the exemption has actually been granted.
            batteryExempt = UpiNotificationListenerService.isExemptFromBatteryOptimisation(context)
        }
    }
    val autoApply by repository.autoApplyDetected.collectAsState(initial = true)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // The screen ran out of room the moment a fourth card was added: without this the
                // overflow is simply clipped, with no way to reach the cards below the fold.
                .verticalScroll(rememberScrollState())
                // Keeps the keyboard from sitting over the balance field and its button, which is
                // the one place on this screen where something has to be typed.
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Automatic transaction detection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = when {
                            // Play requires a plain-language disclosure of what a sensitive
                            // permission reads and where it goes, shown before it is requested —
                            // not buried in the privacy policy.
                            !listenerEnabled ->
                                "Notification access is OFF. CashSense reads notifications to spot " +
                                    "payments as they happen.\n\n" +
                                    "Android does not let an app pick which notifications it receives, " +
                                    "so access covers all of them. CashSense discards everything that " +
                                    "is not from a payment app, a bank, or your SMS and email, and " +
                                    "keeps only the amount, direction and reference of a payment.\n\n" +
                                    "Nothing is logged and nothing leaves your phone — the app has no " +
                                    "internet access at all."
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

            // The single biggest cause of a payment going unseen, and the one with no trace: when
            // Android sleeps the app it unbinds the listener and nothing arrives at all.
            if (!batteryExempt) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Android can put CashSense to sleep", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "While it is asleep the app receives no notifications, so payments made " +
                                "in that time are missed with nothing to show for it. This is the " +
                                "usual reason a transaction never appears.\n\n" +
                                "Allow it to run unrestricted, then — on Samsung phones — also open " +
                                "Battery settings and remove CashSense from \"Sleeping apps\".",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = {
                            // The settings list, not the direct-request dialog: that one needs a
                            // permission Play restricts, and this reaches the same switch.
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            }.onFailure {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                )
                            }
                        }) {
                            Text("Let CashSense run in the background")
                        }
                    }
                }
            }

            detectionGap?.let { gap ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Detection was off for a while", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "CashSense was not receiving notifications between " +
                                "${formatMoment(gap.start)} and ${formatMoment(gap.endInclusive)}. " +
                                "Any payment made in that window was not recorded. Check your bank " +
                                "for that period, then use Correct balance below.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = { scope.launch { repository.dismissDetectionGap() } }) {
                            Text("Got it")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Correct balance", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The wallet adds up the payments it saw. If it ever drifts from what your " +
                            "bank says — a payment made while the phone was off, a message it could " +
                            "not read — put the real figure in here and the difference is recorded " +
                            "as a single correction.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Wallet currently shows ₹${formatRupees(balancePaise)}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = actualBalanceText,
                        onValueChange = {
                            actualBalanceText = it
                            correctionMessage = null
                        },
                        label = { Text("Balance your bank shows (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = actualBalanceText.toDoubleOrNull()?.let { it >= 0.0 } == true,
                        onClick = {
                            val target = actualBalanceText.toDoubleOrNull() ?: return@Button
                            val targetPaise = Math.round(target * 100)
                            scope.launch {
                                val delta = repository.correctBalanceTo(targetPaise)
                                correctionMessage = when {
                                    delta == 0L -> "Already matches — nothing to correct."
                                    delta > 0 -> "Added ₹${formatRupees(delta)} to match your bank."
                                    else -> "Removed ₹${formatRupees(-delta)} to match your bank."
                                }
                                actualBalanceText = ""
                            }
                        }
                    ) {
                        Text("Apply correction")
                    }
                    correctionMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
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

/**
 * Rupees with paise, grouped Indian-style. Paise are shown here even though the rest of the app
 * rounds to whole rupees: this screen exists to reconcile against a bank statement, and a figure
 * that quietly drops 80 paise is exactly the kind of drift it is meant to settle.
 */
private fun formatRupees(paise: Long): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return format.format(paise / 100.0)
}

/** A moment in the user's own time, precise to the minute — enough to find it on a statement. */
private fun formatMoment(millis: Long): String =
    SimpleDateFormat("d MMM, h:mm a", Locale("en", "IN")).format(Date(millis))
