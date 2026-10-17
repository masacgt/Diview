package com.kurogane.dividend.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.FinancialData
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.PortfolioMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** Fetches the latest Japanese annual dividend records at app/device startup. */
class DividendChangeRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = NotificationStore(applicationContext)
        if (!store.settings().dividendChangeEnabled) return@withContext Result.success()

        try {
            val portfolio = LocalImportStore(applicationContext)
            val holdings = portfolio.loadHoldings().orEmpty().filter { it.category == Category.JAPAN }
            val watchlist = portfolio.loadWatchlist().orEmpty().filter { it.category.equals("jp", true) }
            val names = buildMap {
                holdings.forEach { put(PortfolioMath.normalizeCode(it.code), it.name) }
                watchlist.forEach { putIfAbsent(PortfolioMath.normalizeCode(it.code), it.name) }
            }
            val codes = names.keys.filter { it.matches(Regex("[A-Z0-9]{4}")) }
            if (codes.isEmpty()) return@withContext Result.success()

            val repository = CloudflareMarketRepository()
            val slots = Semaphore(4)
            val failures = coroutineScope {
                codes.map { code -> async {
                    slots.withPermit {
                        try {
                            val document = repository.financialDocument(code) ?: return@withPermit null
                            val financial = withContext(Dispatchers.Default) { FinancialData.parse(document, code) }
                            if (store.settings().dividendChangeEnabled) {
                                store.observeDividendHistory(code, names.getValue(code), financial.dividends.map { it.year to it.dps })
                            }
                            null
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            android.util.Log.w("DividendChangeRefresh", "Fetch failed (${failure.javaClass.simpleName})")
                            failure
                        }
                    }
                } }.awaitAll().filterNotNull()
            }
            if (failures.isEmpty()) Result.success()
            else if (NotificationRetryPolicy.shouldRetry(runAttemptCount, failures)) Result.retry()
            else Result.failure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.w("DividendChangeRefresh", "Refresh failed (${failure.javaClass.simpleName})")
            if (NotificationRetryPolicy.shouldRetry(runAttemptCount, listOf(failure))) Result.retry() else Result.failure()
        }
    }
}
