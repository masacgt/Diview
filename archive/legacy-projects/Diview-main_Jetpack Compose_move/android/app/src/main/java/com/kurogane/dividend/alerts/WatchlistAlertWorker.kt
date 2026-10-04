package com.kurogane.dividend.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kurogane.dividend.MainActivity
import com.kurogane.dividend.R
import com.kurogane.dividend.data.LocalPortfolioRepository
import com.kurogane.dividend.data.NotificationHistoryRepository
import com.kurogane.dividend.data.SharedMarketRepository
import com.kurogane.dividend.model.WatchlistStock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

object WatchlistAlertScheduler {
    private const val PERIODIC_WORK = "watchlist_target_alerts"
    private const val IMMEDIATE_WORK = "watchlist_target_alerts_now"

    fun sync(context: Context, hasEnabledAlerts: Boolean) {
        val manager = WorkManager.getInstance(context.applicationContext)
        if (hasEnabledAlerts) {
            val request = PeriodicWorkRequestBuilder<WatchlistAlertWorker>(1, TimeUnit.HOURS).build()
            manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            manager.cancelUniqueWork(PERIODIC_WORK)
            manager.cancelUniqueWork(IMMEDIATE_WORK)
        }
    }

    fun checkNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<WatchlistAlertWorker>().build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

}

class WatchlistAlertWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val stocks = LocalPortfolioRepository(applicationContext).loadWatchlist()
            .filter { it.alertEnabled && it.category == "jp" && it.targetYield > 0.0 }
        val market = SharedMarketRepository(applicationContext)
        val state = applicationContext.getSharedPreferences("watchlist_alert_state", Context.MODE_PRIVATE)

        for (stock in stocks) {
            if (isStopped) break
            val security = market.cachedSecurity(stock.code) ?: continue
            val price = security.price?.takeIf { it > 0.0 } ?: continue
            val dps = security.annualDps?.takeIf { it > 0.0 } ?: continue
            val date = security.priceDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
            if (ChronoUnit.DAYS.between(date, LocalDate.now()) !in 0..7) continue

            val yield = dps / price * 100.0
            val key = "ready_${stock.category}_${stock.code.uppercase(Locale.ROOT)}"
            synchronized(state) {
                if (yield < stock.targetYield) {
                    state.edit().putBoolean(key, false).apply()
                } else if (!state.getBoolean(key, false) && showNotification(stock, price, yield)) {
                    state.edit().putBoolean(key, true).apply()
                }
            }
        }
        Result.success()
    }

    private fun showNotification(stock: WatchlistStock, price: Double, yield: Double): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val notifications = NotificationManagerCompat.from(applicationContext)
        if (!notifications.areNotificationsEnabled()) return false
        val systemManager = applicationContext.getSystemService(NotificationManager::class.java)
        systemManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Watchlist 買場通知", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_WATCHLIST, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            stock.code.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = String.format(Locale.JAPAN, "現在 %.2f%%（目標 %.2f%%）・株価 ¥%,.0f", yield, stock.targetYield, price)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${stock.name}が目標利回りに到達")
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return runCatching {
            notifications.notify(stock.code.hashCode(), notification)
            runCatching { NotificationHistoryRepository(applicationContext).recordBuySignal(stock.code, stock.name, body) }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val CHANNEL_ID = "watchlist_target_yield"

        fun clearState(context: Context, stock: WatchlistStock) {
            context.getSharedPreferences("watchlist_alert_state", Context.MODE_PRIVATE)
                .edit()
                .remove("ready_${stock.category}_${stock.code.uppercase(Locale.ROOT)}")
                .apply()
        }
    }
}
