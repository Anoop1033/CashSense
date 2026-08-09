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
