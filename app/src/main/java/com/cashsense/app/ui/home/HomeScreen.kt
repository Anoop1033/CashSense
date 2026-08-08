package com.cashsense.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cashsense.app.data.Transaction
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.domain.PayeeKind
import com.cashsense.app.domain.TransactionDirection
import com.cashsense.app.domain.UpiPayment
import com.cashsense.app.ui.common.formatPaiseAsRupees
import com.cashsense.app.ui.pay.PayViaUpiDialog
import com.cashsense.app.ui.pay.scanOptions
import com.cashsense.app.ui.theme.CreditGreen
import com.cashsense.app.ui.theme.DebitRed
import com.journeyapps.barcodescanner.ScanContract
import kotlinx.coroutines.delay
import kotlin.math.abs

private data class PayPrefill(
    val vpa: String,
    val payeeName: String,
    val amountPaise: Long?,
    val payeeKind: PayeeKind
)

@Composable
fun HomeScreen(repository: WalletRepository) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModelFactory(repository))
    val state by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    var showManualDialog by remember { mutableStateOf<TransactionDirection?>(null) }
    var reviewingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showPayDialog by remember { mutableStateOf(false) }
    var payPrefill by remember { mutableStateOf<PayPrefill?>(null) }
    var toastAmountPaise by remember { mutableStateOf<Long?>(null) }

    val qrScanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val content = result.contents
        payPrefill = if (content != null) {
            val payload = UpiPayment.parseQrContent(content)
            PayPrefill(
                vpa = payload.vpa ?: "",
                payeeName = payload.payeeName ?: "",
                amountPaise = payload.amountPaise,
                payeeKind = when {
                    payload.vpa == null -> PayeeKind.UNKNOWN
                    payload.isMerchant -> PayeeKind.MERCHANT
                    else -> PayeeKind.PERSONAL
                }
            )
        } else {
            null
        }
        showPayDialog = true
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            qrScanLauncher.launch(scanOptions())
        } else {
            payPrefill = null
            showPayDialog = true
        }
    }
    fun launchQrScan() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            qrScanLauncher.launch(scanOptions())
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.changeEventId) {
        if (state.changeEventId > 0 && state.lastChangePaise != 0L) {
            toastAmountPaise = state.lastChangePaise
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(1400)
            toastAmountPaise = null
        }
    }

    val animatedBalance by animateFloatAsState(
        targetValue = state.breakdown.totalPaise.toFloat(),
        animationSpec = tween(durationMillis = 700),
        label = "balanceRoll"
    )

    Scaffold(
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FloatingActionButton(onClick = { showManualDialog = TransactionDirection.DEBIT }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Add expense")
                }
                FloatingActionButton(onClick = { launchQrScan() }) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = "Pay via UPI")
                }
                ExtendedFloatingActionButton(
                    onClick = { showManualDialog = TransactionDirection.CREDIT },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add money") }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 96.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Your balance", style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = formatPaiseAsRupees(animatedBalance.toLong()),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedVisibility(
                        visible = toastAmountPaise != null,
                        enter = slideInVertically { it / 2 } + fadeIn(),
                        exit = slideOutVertically { -it / 2 } + fadeOut()
                    ) {
                        val amount = toastAmountPaise ?: 0L
                        Text(
                            text = (if (amount > 0) "+" else "-") + formatPaiseAsRupees(abs(amount)),
                            color = if (amount > 0) CreditGreen else DebitRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            if (state.pending.isNotEmpty()) {
                item { Text("Detected transactions", style = MaterialTheme.typography.titleMedium) }
                items(state.pending, key = { it.id }) { tx ->
                    PendingTransactionCard(tx = tx, onClick = { reviewingTransaction = tx })
                }
            }

            item { Text("In your wallet", style = MaterialTheme.typography.titleMedium) }
            item {
                WalletGrid(
                    stacks = state.breakdown.stacks,
                    deltas = state.lastDeltas,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.breakdown.leftoverPaise > 0) {
                item {
                    Text(
                        "+ ${state.breakdown.leftoverPaise} paise not representable as coins",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    reviewingTransaction?.let { tx ->
        ConfirmTransactionDialog(
            transaction = tx,
            onConfirm = { amountPaise, direction ->
                viewModel.confirmPending(tx.id, amountPaise, direction)
                reviewingTransaction = null
            },
            onDismissTransaction = {
                viewModel.dismissPending(tx.id)
                reviewingTransaction = null
            },
            onCancel = { reviewingTransaction = null }
        )
    }

    showManualDialog?.let { direction ->
        ManualTransactionDialog(
            direction = direction,
            onConfirm = { amountPaise, note ->
                viewModel.addManual(amountPaise, direction, note)
                showManualDialog = null
            },
            onCancel = { showManualDialog = null }
        )
    }

    if (showPayDialog) {
        PayViaUpiDialog(
            repository = repository,
            initialVpa = payPrefill?.vpa ?: "",
            initialPayeeName = payPrefill?.payeeName ?: "",
            initialAmountPaise = payPrefill?.amountPaise,
            initialPayeeKind = payPrefill?.payeeKind ?: PayeeKind.UNKNOWN,
            onDismiss = {
                showPayDialog = false
                payPrefill = null
            }
        )
    }
}

@Composable
private fun PendingTransactionCard(tx: Transaction, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = formatPaiseAsRupees(tx.amountPaise),
                    fontWeight = FontWeight.Bold,
                    color = if (tx.direction == TransactionDirection.DEBIT) DebitRed else CreditGreen
                )
                Text(
                    text = tx.sourcePackage ?: "Unknown source",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onClick) { Text("Review") }
        }
    }
}

@Composable
private fun ConfirmTransactionDialog(
    transaction: Transaction,
    onConfirm: (Long, TransactionDirection) -> Unit,
    onDismissTransaction: () -> Unit,
    onCancel: () -> Unit
) {
    var amountText by remember { mutableStateOf((transaction.amountPaise / 100.0).let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    }) }
    var direction by remember { mutableStateOf(transaction.direction) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Confirm transaction") },
        text = {
            Column {
                Text(
                    text = transaction.rawText ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DirectionChip(
                        label = "Spent",
                        selected = direction == TransactionDirection.DEBIT,
                        onClick = { direction = TransactionDirection.DEBIT }
                    )
                    DirectionChip(
                        label = "Received",
                        selected = direction == TransactionDirection.CREDIT,
                        onClick = { direction = TransactionDirection.CREDIT }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rupees = amountText.toDoubleOrNull()
                if (rupees != null && rupees > 0) {
                    onConfirm(Math.round(rupees * 100), direction)
                }
            }) { Text("Confirm") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismissTransaction) { Text("Not a transaction") }
                TextButton(onClick = onCancel) { Text("Later") }
            }
        }
    )
}

@Composable
private fun ManualTransactionDialog(
    direction: TransactionDirection,
    onConfirm: (Long, String?) -> Unit,
    onCancel: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (direction == TransactionDirection.DEBIT) "Add expense" else "Add money") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val rupees = amountText.toDoubleOrNull()
                if (rupees != null && rupees > 0) {
                    onConfirm(Math.round(rupees * 100), note.ifBlank { null })
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}
