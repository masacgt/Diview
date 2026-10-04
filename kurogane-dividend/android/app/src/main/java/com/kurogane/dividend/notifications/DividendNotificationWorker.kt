package com.kurogane.dividend.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Locale

class DividendNotificationWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind")?.let { value -> NotificationKind.entries.firstOrNull { it.name == value } }
            ?: return Result.failure()
        val morning = inputData.getBoolean("morning", true)
        if (!NotificationPolicy.withinWindow(ZonedDateTime.now(), morning)) return Result.success()
        return try {
            NotificationDeliveryEngine(applicationContext, canPost = { selectedKind ->
                NotificationPolicy.withinWindow(ZonedDateTime.now(), morning) && DiviewNotifications(applicationContext).run {
                    createChannels(); canPost(DiviewNotifications.channel(selectedKind))
                }
            }).run(kind)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }
}

/** Kept separate from Worker so failures, permissions and duplicate delivery can be tested offline. */
class NotificationDeliveryEngine(
    context: Context,
    private val fetchPrices: suspend () -> Map<String, Double> = { CloudflareMarketRepository().fetchPriceCatalog() },
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(NotificationPolicy.japanZone) },
    private val canPost: (NotificationKind) -> Boolean = { kind ->
        DiviewNotifications(context).run { createChannels(); canPost(DiviewNotifications.channel(kind)) }
    },
    private val post: (NotificationKind, String, String) -> Boolean = { kind, title, text ->
        DiviewNotifications(context).post(if (kind == NotificationKind.PRICE) DiviewNotifications.PRICE_ID else DiviewNotifications.DIVIDEND_ID,
            DiviewNotifications.channel(kind), title, text, if (kind == NotificationKind.PRICE) "watchlist" else "annual")
    }
) {
    private val store = NotificationStore(context)
    private val portfolio = LocalImportStore(context)

    suspend fun run(kind: NotificationKind) = withContext(Dispatchers.IO) {
        deliveryMutex.withLock {
            val settings = store.settings()
            if (!settings.enabled(kind) || !canPost(kind)) return@withLock
            if (kind == NotificationKind.PRICE) {
                // Reload after the network call so deleted/disabled securities cannot create late alerts.
                val initial = portfolio.loadWatchlist().orEmpty()
                if (initial.none { it.alertEnabled }) { store.ifCurrent(settings) { store.savePriceStates(emptyMap()) }; return@withLock }
                val prices = fetchPrices()
                val stocks = portfolio.loadWatchlist().orEmpty()
                val date = now().withZoneSameInstant(NotificationPolicy.japanZone).toLocalDate()
                val plan = NotificationPolicy.priceAlerts(stocks, prices, store.priceStates(), date)
                if (store.settings() != settings || !canPost(kind)) return@withLock
                if (plan.hits.isEmpty()) { store.ifCurrent(settings) { store.savePriceStates(plan.states) }; return@withLock }
                val text = plan.hits.take(5).joinToString("\n") {
                    "${it.stock.name.take(70)} (${it.stock.code})：取得株価 ${it.stock.currency}${String.format(Locale.JAPAN, "%.2f", it.price)} / 利回り ${String.format(Locale.JAPAN, "%.2f", it.yield)}% / 目標 ${String.format(Locale.JAPAN, "%.2f", it.stock.targetYield)}%"
                } + if (plan.hits.size > 5) "\nほか${plan.hits.size - 5}銘柄。Watchlistで確認できます。" else ""
                store.ifCurrent(settings) { if (canPost(kind) && post(kind, "${plan.hits.size}銘柄が目標利回りに到達", text)) {
                    val sent = plan.hits.map { com.kurogane.dividend.model.PortfolioMath.normalizeCode(it.stock.code) }.toSet()
                    store.savePriceStates(plan.states.mapValues { (code, state) ->
                        if (code in sent) state.copy(notifiedDate = date.toString()) else state
                    })
                } }
            } else {
                val date = now().withZoneSameInstant(NotificationPolicy.japanZone).toLocalDate()
                val month = YearMonth.from(date).toString()
                if (store.dividendMonth() == month) return@withLock
                val holdings = NotificationPolicy.dividendHoldings(portfolio.loadHoldings().orEmpty(), date)
                if (holdings.isEmpty() || store.settings() != settings || !canPost(kind)) return@withLock
                val names = holdings.take(5).joinToString("、") { it.name.take(50) }
                val text = "$names${if (holdings.size > 5) "ほか" else ""}の登録上の配当受取月です。入金日と実際の入金は証券口座で確認してください。"
                store.ifCurrent(settings) {
                    if (canPost(kind) && post(kind, "${date.monthValue}月の配当予定：${holdings.size}銘柄", text)) store.saveDividendMonth(month)
                }
            }
        }
    }

    companion object {
        private val deliveryMutex = Mutex()
    }
}
