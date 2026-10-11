package com.kurogane.dividend.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kurogane.dividend.data.CloudflareMarketRepository
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.model.PortfolioMath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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

/** Delivery stays local; discovered events are held until the next 08:30 or 17:30 checkpoint. */
class NotificationDeliveryEngine(
    context: Context,
    private val fetchPrices: suspend () -> Map<String, Double> = { CloudflareMarketRepository().fetchPriceCatalog() },
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(NotificationPolicy.japanZone) },
    private val canPost: (NotificationKind) -> Boolean = { kind ->
        DiviewNotifications(context).run { createChannels(); canPost(DiviewNotifications.channel(kind)) }
    },
    private val post: (NotificationKind, String, String) -> Boolean = { kind, title, text ->
        val id = when (kind) {
            NotificationKind.PRICE -> DiviewNotifications.PRICE_ID
            NotificationKind.DIVIDEND -> DiviewNotifications.DIVIDEND_ID
            NotificationKind.DIVIDEND_CHANGE -> DiviewNotifications.DIVIDEND_CHANGE_ID
        }
        DiviewNotifications(context).post(id, DiviewNotifications.channel(kind), title, text,
            if (kind == NotificationKind.PRICE) "watchlist" else "annual")
    }
) {
    private val store = NotificationStore(context)
    private val portfolio = LocalImportStore(context)

    suspend fun run(kind: NotificationKind) = withContext(Dispatchers.IO) {
        deliveryMutex.withLock {
            val settings = store.settings()
            if (!settings.enabled(kind) || !canPost(kind)) return@withLock
            val nowMillis = now().toInstant().toEpochMilli()
            when (kind) {
                NotificationKind.PRICE -> {
                    val initialStocks = portfolio.loadWatchlist().orEmpty()
                    if (initialStocks.none { it.alertEnabled }) {
                        store.ifCurrent(settings) {
                            store.savePriceStates(emptyMap())
                            store.removePendingPriceAlerts(store.pendingPriceAlerts())
                        }
                        return@withLock
                    }

                    val prices = fetchPrices()
                    val stocks = portfolio.loadWatchlist().orEmpty()
                    val holdings = portfolio.loadHoldings().orEmpty()
                    val plan = NotificationPolicy.priceAlerts(stocks, holdings, prices, store.priceStates())
                    if (store.settings() != settings || !canPost(kind)) return@withLock
                    store.ifCurrent(settings) {
                        store.savePriceStates(plan.states)
                        val enabledCodes = stocks.filter { it.alertEnabled }
                            .map { PortfolioMath.normalizeCode(it.code) }.toSet()
                        val cancelled = store.pendingPriceAlerts().filter { pending ->
                            pending.code !in enabledCodes || plan.states[pending.code]?.ready != true
                        }
                        store.removePendingPriceAlerts(cancelled)
                        val deliverAt = NotificationPolicy.nextDeliveryAt(now()).toInstant().toEpochMilli()
                        store.queuePendingPriceAlerts(plan.hits.map { hit ->
                            PendingPriceAlert(
                                code = hit.stock.code,
                                name = hit.stock.name,
                                currency = hit.stock.currency,
                                price = hit.price,
                                yield = hit.yield,
                                targetYield = hit.stock.targetYield,
                                held = hit.held,
                                deliverAtMillis = deliverAt
                            )
                        })
                    }
                    if (store.settings() == settings && canPost(kind)) {
                        deliverPendingPriceAlerts(settings, nowMillis)
                    }
                }
                NotificationKind.DIVIDEND -> deliverPendingDividendRights(settings, nowMillis)
                NotificationKind.DIVIDEND_CHANGE -> deliverPendingDividendChanges(settings, nowMillis)
            }
        }
    }

    private fun deliverPendingPriceAlerts(settings: NotificationSettings, nowMillis: Long) {
        val pending = store.pendingPriceAlerts(nowMillis)
        if (pending.isEmpty()) return
        val details = pending.take(5).joinToString("\n") { event ->
            val held = if (event.held) "・保有中" else ""
            "${event.name.take(70)} (${event.code})$held：取得株価 ${event.currency}${String.format(Locale.JAPAN, "%.2f", event.price)} / 利回り ${String.format(Locale.JAPAN, "%.2f", event.yield)}% / 目標 ${String.format(Locale.JAPAN, "%.2f", event.targetYield)}%"
        } + if (pending.size > 5) "\nほか${pending.size - 5}銘柄" else ""
        store.ifCurrent(settings) {
            if (canPost(NotificationKind.PRICE) && post(NotificationKind.PRICE, "${pending.size}銘柄が目標利回りに到達", details)) {
                store.removePendingPriceAlerts(pending)
            }
        }
    }

    private fun deliverPendingDividendRights(settings: NotificationSettings, nowMillis: Long) {
        val pending = store.pendingDividendRightsEvents(nowMillis)
        if (pending.isEmpty()) return
        val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.JAPAN)
        val details = pending.take(5).joinToString("\n") { pendingEvent ->
            val event = pendingEvent.event
            "${event.name.take(50)} (${event.code})：取得済み日程の権利確定日 ${event.recordDate.format(dateFormat)} を経過しました。配当額・入金日は会社発表や証券口座で確認してください。"
        } + if (pending.size > 5) "\nほか${pending.size - 5}銘柄" else ""
        store.ifCurrent(settings) {
            if (canPost(NotificationKind.DIVIDEND) && post(NotificationKind.DIVIDEND,
                    "配当権利確定日を経過：${pending.size}銘柄", details)) {
                store.removePendingDividendRightsEvents(pending)
            }
        }
    }

    private fun deliverPendingDividendChanges(settings: NotificationSettings, nowMillis: Long) {
        val pending = store.pendingDividendChanges(nowMillis)
        if (pending.isEmpty()) return
        val increased = pending.count { it.isIncrease }
        val decreased = pending.size - increased
        val title = buildList {
            if (increased > 0) add("増配 ${increased}銘柄")
            if (decreased > 0) add("減配 ${decreased}銘柄")
        }.joinToString("・") + "のお知らせ"
        val details = pending.take(8).joinToString("\n") { event ->
            val direction = if (event.isIncrease) "増配" else "減配"
            "${event.name.take(50)} (${event.code})：${event.fiscalYear}年度 $direction / 1株配当 ¥${String.format(Locale.JAPAN, "%.2f", event.previousDps)} → ¥${String.format(Locale.JAPAN, "%.2f", event.currentDps)}"
        } + if (pending.size > 8) "\nほか${pending.size - 8}銘柄" else ""
        store.ifCurrent(settings) {
            if (canPost(NotificationKind.DIVIDEND_CHANGE) && post(NotificationKind.DIVIDEND_CHANGE, title, details)) {
                store.removeDividendChangeEvents(pending)
            }
        }
    }

    companion object {
        private val deliveryMutex = Mutex()
    }
}
