package com.kurogane.dividend.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NotificationHistoryItem(
    val id: String,
    val code: String,
    val name: String,
    val title: String,
    val body: String,
    val category: String,
    val createdAt: Long,
    val unread: Boolean
)

class NotificationHistoryRepository(context: Context) {
    private val prefs = context.getSharedPreferences("notification_history", Context.MODE_PRIVATE)

    fun load(): List<NotificationHistoryItem> = synchronized(lock) {
        if (!prefs.contains(KEY_ITEMS)) return@synchronized sampleItems()
        runCatching {
            val items = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.getJSONObject(index)
                    add(NotificationHistoryItem(
                        id = item.getString("id"),
                        code = item.optString("code"),
                        name = item.optString("name"),
                        title = item.getString("title"),
                        body = item.getString("body"),
                        category = item.optString("category", "signal"),
                        createdAt = item.getLong("createdAt"),
                        unread = item.optBoolean("unread", true)
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun recordBuySignal(code: String, name: String, body: String) = synchronized(lock) {
        save(listOf(NotificationHistoryItem(
            id = UUID.randomUUID().toString(),
            code = code,
            name = name,
            title = "買場シグナル",
            body = body,
            category = "signal",
            createdAt = System.currentTimeMillis(),
            unread = true
        )) + load())
    }

    fun markRead(id: String) = synchronized(lock) {
        save(load().map { if (it.id == id) it.copy(unread = false) else it })
    }

    fun markAllRead() = synchronized(lock) {
        save(load().map { it.copy(unread = false) })
    }

    private fun save(items: List<NotificationHistoryItem>) {
        val array = JSONArray()
        items.take(100).forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("code", item.code)
                .put("name", item.name)
                .put("title", item.title)
                .put("body", item.body)
                .put("category", item.category)
                .put("createdAt", item.createdAt)
                .put("unread", item.unread))
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun sampleItems(): List<NotificationHistoryItem> {
        val now = System.currentTimeMillis()
        return listOf(
            NotificationHistoryItem("sample-signal-8316", "8316", "三井住友FG", "買場シグナル", "目標利回り4.00%を突破！絶好の買場水準に到達しました（現在利回り 4.13%）", "signal", now - 10 * 60_000L, true),
            NotificationHistoryItem("sample-dividend-7203", "7203", "トヨタ自動車", "配当金入金", "中間配当金が入金されました。受取金額: ¥3,640（100株）", "dividend", now - 90 * 60_000L, true),
            NotificationHistoryItem("sample-exdiv-aapl", "AAPL", "Apple Inc.", "権利落ち日", "次回配当の権利付き最終売買日が近づいています。", "dividend", now - 24 * 60 * 60_000L, true),
            NotificationHistoryItem("sample-signal-9432", "9432", "NTT", "買場シグナル", "株価調整により配当利回りが3.37%に上昇中。目標利回り4.00%まであと少しです。", "signal", now - 2 * 24 * 60 * 60_000L, false),
            NotificationHistoryItem("sample-reinvest-emaxis", "投信", "eMAXIS Slim 全世界株式", "分配金再投資", "四半期分配金のファンド内自動再投資が完了しました。", "dividend", now - 3 * 24 * 60 * 60_000L, false)
        )
    }

    private companion object {
        const val KEY_ITEMS = "items"
        val lock = Any()
    }
}
