package com.kurogane.dividend.data

import com.kurogane.dividend.model.Category
import org.json.JSONObject

internal const val SECURITY_CATEGORY_CACHE_MILLIS = 45L * 24 * 60 * 60 * 1000

internal data class CachedSecurityCategory(val category: String?, val fetchedAtMillis: Long)

/** Per-code timestamps allow new holdings to be looked up during an existing cache's lifetime. */
internal data class SecurityCategoryCache(val entries: Map<String, CachedSecurityCategory> = emptyMap()) {
    fun dueCodes(codes: Collection<String>, now: Long): List<String> = codes
        .map(::normalizeJapaneseSecurityCode).distinct().filter { code ->
            val entry = entries[code]
            entry == null || now - entry.fetchedAtMillis !in 0 until SECURITY_CATEGORY_CACHE_MILLIS
        }

    fun updated(categories: Map<String, String?>, now: Long) = SecurityCategoryCache(
        entries + categories.mapValues { CachedSecurityCategory(it.value, now) }
    )

    fun encode(): String = JSONObject().apply {
        entries.forEach { (code, entry) ->
            put(code, JSONObject().put("category", entry.category ?: JSONObject.NULL)
                .put("fetched_at", entry.fetchedAtMillis))
        }
    }.toString()

    companion object {
        fun decode(raw: String?): SecurityCategoryCache {
            val root = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return SecurityCategoryCache()
            return SecurityCategoryCache(buildMap {
                root.keys().forEach { code ->
                    val row = root.optJSONObject(code) ?: return@forEach
                    val category = if (row.isNull("category")) null else row.optString("category")
                    val fetchedAt = row.optLong("fetched_at", 0)
                    if (code.matches(Regex("[A-Z0-9]{4,5}")) && row.has("category") &&
                        (category == null || category.matches(Regex("\\d{3}"))) && fetchedAt > 0) {
                        put(code, CachedSecurityCategory(category, fetchedAt))
                    }
                }
            })
        }
    }
}

/** This label belongs only to the asset list; the holding's industry remains the TSE33 industry. */
internal fun assetManagementSectorLabel(category: Category, sector: String, productCategory: String?): String {
    if (category != Category.JAPAN || sector != "東証33業種未分類") return sector
    return when (productCategory) {
        "013" -> "REIT（不動産投資信託）"
        "014" -> "ETF（上場投資信託）"
        else -> sector
    }
}
