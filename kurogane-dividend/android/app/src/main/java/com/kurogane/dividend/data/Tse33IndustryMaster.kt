package com.kurogane.dividend.data

import android.content.Context

/** Bundled JPX TSE 33-industry snapshot, keyed by four-digit securities code. */
object Tse33IndustryMaster {
    @Volatile
    private var cachedIndustries: Map<String, String>? = null

    fun industries(context: Context): Map<String, String> {
        cachedIndustries?.let { return it }
        return synchronized(this) {
            cachedIndustries ?: context.assets.open("tse33_industry_master.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.drop(1)
                    .mapNotNull { line ->
                        val columns = line.split('\t')
                        val code = normalizeCode(columns.getOrNull(0).orEmpty())
                        val industry = columns.getOrNull(3)?.trim().orEmpty()
                        if (code != null && industry.isNotEmpty()) code to industry else null
                    }
                    .toMap()
            }.also { cachedIndustries = it }
        }
    }

    fun normalizeCode(rawCode: String): String? {
        val code = rawCode.trim().substringBefore('.').uppercase()
        return code.takeIf { it.matches(Regex("[A-Z0-9]{4}")) }
    }
}
