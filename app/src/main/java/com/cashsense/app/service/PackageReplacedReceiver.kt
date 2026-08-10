package com.cashsense.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restores notification detection after the app is updated.
 *
 * Replacing the app tears down its notification listener, and asking for the binding back from
 * [android.app.Application.onCreate] is not enough on its own: after an update the app is not
 * running, so nothing calls it. Until something happens to start the process — the user opening
 * the app, typically — payments go unrecorded while the permission still reads as granted. On a
 * test phone that swallowed a payment made four minutes after an update.
 *
 * This is the one signal Android delivers to an app purely because it was replaced, so it is the
 * only point where the binding can be restored without the user doing anything.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpiNotificationListenerService.requestRebindIfEnabled(context)
        }
    }
}
