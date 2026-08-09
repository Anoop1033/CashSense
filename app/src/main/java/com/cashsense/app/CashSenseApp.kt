package com.cashsense.app

import android.app.Application
import com.cashsense.app.data.AppDatabase
import com.cashsense.app.data.WalletPrefs
import com.cashsense.app.data.WalletRepository
import com.cashsense.app.service.UpiNotificationListenerService

class CashSenseApp : Application() {

    val repository: WalletRepository by lazy {
        val db = AppDatabase.getInstance(this)
        WalletRepository(db.transactionDao(), WalletPrefs(this))
    }

    override fun onCreate() {
        super.onCreate()
        // Replacing the app unbinds its notification listener and the system does not reliably
        // bind it again, which leaves detection dead while the permission still reads as granted.
        // Asking on every start means an update repairs itself instead of needing the user to
        // notice that payments quietly stopped being recorded.
        UpiNotificationListenerService.requestRebindIfEnabled(this)
    }
}
