package com.kurogane.dividend.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Refreshes the opt-in dividend-change snapshot after the device has restarted. */
class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = NotificationStore(context).settings()
        NotificationScheduler.sync(context, settings)
        if (settings.dividendChangeEnabled) NotificationScheduler.enqueueDividendChangeRefresh(context)
    }
}
