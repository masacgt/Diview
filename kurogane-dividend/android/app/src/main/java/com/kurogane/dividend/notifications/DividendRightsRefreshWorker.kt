package com.kurogane.dividend.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.PortfolioMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Checks locally held stocks against the acquired Cloudflare rights-date schedule. */
class DividendRightsRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = NotificationStore(applicationContext)
        val settings = store.settings()
        if (!settings.dividendEnabled) return@withContext Result.success()

        try {
            val bySymbol = LocalImportStore(applicationContext).loadHoldings().orEmpty()
                .asSequence()
                .filter { it.shares > 0 }
                .mapNotNull { holding -> symbolFor(holding)?.let { it to holding } }
                .toMap()
            if (bySymbol.isEmpty()) return@withContext Result.success()

            val repository = CloudflareMarketRepository()
            val records = mutableListOf<DividendRightsEvent>()
            bySymbol.keys.chunked(50).forEach { batch ->
                val calendar = repository.fetchDividendMonths(batch)
                calendar.eventsBySymbol.forEach symbolLoop@ { (symbol, events) ->
                    val holding = bySymbol[symbol] ?: return@symbolLoop
                    events.forEach eventLoop@ { event ->
                        val recordDate = event.recordDate ?: return@eventLoop
                        records += DividendRightsEvent(
                            id = rightsEventId(holding.code, event.date, recordDate),
                            code = holding.code,
                            name = holding.name,
                            exDividendDate = event.date,
                            finalRightsDate = event.finalRightsDate,
                            recordDate = recordDate
                        )
                    }
                }
                calendar.upcomingEventsBySymbol.forEach upcomingLoop@ { (symbol, event) ->
                    if (event.estimatedFromHistory) return@upcomingLoop
                    val holding = bySymbol[symbol] ?: return@upcomingLoop
                    val recordDate = event.recordDate ?: return@upcomingLoop
                    records += DividendRightsEvent(
                        id = rightsEventId(holding.code, event.exDividendDate, recordDate),
                        code = holding.code,
                        name = holding.name,
                        exDividendDate = event.exDividendDate,
                        finalRightsDate = event.finalRightsDate,
                        recordDate = recordDate
                    )
                }
            }

            if (store.settings() == settings && records.isNotEmpty()) {
                store.observeDividendRightsEvents(
                    records.distinctBy { it.id },
                    LocalDate.now(NotificationPolicy.japanZone),
                    System.currentTimeMillis()
                )
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun symbolFor(holding: Holding): String? {
        val code = PortfolioMath.normalizeCode(holding.code).removeSuffix(".T")
        return when (holding.category) {
            Category.JAPAN -> "$code.T".takeIf { code.matches(Regex("[A-Z0-9]{4,5}")) }
            Category.US -> code.takeIf { it.matches(Regex("[A-Z0-9.^_=-]{1,32}")) }
            Category.FUND -> null
        }
    }

    private fun rightsEventId(code: String, exDividendDate: LocalDate, recordDate: LocalDate): String =
        "${PortfolioMath.normalizeCode(code)}:$exDividendDate:$recordDate"
}
