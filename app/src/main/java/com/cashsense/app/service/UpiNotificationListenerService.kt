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
 * Listens to all posted notifications system-wide — the Android API offers no way to subscribe to
 * a subset of packages — but discards everything that is not from a payment app, a bank, or a
 * carrier of bank alerts, and then everything that does not survive [TransactionTextParser]'s
 * amount-beside-verb check.
 *
 * What a surviving match does depends on how sure it is. A reading that corroborates itself, by
 * quoting the bank's reference or naming the account, goes straight into the balance when the user
 * has asked for that; anything less waits on the Wallet screen to be confirmed. Nothing read here
 * is logged or transmitted.
 */
class UpiNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        isConnected = true
    }

    /**
     * Android drops the binding whenever the app is replaced — an update from the Play Store is
     * enough — and does not always restore it. The permission still reads as granted, so nothing
     * looks wrong while every payment silently goes undetected. Asking for the binding back is
     * the documented remedy.
     */
    override fun onListenerDisconnected() {
        isConnected = false
        requestRebind(ComponentName(this, UpiNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == applicationContext.packageName) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val body = bigText ?: text

        // Notification text is read here and either turned into a transaction or dropped on the
        // spot. It is never logged and never leaves the device: this handler is the only place it
        // is looked at, and only an amount, a direction and the bank's reference survive it.
        val parsed = TransactionTextParser.parse(packageName, title, body) ?: return

        val app = application as CashSenseApp
        scope.launch {
            app.repository.addPendingFromNotification(parsed)
        }
    }

    companion object {
        /**
         * Whether the system currently has the listener bound. Distinct from [isEnabled], which
         * only reports that permission was granted — the two disagree exactly in the case that
         * breaks detection silently.
         */
        @Volatile
        var isConnected: Boolean = false
            private set

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: return false
            val expected = ComponentName(context, UpiNotificationListenerService::class.java).flattenToString()
            return flat.contains(expected)
        }

        /**
         * Nudges the system to bind the listener again. Safe to call at any time: it does nothing
         * unless permission has been granted, and is a no-op when already bound.
         */
        fun requestRebindIfEnabled(context: Context) {
            if (!isEnabled(context)) return
            runCatching {
                requestRebind(ComponentName(context, UpiNotificationListenerService::class.java))
            }
        }
    }
}
