package com.kurogane.dividend.data

import android.content.Context
import java.time.LocalDate

/** Bundled JPX issue lists; product category 013 also includes infrastructure funds. */
internal object JpxFundMarketMaster {
    @Volatile private var cached: Map<String, Pair<String, LocalDate>>? = null

    fun market(context: Context, code: String, today: LocalDate = LocalDate.now()): String? {
        val entries = cached ?: synchronized(this) {
            cached ?: runCatching {
                context.assets.open("jpx_fund_market_master.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.drop(1).mapNotNull { line ->
                        val parts = line.split('\t')
                        val date = parts.getOrNull(2)?.let { runCatching { LocalDate.parse(it.replace('/', '-')) }.getOrNull() }
                        if (parts.size == 3 && parts[1] in setOf("reit", "infrastructure") && date != null)
                            parts[0] to (parts[1] to date) else null
                    }.toMap()
                }
            }.getOrDefault(emptyMap()).also { cached = it }
        }
        return entries[normalizeJapaneseSecurityCode(code)]?.takeIf { !it.second.isAfter(today) }?.first
    }
}
