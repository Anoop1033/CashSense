package com.cashsense.app.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cashsense.app.CashSenseApp
import com.cashsense.app.domain.TransactionTextParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens to all posted notifications system-wide (required by the Android API —
 * there is no way to subscribe to a subset of packages) but only ever acts on
 * ones that survive [TransactionTextParser]'s strict amount+keyword check.
 * Every match becomes a PENDING transaction that the user must confirm in the
 * app before it affects the wallet — nothing is applied silently.
 */
class UpiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val body = bigText ?: text

        val parsed = TransactionTextParser.parse(packageName, title, body) ?: return

        val app = application as CashSenseApp
        scope.launch {
            app.repository.addPendingFromNotification(parsed)
        }
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val expected = ComponentName(context, UpiNotificationListenerService::class.java).flattenToString()
            return flat.contains(expected)
        }
    }
}
