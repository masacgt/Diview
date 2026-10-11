package com.kurogane.dividend.notifications

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class NotificationHistoryEntry(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val message: String,
    val createdAtMillis: Long,
    val isRead: Boolean
)

/** Preferences contain opt-ins and delivery markers, not account quantities or API credentials. */
class NotificationStore(context: Context) {
    private val settings = context.getSharedPreferences("notification_preferences", Context.MODE_PRIVATE)
    private val delivery = context.getSharedPreferences("notification_delivery", Context.MODE_PRIVATE)

    fun settings(): NotificationSettings = synchronized(lock) {
        NotificationSettings(
            priceEnabled = settings.getBoolean("price", false),
            dividendEnabled = settings.getBoolean("dividend", false),
            revision = settings.getLong("revision", 0L),
            dividendChangeEnabled = settings.getBoolean("dividend_change", false)
        )
    }

    /** Keep the final opt-in check, publication and delivery marker atomic with reset/disable. */
    fun ifCurrent(expected: NotificationSettings, action: () -> Unit) = synchronized(lock) {
        if (settings() == expected) action()
    }

    fun setEnabled(kind: NotificationKind, enabled: Boolean): NotificationSettings = synchronized(lock) {
        val before = settings()
        if (before.enabled(kind) == enabled) return@synchronized before
        val revision = if (before.revision == Long.MAX_VALUE) 1L else before.revision + 1
        val key = when (kind) {
            NotificationKind.PRICE -> "price"
            NotificationKind.DIVIDEND -> "dividend"
            NotificationKind.DIVIDEND_CHANGE -> "dividend_change"
        }
        check(settings.edit().putBoolean(key, enabled)
            .putLong("revision", revision).commit()) { "通知設定を保存できませんでした" }
        settings()
    }

    fun priceStates(): Map<String, PriceAlertState> = synchronized(lock) {
        val root = JSONObject(delivery.getString("prices", "{}") ?: "{}")
        buildMap { root.keys().forEach { code ->
            val row = root.getJSONObject(code)
            put(code, PriceAlertState(row.getString("signature"), row.getBoolean("ready"),
                if (row.isNull("notifiedDate")) null else row.getString("notifiedDate")))
        } }
    }

    fun savePriceStates(states: Map<String, PriceAlertState>) = synchronized(lock) {
        val root = JSONObject().apply { states.forEach { (code, state) -> put(code, JSONObject().apply {
            put("signature", state.signature); put("ready", state.ready)
            put("notifiedDate", state.notifiedDate ?: JSONObject.NULL)
        }) } }
        check(delivery.edit().putString("prices", root.toString()).commit()) { "通知履歴を保存できませんでした" }
    }

    fun queuePendingPriceAlerts(alerts: List<PendingPriceAlert>) = synchronized(lock) {
        if (alerts.isEmpty()) return@synchronized
        val root = JSONObject(delivery.getString("price_pending", "{}") ?: "{}")
        alerts.forEach { alert ->
            val code = com.kurogane.dividend.model.PortfolioMath.normalizeCode(alert.code)
            if (code.isBlank()) return@forEach
            val previous = root.optJSONObject(code)
            val deliverAt = previous?.optLong("deliverAtMillis")?.takeIf { it > 0L }
                ?.let { minOf(it, alert.deliverAtMillis) } ?: alert.deliverAtMillis
            root.put(code, JSONObject()
                .put("name", alert.name)
                .put("currency", alert.currency)
                .put("price", alert.price)
                .put("yield", alert.yield)
                .put("targetYield", alert.targetYield)
                .put("held", alert.held)
                .put("deliverAtMillis", deliverAt))
        }
        check(delivery.edit().putString("price_pending", root.toString()).commit()) { "通知履歴を保存できませんでした" }
    }

    fun pendingPriceAlerts(nowMillis: Long? = null): List<PendingPriceAlert> = synchronized(lock) {
        val root = JSONObject(delivery.getString("price_pending", "{}") ?: "{}")
        buildList {
            root.keys().forEach { code ->
                val row = root.optJSONObject(code) ?: return@forEach
                val event = PendingPriceAlert(
                    code = code,
                    name = row.optString("name"),
                    currency = row.optString("currency", "¥"),
                    price = row.optDouble("price", Double.NaN),
                    yield = row.optDouble("yield", Double.NaN),
                    targetYield = row.optDouble("targetYield", Double.NaN),
                    held = row.optBoolean("held"),
                    deliverAtMillis = row.optLong("deliverAtMillis")
                )
                if (event.name.isNotBlank() && event.price.isFinite() && event.yield.isFinite() &&
                    event.targetYield.isFinite() && (nowMillis == null || event.deliverAtMillis <= nowMillis)) add(event)
            }
        }
    }

