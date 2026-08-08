package com.cashsense.app.ui.pay

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.domain.PayeeKind
import com.cashsense.app.domain.UpiPayment
import com.cashsense.app.domain.UpiStatus
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

private data class UpiResultInfo(val title: String, val subtitle: String)

fun scanOptions(): ScanOptions = ScanOptions()
    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    .setPrompt("Scan a UPI QR code")
    .setBeepEnabled(false)
    .setOrientationLocked(true)

/**
 * Builds a standard UPI deep link and hands off to whichever UPI app the user has installed
 * to actually authenticate and move the money — CashSense never sees a PIN. Once that app
 * returns a result, the payment is recorded immediately with the exact outcome instead of
 * being guessed from a notification afterwards.
 */
@Composable
fun PayViaUpiDialog(
    repository: WalletRepository,
    onDismiss: () -> Unit,
    initialVpa: String = "",
    initialPayeeName: String = "",
    initialAmountPaise: Long? = null,
    initialPayeeKind: PayeeKind = PayeeKind.UNKNOWN
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var vpa by remember { mutableStateOf(initialVpa) }
    var payeeName by remember { mutableStateOf(initialPayeeName) }
    var payeeKind by remember { mutableStateOf(initialPayeeKind) }
    var overrideP2pBlock by remember { mutableStateOf(false) }
    var amountText by remember {
        mutableStateOf(
            initialAmountPaise?.let { paise ->
                val rupees = paise / 100.0
                if (rupees == rupees.toLong().toDouble()) rupees.toLong().toString() else rupees.toString()
            } ?: ""
        )
    }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<UpiResultInfo?>(null) }

    val transactionRef = remember { "CS" + System.currentTimeMillis().toString(36).uppercase() }

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val content = result.contents
        if (content != null) {
            val payload = UpiPayment.parseQrContent(content)
            if (payload.vpa != null) {
                vpa = payload.vpa
                payeeKind = if (payload.isMerchant) PayeeKind.MERCHANT else PayeeKind.PERSONAL
                overrideP2pBlock = false
                payload.payeeName?.let { payeeName = it }
                payload.amountPaise?.let { paise ->
                    val rupees = paise / 100.0
                    amountText = if (rupees == rupees.toLong().toDouble()) rupees.toLong().toString() else rupees.toString()
                }
                errorMessage = null
            } else {
                errorMessage = "Couldn't find a UPI ID in that QR code."
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            qrScanLauncher.launch(scanOptions())
        } else {
            errorMessage = "Camera permission is needed to scan a QR code."
        }
    }

    val upiPaymentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val response = activityResult.data?.getStringExtra("response")
        val parsed = UpiPayment.parseResponse(response)
        val amountPaise = amountText.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
        val payeeLabel = payeeName.ifBlank { vpa }

        when (parsed.status) {
            UpiStatus.SUCCESS -> {
                scope.launch { repository.addUpiPayment(amountPaise, payeeLabel, confirmed = true) }
                resultInfo = UpiResultInfo("Payment successful", "Recorded: paid to $payeeLabel")
            }
            UpiStatus.FAILURE -> {
                // A rejection on a payee we couldn't classify is very often the P2P-via-intent
                // rule rather than anything the user did wrong, and UPI apps report it as a
                // confusing "limit exceeded" — so name the likely cause instead of leaving them
                // to guess at a limit they haven't actually hit.
                val hint = if (payeeKind == PayeeKind.MERCHANT) {
                    "Nothing was recorded in your wallet."
                } else {
                    "Nothing was recorded in your wallet. If your UPI app mentioned a bank limit, " +
                        "that is usually misleading: since April 2024 UPI blocks person-to-person " +
                        "payments started from another app. Paying an individual works from your " +
                        "UPI app directly, and CashSense will still record it from the notification."
                }
                resultInfo = UpiResultInfo("Payment failed or cancelled", hint)
            }
            UpiStatus.SUBMITTED, UpiStatus.UNKNOWN -> {
                scope.launch { repository.addUpiPayment(amountPaise, payeeLabel, confirmed = false) }
                resultInfo = UpiResultInfo(
                    "Couldn't confirm the result",
                    "Added to Detected transactions on the Wallet screen — review it once you've checked."
                )
            }
        }
    }

    // Only a QR that positively identified a personal payee justifies warning up front. An
    // unknown (hand-typed) payee just attempts the payment; if UPI does reject it as P2P, the
    // failure message below explains why rather than pre-emptively nagging.
    val blockedAsP2p = payeeKind == PayeeKind.PERSONAL && !overrideP2pBlock

    fun startPayment() {
        val amount = amountText.toDoubleOrNull()
        when {
            vpa.isBlank() -> errorMessage = "Enter a UPI ID or scan a QR code."
            amount == null || amount <= 0.0 -> errorMessage = "Enter a valid amount."
            else -> {
                errorMessage = null
                val amountPaise = Math.round(amount * 100)
                val uri = UpiPayment.buildPaymentUri(
                    payeeVpa = vpa.trim(),
                    payeeName = payeeName.trim().ifBlank { vpa.trim() },
                    amountPaise = amountPaise,
                    note = note.ifBlank { null },
                    transactionRefId = transactionRef
                )
                try {
                    upiPaymentLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                } catch (e: ActivityNotFoundException) {
                    errorMessage = "No UPI app found on this device."
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(resultInfo?.title ?: "Pay via UPI") },
        text = {
            val info = resultInfo
            if (info != null) {
                Text(info.subtitle)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = vpa,
                            onValueChange = {
                                vpa = it
                                // Editing by hand discards whatever a scan had established, and a
                                // typed address says nothing about the payee either way.
                                payeeKind = PayeeKind.UNKNOWN
                                overrideP2pBlock = false
                            },
                            label = { Text("UPI ID") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                qrScanLauncher.launch(scanOptions())
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan QR code")
                        }
                    }
                    OutlinedTextField(
                        value = payeeName,
                        onValueChange = { payeeName = it },
                        label = { Text("Payee name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (blockedAsP2p && vpa.isNotBlank()) {
                        Text(
                            "This looks like a personal UPI ID rather than a shop. Since April 2024, " +
                                "UPI rules stop apps like CashSense from starting person-to-person " +
                                "payments — your UPI app rejects them with a misleading \"you have " +
                                "exceeded the bank limit\" message. Pay directly in your UPI app " +
                                "instead, and CashSense will record it from the payment notification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { overrideP2pBlock = true }) {
                            Text("Try paying anyway")
                        }
                    }
                    errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            when {
                resultInfo != null -> TextButton(onClick = onDismiss) { Text("Done") }
                blockedAsP2p && vpa.isNotBlank() -> TextButton(onClick = onDismiss) { Text("Got it") }
                else -> TextButton(onClick = { startPayment() }) { Text("Pay") }
            }
        },
        dismissButton = {
            if (resultInfo == null) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
