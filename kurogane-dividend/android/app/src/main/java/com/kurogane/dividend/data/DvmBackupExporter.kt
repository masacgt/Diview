package com.kurogane.dividend.data

import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import org.json.JSONArray
import org.json.JSONObject

/** Creates the legacy-compatible stock-array payload and encrypts it in the APK's DVMV1 format. */
object DvmBackupExporter {
    fun export(holdings: List<Holding>): ByteArray {
        val payload = JSONArray().apply {
            holdings.forEachIndexed { index, holding ->
                val freeShares = holding.effectiveTaxFreeShares
                val taxableShares = holding.shares - freeShares
                val fund = holding.category == Category.FUND
                put(JSONObject().apply {
                    put("id", index + 1)
                    put("code", holding.code)
                    put("corporateCode", holding.code)
                    put("name", holding.name)
                    put("corporateName", holding.name)
                    put("country", if (holding.category == Category.US) "US" else "JP")
                    put("category", holding.category.name)
                    put("market", holding.market)
                    put("sector", holding.sector)
                    put("isUSD", holding.category == Category.US)
                    put("currency", holding.currency)
                    put("numOfStocksHeld", taxableShares.toString())
                    put("acquisitionUnitPrice", holding.acqPrice.toString())
                    put("numOfStocksHeldNisaNew", freeShares.toString())
                    put("acquisitionUnitPriceNisaNew", holding.acqPrice.toString())
                    put("numOfHaitou", holding.dps.toString())
                    put("nenriForSyutoku", holding.dps.toString())
                    put("priceOfStock", holding.price.toString())
                    put("yield", holding.yieldRate.toString())
                    put("yieldRimawari", holding.yieldRate.toString())
                    put("monthOfHaitou", holding.dividendMonths.joinToString(","))
                    put("portfolioId", "default")
                    if (fund) put("fund", true)
                })
            }
        }
        return DvmBackupImporter.encrypt(payload.toString().toByteArray(Charsets.UTF_8))
    }

    fun exportExtras(
        shareholderBenefits: Map<String, List<ShareholderBenefit>>,
        stockClimateOverrides: Map<String, StockClimate>,
        dividendHistoryOverrides: Map<String, List<DividendHistoryOverride>>
    ): ByteArray {
        val payload = JSONObject().apply {
            put("format", "diview-portable-data-v1")
            put("shareholderBenefits", JSONObject().apply {
                shareholderBenefits.forEach { (code, benefits) ->
                    put(code, JSONArray().apply {
                        benefits.forEach { benefit ->
                            put(JSONObject().put("name", benefit.name).put("marketValue", benefit.marketValue))
                        }
                    })
                }
            })
            put("stockClimateOverrides", JSONObject().apply {
                stockClimateOverrides.forEach { (code, climate) -> put(code, climate.name) }
            })
            put("dividendHistoryOverrides", JSONObject().apply {
                dividendHistoryOverrides.forEach { (code, overrides) ->
                    put(code, JSONArray().apply {
                        overrides.forEach { row -> put(JSONObject().apply {
                            put("year", row.year)
                            put("stock_price_overridden", row.hasStockPriceOverride)
                            put("stock_price", row.stockPrice ?: JSONObject.NULL)
                            put("annual_dividend_overridden", row.hasAnnualDividendOverride)
                            put("annual_dividend", row.annualDividend ?: JSONObject.NULL)
                        }) }
                    })
                }
            })
        }
        return DvmBackupImporter.encrypt(payload.toString().toByteArray(Charsets.UTF_8))
    }
}
