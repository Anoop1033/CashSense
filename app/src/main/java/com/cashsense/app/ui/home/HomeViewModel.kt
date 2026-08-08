package com.cashsense.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cashsense.app.data.Transaction
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.domain.DenominationBreakdown
import com.cashsense.app.domain.DenominationStack
import com.cashsense.app.domain.StackDelta
import com.cashsense.app.domain.TransactionDirection
import com.cashsense.app.domain.WalletBreakdown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val breakdown: WalletBreakdown = WalletBreakdown(0, emptyList(), 0),
    val pending: List<Transaction> = emptyList(),
    val lastDeltas: List<StackDelta> = emptyList(),
    val lastChangePaise: Long = 0L,
    val changeEventId: Int = 0
)

class HomeViewModel(private val repository: WalletRepository) : ViewModel() {

    private var previousStacks: List<DenominationStack> = emptyList()
    private var previousBalance: Long? = null
    private var changeEventCounter = 0

    val uiState: StateFlow<HomeUiState> = combine(
        repository.balancePaise,
        repository.pendingTransactions
    ) { balancePaise, pending ->
        val breakdown = DenominationBreakdown.breakdown(balancePaise)
        val deltas = DenominationBreakdown.diff(previousStacks, breakdown.stacks)
            .filter { it.change != 0 }
        previousStacks = breakdown.stacks

        // null on the very first emission, so opening the app never fires a spurious "toast".
        val change = previousBalance?.let { balancePaise - it } ?: 0L
        if (change != 0L) changeEventCounter++
        previousBalance = balancePaise

        HomeUiState(
            breakdown = breakdown,
            pending = pending,
            lastDeltas = deltas,
            lastChangePaise = change,
            changeEventId = changeEventCounter
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun confirmPending(id: Long, amountPaise: Long, direction: TransactionDirection) {
        viewModelScope.launch {
            repository.confirmTransaction(id, amountPaise, direction, note = null)
        }
    }

    fun dismissPending(id: Long) {
        viewModelScope.launch { repository.dismissTransaction(id) }
    }

    fun addManual(amountPaise: Long, direction: TransactionDirection, note: String?) {
        viewModelScope.launch { repository.addManualTransaction(amountPaise, direction, note) }
    }
}

class HomeViewModelFactory(private val repository: WalletRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(repository) as T
    }
}
