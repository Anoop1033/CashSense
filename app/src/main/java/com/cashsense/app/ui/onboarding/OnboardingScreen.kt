package com.cashsense.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cashsense.app.data.WalletRepository
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(repository: WalletRepository) {
    var amountText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome to CashSense", style = MaterialTheme.typography.headlineMedium)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
            Text(
                text = "See your bank balance as currency notes, and watch it shrink " +
                    "when you spend — just like a physical wallet.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Your current balance (₹)") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
            Button(
                onClick = {
                    val rupees = amountText.toDoubleOrNull()
                    if (rupees != null && rupees >= 0) {
                        val paise = Math.round(rupees * 100)
                        scope.launch { repository.completeOnboarding(paise) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get started")
            }
        }
    }
}
