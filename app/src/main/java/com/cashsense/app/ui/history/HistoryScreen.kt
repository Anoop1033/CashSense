package com.cashsense.app.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cashsense.app.data.Transaction
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.domain.TransactionDirection
import com.cashsense.app.ui.common.formatPaiseAsRupees
import com.cashsense.app.ui.theme.CreditGreen
import com.cashsense.app.ui.theme.DebitRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(repository: WalletRepository) {
    val transactions by repository.confirmedTransactions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pendingRemoval by remember { mutableStateOf<Transaction?>(null) }

    Scaffold { padding ->
        if (transactions.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No transactions yet", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions, key = { it.id }) { tx ->
                    TransactionRow(tx, onLongPress = { pendingRemoval = tx })
                }
            }
        }
    }

    pendingRemoval?.let { tx ->
        val isDebit = tx.direction == TransactionDirection.DEBIT
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove this transaction?") },
            text = {
                Text(
                    "${formatPaiseAsRupees(tx.amountPaise)} will be " +
                        (if (isDebit) "added back to" else "taken off") +
                        " your balance. Use this if CashSense read a notification wrongly."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { repository.dismissTransaction(tx.id) }
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            }
        )
    }
}

private val dateFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(tx: Transaction, onLongPress: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The label takes the leftover width and truncates; without this a long payee note
            // squeezes the amount column until it wraps one character per line.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.note ?: if (tx.source == "AUTO") (tx.sourcePackage ?: "Auto-detected") else "Manual entry",
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(tx.timestampMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            val isDebit = tx.direction == TransactionDirection.DEBIT
            Text(
                text = (if (isDebit) "- " else "+ ") + formatPaiseAsRupees(tx.amountPaise),
                fontWeight = FontWeight.Bold,
                color = if (isDebit) DebitRed else CreditGreen,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
