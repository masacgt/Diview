package com.kurogane.dividend.notifications

import android.content.Context
import org.json.JSONObject

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

    fun dividendMonth(): String? = delivery.getString("dividend_month", null)
    fun saveDividendMonth(month: String) {
        check(delivery.edit().putString("dividend_month", month).commit()) { "通知履歴を保存できませんでした" }
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
                .put("currentDps", event.currentDps))
        }
        check(delivery.edit()
            .putString("dividend_change_baselines", baselines.toString())
            .putString("dividend_change_pending", pending.toString())
            .commit()) { "通知履歴を保存できませんでした" }
    }

    fun pendingDividendChanges(): List<DividendChangeEvent> = synchronized(lock) {
        val pending = JSONObject(delivery.getString("dividend_change_pending", "{}") ?: "{}")
        buildList {
            pending.keys().forEach { code ->
                val row = pending.optJSONObject(code) ?: return@forEach
                val year = row.optInt("year", -1)
                val previousDps = row.optDouble("previousDps", Double.NaN)
                val currentDps = row.optDouble("currentDps", Double.NaN)
                val name = row.optString("name")
                if (year in 1900..2100 && name.isNotBlank() && previousDps.isFinite() && currentDps.isFinite()) {
                    add(DividendChangeEvent(code, name, year, previousDps, currentDps))
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

    fun clear(): NotificationSettings = synchronized(lock) {
        val before = settings()
        val revision = if (before.revision == Long.MAX_VALUE) 1L else before.revision + 1
        check(settings.edit().clear().putLong("revision", revision).commit()) { "通知設定を初期化できませんでした" }
        check(delivery.edit().clear().commit()) { "通知履歴を初期化できませんでした" }
        settings()
    }

    companion object { private val lock = Any() }
}
