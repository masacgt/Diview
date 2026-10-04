package com.kurogane.dividend.notifications

import android.content.Context
import org.json.JSONObject

/** Preferences contain opt-ins and delivery markers, not account quantities or API credentials. */
class NotificationStore(context: Context) {
    private val settings = context.getSharedPreferences("notification_preferences", Context.MODE_PRIVATE)
    private val delivery = context.getSharedPreferences("notification_delivery", Context.MODE_PRIVATE)

    fun settings(): NotificationSettings = synchronized(lock) {
        NotificationSettings(settings.getBoolean("price", false), settings.getBoolean("dividend", false), settings.getLong("revision", 0L))
    }

    /** Keep the final opt-in check, publication and delivery marker atomic with reset/disable. */
    fun ifCurrent(expected: NotificationSettings, action: () -> Unit) = synchronized(lock) {
        if (settings() == expected) action()
    }

    fun setEnabled(kind: NotificationKind, enabled: Boolean): NotificationSettings = synchronized(lock) {
        val before = settings()
        if (before.enabled(kind) == enabled) return@synchronized before
        val revision = if (before.revision == Long.MAX_VALUE) 1L else before.revision + 1
        check(settings.edit().putBoolean(if (kind == NotificationKind.PRICE) "price" else "dividend", enabled)
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

    fun clear(): NotificationSettings = synchronized(lock) {
        val before = settings()
        val revision = if (before.revision == Long.MAX_VALUE) 1L else before.revision + 1
        check(settings.edit().clear().putLong("revision", revision).commit()) { "通知設定を初期化できませんでした" }
        check(delivery.edit().clear().commit()) { "通知履歴を初期化できませんでした" }
        settings()
    }

    companion object { private val lock = Any() }
}