    fun removePendingPriceAlerts(alerts: List<PendingPriceAlert>) = synchronized(lock) {
        if (alerts.isEmpty()) return@synchronized
        val root = JSONObject(delivery.getString("price_pending", "{}") ?: "{}")
        alerts.forEach { event ->
            val row = root.optJSONObject(event.code) ?: return@forEach
            if (row.optLong("deliverAtMillis") == event.deliverAtMillis &&
                row.optDouble("price", Double.NaN) == event.price &&
                row.optDouble("yield", Double.NaN) == event.yield) root.remove(event.code)
        }
        check(delivery.edit().putString("price_pending", root.toString()).commit()) { "通知履歴を保存できませんでした" }
    }

    fun dividendMonth(): String? = delivery.getString("dividend_month", null)
    fun saveDividendMonth(month: String) {
        check(delivery.edit().putString("dividend_month", month).commit()) { "通知履歴を保存できませんでした" }
    }

    fun notificationHistory(): List<NotificationHistoryEntry> = synchronized(lock) {
        readNotificationHistory()
    }

    fun unreadNotificationCount(): Int = notificationHistory().count { !it.isRead }

    fun recordNotification(kind: NotificationKind, title: String, message: String, createdAtMillis: Long = System.currentTimeMillis()) = synchronized(lock) {
        val entry = NotificationHistoryEntry(
            id = UUID.randomUUID().toString(),
            kind = kind,
            title = title.take(160),
            message = message.take(1000),
            createdAtMillis = createdAtMillis,
            isRead = false
        )
        writeNotificationHistory((listOf(entry) + readNotificationHistory()).take(MAX_NOTIFICATION_HISTORY))
    }

    fun markNotificationRead(id: String) = synchronized(lock) {
        val history = readNotificationHistory()
        if (history.none { it.id == id && !it.isRead }) return@synchronized
        writeNotificationHistory(history.map { if (it.id == id) it.copy(isRead = true) else it })
    }

    fun markAllNotificationsRead() = synchronized(lock) {
        val history = readNotificationHistory()
        if (history.none { !it.isRead }) return@synchronized
        writeNotificationHistory(history.map { it.copy(isRead = true) })
    }

