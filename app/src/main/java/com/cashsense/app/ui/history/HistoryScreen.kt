package com.cashsense.app.ui.history

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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(repository: WalletRepository) {
    val transactions by repository.confirmedTransactions.collectAsState(initial = emptyList())

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
                items(transactions, key = { it.id }) { tx -> TransactionRow(tx) }
            }
        }
    }
}

private val dateFormat = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

@Composable
private fun TransactionRow(tx: Transaction) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
