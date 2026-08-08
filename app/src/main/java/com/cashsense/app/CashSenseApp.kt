package com.cashsense.app

import android.app.Application
import com.cashsense.app.data.AppDatabase
import com.cashsense.app.data.WalletPrefs
import com.cashsense.app.data.WalletRepository

class CashSenseApp : Application() {

    val repository: WalletRepository by lazy {
        val db = AppDatabase.getInstance(this)
        WalletRepository(db.transactionDao(), WalletPrefs(this))
    }
}
