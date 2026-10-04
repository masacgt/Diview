package com.kurogane.dividend.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Android persists these jobs across process death and reboot; delivery times are inexact. */
object NotificationScheduler {
    const val PRICE_MORNING = "diview.notifications.price.morning"
    const val PRICE_EVENING = "diview.notifications.price.evening"
    const val DIVIDEND_MORNING = "diview.notifications.dividend.morning"

    fun sync(context: Context, settings: NotificationSettings) {
        val work = WorkManager.getInstance(context)
        schedule(work, PRICE_MORNING, NotificationKind.PRICE, true, settings.priceEnabled)
        schedule(work, PRICE_EVENING, NotificationKind.PRICE, false, settings.priceEnabled)
        schedule(work, DIVIDEND_MORNING, NotificationKind.DIVIDEND, true, settings.dividendEnabled)
    }

    private fun schedule(work: WorkManager, name: String, kind: NotificationKind, morning: Boolean, enabled: Boolean) {
        if (!enabled) { work.cancelUniqueWork(name); return }
        val delay = NotificationPolicy.nextDelay(ZonedDateTime.now(), if (morning) NotificationPolicy.morning else NotificationPolicy.evening)
        val constraints = Constraints.Builder().apply {
            if (kind == NotificationKind.PRICE) setRequiredNetworkType(NetworkType.CONNECTED)
        }.build()
        val request = PeriodicWorkRequestBuilder<DividendNotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setInputData(workDataOf("kind" to kind.name, "morning" to morning))
            .addTag("diview.notifications")
            .build()
        work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
