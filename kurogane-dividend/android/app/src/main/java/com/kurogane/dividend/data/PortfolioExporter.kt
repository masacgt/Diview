package com.kurogane.dividend.data

import com.kurogane.dividend.model.Holding
import org.json.JSONArray
import org.json.JSONObject

object PortfolioExporter {
    fun json(holdings: List<Holding>): String = JSONObject().apply {
        put("format", "diview-portfolio-v1")
        put("holdings", JSONArray().apply { holdings.forEach { stock -> put(JSONObject().apply {
            put("code", stock.code); put("name", stock.name); put("shares", stock.shares)
            put("averageCost", stock.acqPrice); put("currentPrice", stock.price); put("annualDividendPerShare", stock.dps)
            put("currency", stock.currency); put("category", stock.category.name); put("account", stock.accountType)
            put("taxFreeShares", stock.effectiveTaxFreeShares); put("dividendMonths", JSONArray(stock.dividendMonths))
            put("sector", stock.sector); put("market", stock.market)
        }) } })
    }.toString(2)

    fun csv(holdings: List<Holding>): String = buildString {
        append("\uFEFF銘柄コード,銘柄名,保有数量,平均取得価額,現在値,口座区分\r\n")
        holdings.forEach { stock ->
            val free = stock.effectiveTaxFreeShares
            val accounts = if (free in 1 until stock.shares) listOf(free to "NISA", (stock.shares - free) to "特定口座")
                else listOf(stock.shares to if (free > 0) "NISA" else stock.accountType)
            accounts.forEach { (shares, account) ->
                append(listOf(if (stock.category == com.kurogane.dividend.model.Category.FUND) "" else stock.code,
                    stock.name, shares.toString(), stock.acqPrice.toString(), stock.price.toString(), account)
                    .joinToString(",") { quote(it) })
                append("\r\n")
            }
        }
    }

    private fun quote(value: String): String {
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}
