package com.kurogane.dividend.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
    const val DIVIDEND_EVENING = "diview.notifications.dividend.evening"
    const val DIVIDEND_CHANGE_MORNING = "diview.notifications.dividend-change.morning"
    const val DIVIDEND_CHANGE_EVENING = "diview.notifications.dividend-change.evening"
    const val DIVIDEND_CHANGE_REFRESH = "diview.notifications.dividend-change.refresh"
    const val DIVIDEND_CHANGE_REFRESH_MORNING = "diview.notifications.dividend-change.refresh.morning"
    const val DIVIDEND_CHANGE_REFRESH_EVENING = "diview.notifications.dividend-change.refresh.evening"
    const val DIVIDEND_RIGHTS_REFRESH = "diview.notifications.dividend-rights.refresh"
    const val DIVIDEND_RIGHTS_REFRESH_MORNING = "diview.notifications.dividend-rights.refresh.morning"
    const val DIVIDEND_RIGHTS_REFRESH_EVENING = "diview.notifications.dividend-rights.refresh.evening"

    fun sync(context: Context, settings: NotificationSettings) {
        val work = WorkManager.getInstance(context)
        schedule(work, PRICE_MORNING, NotificationKind.PRICE, true, settings.priceEnabled)
        schedule(work, PRICE_EVENING, NotificationKind.PRICE, false, settings.priceEnabled)
        schedule(work, DIVIDEND_MORNING, NotificationKind.DIVIDEND, true, settings.dividendEnabled)
        schedule(work, DIVIDEND_EVENING, NotificationKind.DIVIDEND, false, settings.dividendEnabled)
        schedule(work, DIVIDEND_CHANGE_MORNING, NotificationKind.DIVIDEND_CHANGE, true, settings.dividendChangeEnabled)
        schedule(work, DIVIDEND_CHANGE_EVENING, NotificationKind.DIVIDEND_CHANGE, false, settings.dividendChangeEnabled)
        scheduleDividendChangeRefresh(work, DIVIDEND_CHANGE_REFRESH_MORNING, settings.dividendChangeEnabled, true)
        scheduleDividendChangeRefresh(work, DIVIDEND_CHANGE_REFRESH_EVENING, settings.dividendChangeEnabled, false)
        scheduleDividendRightsRefresh(work, DIVIDEND_RIGHTS_REFRESH_MORNING, settings.dividendEnabled, true)
        scheduleDividendRightsRefresh(work, DIVIDEND_RIGHTS_REFRESH_EVENING, settings.dividendEnabled, false)
    }

    fun enqueueDividendChangeRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<DividendChangeRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("diview.notifications")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DIVIDEND_CHANGE_REFRESH, ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueDividendRightsRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<DividendRightsRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("diview.notifications")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DIVIDEND_RIGHTS_REFRESH, ExistingWorkPolicy.KEEP, request)
    }

    private fun scheduleDividendChangeRefresh(work: WorkManager, name: String, enabled: Boolean, morning: Boolean) {
        if (!enabled) { work.cancelUniqueWork(name); return }
        val time = if (morning) java.time.LocalTime.of(8, 0) else java.time.LocalTime.of(17, 0)
        val delay = NotificationPolicy.nextDelay(ZonedDateTime.now(), time)
        val request = PeriodicWorkRequestBuilder<DividendChangeRefreshWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("diview.notifications")
            .build()
        work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun scheduleDividendRightsRefresh(work: WorkManager, name: String, enabled: Boolean, morning: Boolean) {
        if (!enabled) { work.cancelUniqueWork(name); return }
        val time = if (morning) java.time.LocalTime.of(8, 0) else java.time.LocalTime.of(17, 0)
        val delay = NotificationPolicy.nextDelay(ZonedDateTime.now(), time)
        val request = PeriodicWorkRequestBuilder<DividendRightsRefreshWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("diview.notifications")
            .build()
        work.enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)
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
