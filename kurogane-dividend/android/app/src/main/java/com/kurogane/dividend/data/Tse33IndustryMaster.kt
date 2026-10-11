package com.kurogane.dividend.data

import android.content.Context
import com.kurogane.dividend.model.StockClimate
import java.util.Locale

data class JapaneseSecurityIdentity(val name: String, val industry: String)

/** Bundled JPX TSE 33-industry snapshot, keyed by four-digit securities code. */
object Tse33IndustryMaster {
    private val cyclicalIndustries = setOf(
        "建設業", "鉱業", "繊維製品", "パルプ・紙", "化学", "石油・石炭製品", "ゴム製品",
        "ガラス・土石製品", "鉄鋼", "非鉄金属", "金属製品", "機械", "電気機器", "輸送用機器",
        "精密機器", "その他製品", "海運業", "空運業", "卸売業", "銀行業", "証券、商品先物取引業",
        "保険業", "その他金融業", "不動産業"
    )
    private val defensiveIndustries = setOf(
        "水産・農林業", "食料品", "医薬品", "電気・ガス業", "陸運業"
    )
    private val neutralIndustries = setOf(
        "情報・通信業", "倉庫・運輸関連業", "小売業", "サービス業"
    )

    @Volatile
    private var cachedIndustries: Map<String, JapaneseSecurityIdentity>? = null

    fun identities(context: Context): Map<String, JapaneseSecurityIdentity> {
        cachedIndustries?.let { return it }
        return synchronized(this) {
            cachedIndustries ?: run {
                val result = context.assets.open("tse33_industry_master.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.drop(1)
                    .mapNotNull { line ->
                        val columns = line.split('\t')
                        val code = normalizeCode(columns.getOrNull(0).orEmpty())
                        val name = columns.getOrNull(1)?.trim().orEmpty()
                        val industry = columns.getOrNull(3)?.trim().orEmpty()
                        if (code != null && name.isNotEmpty() && industry.isNotEmpty()) code to JapaneseSecurityIdentity(name, industry) else null
                    }
                    .toMap()
                }.toMutableMap()
                runCatching {
                    context.assets.open("jpx_security_name_master.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                        lines.drop(1).forEach { line ->
                            val columns = line.split('\t')
                            val code = normalizeCode(columns.getOrNull(0).orEmpty())
                            val name = columns.getOrNull(1)?.trim().orEmpty()
                            if (code != null && name.isNotEmpty()) {
                                result.putIfAbsent(code, JapaneseSecurityIdentity(name, "東証33業種未分類"))
                            }
                        }
                    }
                }
                result.toMap()
            }.also { cachedIndustries = it }
        }
    }

    fun normalizeCode(rawCode: String): String? {
        val code = rawCode.trim().substringBefore('.').uppercase(Locale.ROOT)
        return code.takeIf { it.matches(Regex("[A-Z0-9]{4}")) }
    }

    fun industries(context: Context): Map<String, String> = identities(context).mapValues { it.value.industry }

    fun defaultClimate(industry: String): StockClimate? = when (industry.trim()) {
        in cyclicalIndustries -> StockClimate.CYCLICAL
        in defensiveIndustries -> StockClimate.DEFENSIVE
        in neutralIndustries -> StockClimate.NEUTRAL
        else -> null
    }
}