    private fun readNotificationHistory(): List<NotificationHistoryEntry> {
        val rows = JSONArray(delivery.getString(NOTIFICATION_HISTORY_KEY, "[]") ?: "[]")
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val kind = runCatching { NotificationKind.valueOf(row.optString("kind")) }.getOrNull() ?: continue
                val id = row.optString("id")
                val title = row.optString("title")
                if (id.isBlank() || title.isBlank()) continue
                add(NotificationHistoryEntry(
                    id = id,
                    kind = kind,
                    title = title,
                    message = row.optString("message"),
                    createdAtMillis = row.optLong("createdAtMillis"),
                    isRead = row.optBoolean("isRead")
                ))
            }
        }.sortedByDescending { it.createdAtMillis }
    }

    private fun writeNotificationHistory(entries: List<NotificationHistoryEntry>) {
        val rows = JSONArray().apply {
            entries.forEach { entry -> put(JSONObject()
                .put("id", entry.id)
                .put("kind", entry.kind.name)
                .put("title", entry.title)
                .put("message", entry.message)
                .put("createdAtMillis", entry.createdAtMillis)
                .put("isRead", entry.isRead))
        } }
        check(delivery.edit().putString(NOTIFICATION_HISTORY_KEY, rows.toString()).commit()) {
            "通知履歴を保存できませんでした"
        }
    }

    /** The first successful fetch establishes a baseline; later year/DPS changes become pending alerts. */
    fun seedDividendChangeBaseline(code: String, history: List<Pair<Int, Double>>) = synchronized(lock) {
        val normalized = com.kurogane.dividend.model.PortfolioMath.normalizeCode(code)
        if (normalized.isBlank()) return@synchronized
        val latest = history.filter { (year, dps) -> year in 1900..2100 && dps.isFinite() && dps >= 0.0 }
            .maxByOrNull { it.first } ?: return@synchronized
        val baselines = JSONObject(delivery.getString("dividend_change_baselines", "{}") ?: "{}")
        if (!baselines.has(normalized)) {
            baselines.put(normalized, JSONObject().put("year", latest.first).put("dps", latest.second))
            check(delivery.edit().putString("dividend_change_baselines", baselines.toString()).commit()) {
                "通知履歴を保存できませんでした"
            }
        }
    }

    fun observeDividendHistory(code: String, name: String, history: List<Pair<Int, Double>>) = synchronized(lock) {
        val normalized = com.kurogane.dividend.model.PortfolioMath.normalizeCode(code)
        if (normalized.isBlank() || name.isBlank()) return@synchronized
        val latest = history.filter { (year, dps) -> year in 1900..2100 && dps.isFinite() && dps >= 0.0 }
            .maxByOrNull { it.first } ?: return@synchronized
        val baselines = JSONObject(delivery.getString("dividend_change_baselines", "{}") ?: "{}")
        val pending = JSONObject(delivery.getString("dividend_change_pending", "{}") ?: "{}")
        val previous = baselines.optJSONObject(normalized)
        val event = if (previous == null) null else {
            val oldYear = previous.optInt("year", -1)
            val oldDps = previous.optDouble("dps", Double.NaN)
            when {
                latest.first == oldYear && latest.second != oldDps ->
                    DividendChangeEvent(normalized, name, latest.first, oldDps, latest.second)
                latest.first > oldYear -> {
                    val priorYearDps = history.firstOrNull { it.first == latest.first - 1 }?.second
                    if (priorYearDps != null && priorYearDps != latest.second)
                        DividendChangeEvent(normalized, name, latest.first, priorYearDps, latest.second)
                    else null
                }
                else -> null
            }
        }
        val existing = baselines.optJSONObject(normalized)
        if (existing == null || latest.first >= existing.optInt("year", -1)) {
            baselines.put(normalized, JSONObject().put("year", latest.first).put("dps", latest.second))
        }
        if (event != null) {
            pending.put(normalized, JSONObject()
                .put("name", event.name)
                .put("year", event.fiscalYear)
                .put("previousDps", event.previousDps)
                .put("currentDps", event.currentDps)
                .put("deliverAtMillis", nextDeliveryAtMillis()))
        }
        check(delivery.edit()
            .putString("dividend_change_baselines", baselines.toString())
            .putString("dividend_change_pending", pending.toString())
            .commit()) { "通知履歴を保存できませんでした" }
    }

    fun pendingDividendChanges(nowMillis: Long? = null): List<DividendChangeEvent> = synchronized(lock) {
        val pending = JSONObject(delivery.getString("dividend_change_pending", "{}") ?: "{}")
        buildList {
            pending.keys().forEach { code ->
                val row = pending.optJSONObject(code) ?: return@forEach
                val year = row.optInt("year", -1)
                val previousDps = row.optDouble("previousDps", Double.NaN)
                val currentDps = row.optDouble("currentDps", Double.NaN)
                val name = row.optString("name")
                val deliverAtMillis = row.optLong("deliverAtMillis")
                if (year in 1900..2100 && name.isNotBlank() && previousDps.isFinite() && currentDps.isFinite()) {
                    if (nowMillis == null || deliverAtMillis <= nowMillis) {
                        add(DividendChangeEvent(code, name, year, previousDps, currentDps, deliverAtMillis))
                    }
                }
            }
        }
    }

    fun removeDividendChangeEvents(events: List<DividendChangeEvent>) = synchronized(lock) {
        if (events.isEmpty()) return@synchronized
        val pending = JSONObject(delivery.getString("dividend_change_pending", "{}") ?: "{}")
        events.forEach { event ->
            val row = pending.optJSONObject(event.code) ?: return@forEach
            if (row.optInt("year") == event.fiscalYear &&
                row.optDouble("previousDps", Double.NaN) == event.previousDps &&
                row.optDouble("currentDps", Double.NaN) == event.currentDps) pending.remove(event.code)
        }
        check(delivery.edit().putString("dividend_change_pending", pending.toString()).commit()) {
            "通知履歴を保存できませんでした"
        }
    }

    fun clearPendingDividendChanges() = synchronized(lock) {
        check(delivery.edit().remove("dividend_change_pending").commit()) { "通知履歴を保存できませんでした" }
    }

    /** Records newly passed record dates once; the first scan seeds already-past events silently. */
    fun observeDividendRightsEvents(events: List<DividendRightsEvent>, today: LocalDate, detectedAtMillis: Long) = synchronized(lock) {
        if (events.isEmpty()) return@synchronized
        val initialized = JSONObject(delivery.getString("rights_initialized", "{}") ?: "{}")
        val seen = JSONObject(delivery.getString("rights_seen", "{}") ?: "{}")
        val pending = JSONObject(delivery.getString("rights_pending", "{}") ?: "{}")
        val byCode = events.groupBy { com.kurogane.dividend.model.PortfolioMath.normalizeCode(it.code) }
        byCode.forEach { (code, rows) ->
            if (code.isBlank()) return@forEach
            if (!initialized.has(code)) {
                rows.filter { it.recordDate.isBefore(today) }.forEach { seen.put(it.id, true) }
                initialized.put(code, true)
            }
            rows.forEach { event ->
                if (seen.has(event.id) || !event.recordDate.isBefore(today)) return@forEach
                pending.put(event.id, event.toJson(nextDeliveryAtMillis(detectedAtMillis)))
                seen.put(event.id, true)
            }
        }
        check(delivery.edit()
            .putString("rights_initialized", initialized.toString())
            .putString("rights_seen", seen.toString())
            .putString("rights_pending", pending.toString())
            .commit()) { "通知履歴を保存できませんでした" }
    }

    fun pendingDividendRightsEvents(nowMillis: Long? = null): List<PendingDividendRightsEvent> = synchronized(lock) {
        val pending = JSONObject(delivery.getString("rights_pending", "{}") ?: "{}")
        buildList {
            pending.keys().forEach { id ->
                val row = pending.optJSONObject(id) ?: return@forEach
                val deliverAt = row.optLong("deliverAtMillis")
                if (nowMillis != null && deliverAt > nowMillis) return@forEach
                val code = row.optString("code")
                val name = row.optString("name")
                val exDate = runCatching { LocalDate.parse(row.optString("exDividendDate")) }.getOrNull() ?: return@forEach
                val recordDate = runCatching { LocalDate.parse(row.optString("recordDate")) }.getOrNull() ?: return@forEach
                val finalRightsDate = runCatching { LocalDate.parse(row.optString("finalRightsDate")) }.getOrNull()
                if (code.isNotBlank() && name.isNotBlank()) {
                    add(PendingDividendRightsEvent(
                        DividendRightsEvent(id, code, name, exDate, finalRightsDate, recordDate), deliverAt
                    ))
                }
            }
        }
    }

    fun removePendingDividendRightsEvents(events: List<PendingDividendRightsEvent>) = synchronized(lock) {
        if (events.isEmpty()) return@synchronized
        val pending = JSONObject(delivery.getString("rights_pending", "{}") ?: "{}")
        events.forEach { event ->
            val row = pending.optJSONObject(event.event.id) ?: return@forEach
            if (row.optLong("deliverAtMillis") == event.deliverAtMillis) pending.remove(event.event.id)
        }
        check(delivery.edit().putString("rights_pending", pending.toString()).commit()) { "通知履歴を保存できませんでした" }
    }

    fun clear(): NotificationSettings = synchronized(lock) {
        val before = settings()
        val revision = if (before.revision == Long.MAX_VALUE) 1L else before.revision + 1
        check(settings.edit().clear().putLong("revision", revision).commit()) { "通知設定を初期化できませんでした" }
        check(delivery.edit().clear().commit()) { "通知履歴を初期化できませんでした" }
        settings()
    }

    private fun DividendRightsEvent.toJson(deliverAtMillis: Long) = JSONObject()
        .put("code", code)
        .put("name", name)
        .put("exDividendDate", exDividendDate.toString())
        .put("finalRightsDate", finalRightsDate?.toString().orEmpty())
        .put("recordDate", recordDate.toString())
        .put("deliverAtMillis", deliverAtMillis)

    private fun nextDeliveryAtMillis(detectedAtMillis: Long = System.currentTimeMillis()): Long =
        NotificationPolicy.nextDeliveryAt(Instant.ofEpochMilli(detectedAtMillis).atZone(NotificationPolicy.japanZone))
            .toInstant().toEpochMilli()

    companion object {
        private val lock = Any()
        private const val NOTIFICATION_HISTORY_KEY = "notification_history"
        private const val MAX_NOTIFICATION_HISTORY = 100
    }
}
