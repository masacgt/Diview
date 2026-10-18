package com.kurogane.dividend.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.model.WatchlistStock

enum class DvmBackupKind(val fileName: String, val label: String) {
    STOCKS("BackUpStockInfoAndroid.dvm", "銘柄一覧"),
    RECORDS("BackUpRecordInfoAndroid.dvm", "資産・配当推移"),
    CUSTOM_STOCKS("BackUpCustomStockInfoAndroid.dvm", "カスタム銘柄"),
    YIELD_ALERTS("BackUpYieldAlertAndroid.dvm", "目標利回りアラート"),
    DIVIEW_EXTRAS("DiviewPortableData.dvm", "Diview編集データ");

    companion object {
        fun fromName(name: String): DvmBackupKind? = entries.firstOrNull { name.equals(it.fileName, true) }
    }
}

data class DvmStock(val code: String, val name: String, val shares: Double, val averageCost: Double, val currentPrice: Double, val account: String,
                    val taxFreeShares: Double? = null)
data class DvmPortableExtras(
    val shareholderBenefits: Map<String, List<ShareholderBenefit>> = emptyMap(),
    val stockClimateOverrides: Map<String, StockClimate> = emptyMap(),
    val dividendHistoryOverrides: Map<String, List<DividendHistoryOverride>> = emptyMap(),
    val watchlist: List<WatchlistStock>? = null
)
data class DvmBackupPreview(
    val kind: DvmBackupKind,
    val itemCount: Int,
    val stocks: List<DvmStock>,
    val payload: ByteArray,
    val extras: DvmPortableExtras = DvmPortableExtras()
)

object DvmBackupImporter {
    private val magic = "DVMV1".toByteArray(StandardCharsets.US_ASCII)
    // This matches the AES-256-GCM key used by the legacy DVM backup format.
    private val legacyKey = byteArrayOf(68,118,77,103,83,121,110,99,65,69,83,50,53,54,71,67,77,75,101,121,50,48,50,54,79,116,116,105,76,97,98,33)

    fun decrypt(file: ByteArray): ByteArray {
        require(file.size <= 50 * 1024 * 1024) { "DVMバックアップが大きすぎます" }
        require(file.size >= magic.size + 12 + 16 && magic.indices.all { file[it] == magic[it] }) { "DVMバックアップの形式が正しくありません" }
        val nonce = file.copyOfRange(magic.size, magic.size + 12)
        val cipherText = file.copyOfRange(magic.size + 12, file.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(legacyKey, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(cipherText)
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        require(plaintext.size <= 50 * 1024 * 1024) { "DVMバックアップが大きすぎます" }
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(legacyKey, "AES"), GCMParameterSpec(128, nonce))
        return magic + nonce + cipher.doFinal(plaintext)
    }

    fun parse(fileName: String, bytes: ByteArray): DvmBackupPreview {
        val kind = requireNotNull(DvmBackupKind.fromName(fileName)) { "未対応のDVMバックアップです" }
        val plain = decrypt(bytes)
        val text = plain.toString(StandardCharsets.UTF_8).trim()
        if (kind == DvmBackupKind.DIVIEW_EXTRAS) {
            val root = JSONObject(text)
            val extras = parseExtras(root)
            val itemCount = extras.shareholderBenefits.size + extras.stockClimateOverrides.size +
                extras.dividendHistoryOverrides.size + extras.watchlist.orEmpty().size
            return DvmBackupPreview(kind, itemCount, emptyList(), plain, extras)
        }
        val items = when {
            text.isBlank() -> JSONArray()
            text.startsWith("[") -> JSONArray(text)
            text.startsWith("{") -> {
                val root = JSONObject(text)
                val keys = when (kind) {
                    DvmBackupKind.STOCKS -> listOf("stocks", "stockInfo", "data")
                    DvmBackupKind.RECORDS -> listOf("records", "data")
                    DvmBackupKind.CUSTOM_STOCKS -> listOf("customStocks", "data")
                    DvmBackupKind.YIELD_ALERTS -> listOf("yieldAlerts", "alerts", "data")
                    DvmBackupKind.DIVIEW_EXTRAS -> emptyList()
                }
                keys.firstNotNullOfOrNull { key -> root.optJSONArray(key) }
                    ?: throw IllegalArgumentException("バックアップに対象データがありません")
            }
            else -> throw IllegalArgumentException("バックアップの中身を読み取れません")
        }
        val stockBenefits = linkedMapOf<String, List<ShareholderBenefit>>()
        val stocks = if (kind == DvmBackupKind.STOCKS) (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val holdingFields = listOf(
                "numOfStocksHeld" to "acquisitionUnitPrice", "numOfStocksHeldNisa" to "acquisitionUnitPriceNisa",
                "numOfStocksHeldNisaNew" to "acquisitionUnitPriceNisaNew", "numOfStocksHeldNisaNewTsumitate" to "acquisitionUnitPriceNisaNewTsumitate",
                "numOfStocksHeldIdeco" to "acquisitionUnitPriceIdeco"
            )
            val positions = holdingFields.map { (quantityKey, costKey) ->
                val quantity = item.optDouble(quantityKey, 0.0)
                require(PortfolioMath.wholeShares(quantity) != null) { "バックアップの保有数量が正しくありません" }
                quantity to item.optString(costKey).toNumber()
            }.filter { it.first > 0.0 }
            val shares = positions.sumOf { it.first }
            require(PortfolioMath.wholeShares(shares) != null) { "保有数量が上限を超えています" }
            val averageCost = if (shares > 0) positions.sumOf { (quantity, cost) -> quantity * cost } / shares else item.optString("acquisitionUnitPrice").toNumber()
            val name = item.optString("corporateName", item.optString("name")).trim()
            val rawCode = item.optString("corporateCode", item.optString("code"))
            val isFund = item.optString("category").equals("FUND", ignoreCase = true) || rawCode.startsWith("FUND:", ignoreCase = true)
            val code = if (isFund && !rawCode.startsWith("FUND:", ignoreCase = true))
                "FUND:${FundNameMatcher.normalize(name)}" else PortfolioMath.normalizeCode(rawCode)
            val stock = DvmStock(
                code = code, name = name, shares = shares,
                averageCost = averageCost, currentPrice = item.optString("priceOfStock").toNumber(),
                account = when {
                    item.optBoolean("isUSD", false) || item.optInt("isUSD", 0) == 1 -> "米国株"
                    positions.size > 1 -> "複数口座"
                    positions.isNotEmpty() && holdingFields[holdingFields.indexOfFirst { (key, _) -> item.optDouble(key, 0.0) > 0 }].first.contains("Nisa") -> "NISA"
                    else -> "特定口座"
                },
                taxFreeShares = holdingFields.filter { it.first.contains("Nisa") }.sumOf { item.optDouble(it.first, 0.0) }
            )
            if (!PortfolioMath.validCode(stock.code) || stock.name.isBlank()) return@mapNotNull null
            val benefitDescription = item.optString("shareholderBenefitDescription").trim()
            val benefitAmount = item.optString("shareholderBenefitAmount").trim()
            if (benefitDescription.isNotBlank() || benefitAmount.isNotBlank()) {
                stockBenefits[stock.code] = listOf(ShareholderBenefit(benefitDescription, benefitAmount))
            }
            stock
        } else emptyList()
        return DvmBackupPreview(kind, items.length(), stocks, plain, DvmPortableExtras(shareholderBenefits = stockBenefits))
    }

    private fun parseExtras(root: JSONObject): DvmPortableExtras {
        val benefitsJson = root.optJSONObject("shareholderBenefits") ?: JSONObject()
        val benefits = buildMap {
            val codes = benefitsJson.keys()
            while (codes.hasNext()) {
                val rawCode = codes.next()
                val code = PortfolioMath.normalizeCode(rawCode)
                val entries = benefitsJson.optJSONArray(rawCode) ?: continue
                val rows = (0 until entries.length()).mapNotNull { index ->
                    val row = entries.optJSONObject(index) ?: return@mapNotNull null
                    ShareholderBenefit(row.optString("name"), row.optString("marketValue"))
                        .takeIf { it.name.isNotBlank() || it.marketValue.isNotBlank() }
                }
                if (rows.isNotEmpty()) put(code, rows)
            }
        }
        val climatesJson = root.optJSONObject("stockClimateOverrides") ?: JSONObject()
        val climates = buildMap {
            val codes = climatesJson.keys()
            while (codes.hasNext()) {
                val rawCode = codes.next()
                val code = PortfolioMath.normalizeCode(rawCode)
                runCatching { StockClimate.valueOf(climatesJson.optString(rawCode)) }.getOrNull()?.let { put(code, it) }
            }
        }
        val historyJson = root.optJSONObject("dividendHistoryOverrides") ?: JSONObject()
        val histories = buildMap {
            val codes = historyJson.keys()
            while (codes.hasNext()) {
                val rawCode = codes.next()
                val code = PortfolioMath.normalizeCode(rawCode).removeSuffix(".T")
                val entries = historyJson.optJSONArray(rawCode) ?: continue
                val rows = (0 until entries.length()).mapNotNull { index ->
                    val row = entries.optJSONObject(index) ?: return@mapNotNull null
                    val hasPrice = row.optBoolean("stock_price_overridden")
                    val hasDividend = row.optBoolean("annual_dividend_overridden")
                    val price = if (row.isNull("stock_price")) null else row.optDouble("stock_price", Double.NaN)
                    val dividend = if (row.isNull("annual_dividend")) null else row.optDouble("annual_dividend", Double.NaN)
                    if (row.optInt("year") !in 1900..2100 || (!hasPrice && !hasDividend) ||
                        (price != null && (!price.isFinite() || price < 0 || !PortfolioMath.validMoney(price))) ||
                        (dividend != null && (!dividend.isFinite() || dividend < 0 || !PortfolioMath.validMoney(dividend)))) return@mapNotNull null
                    DividendHistoryOverride(row.optInt("year"), price, dividend, hasPrice, hasDividend)
                }.distinctBy { it.year }.sortedBy { it.year }
                if (code.matches(Regex("[A-Z0-9]{4}") ) && rows.isNotEmpty()) put(code, rows)
            }
        }
        val watchlist = if (!root.has("watchlist")) null else parseWatchlist(
            root.optJSONArray("watchlist") ?: throw IllegalArgumentException("バックアップの監視銘柄データが正しくありません")
        )
        return DvmPortableExtras(benefits, climates, histories, watchlist)
    }

    private fun parseWatchlist(items: JSONArray): List<WatchlistStock> = (0 until items.length()).map { index ->
        val item = items.optJSONObject(index) ?: throw IllegalArgumentException("バックアップの監視銘柄データが正しくありません")
        val code = PortfolioMath.normalizeCode(item.optString("code"))
        val name = item.optString("name").trim()
        val category = item.optString("category").lowercase(java.util.Locale.ROOT)
        val price = item.optDouble("price", Double.NaN)
        val dps = item.optDouble("dps", Double.NaN)
        val currentYield = item.optDouble("currentYield", Double.NaN)
        val targetYield = item.optDouble("targetYield", Double.NaN)
        val targetPrice = item.optDouble("targetPrice", Double.NaN)
        val defaultShares = item.optInt("defaultShares", 100)
        val months = item.optJSONArray("payoutMonths")
            ?: throw IllegalArgumentException("バックアップの監視銘柄データが正しくありません")
        val payoutMonths = (0 until months.length()).map { months.getInt(it) }
        val usdJpyRate = if (item.has("usdJpyRate") && !item.isNull("usdJpyRate")) item.optDouble("usdJpyRate", Double.NaN) else null
        require(PortfolioMath.validCode(code) && name.isNotBlank() && category in setOf("jp", "us") &&
            listOf(price, dps, targetPrice).all(PortfolioMath::validMoney) &&
            currentYield.isFinite() && targetYield.isFinite() && targetYield > 0.0 &&
            defaultShares > 0 && payoutMonths.all { it in 1..12 } &&
            (usdJpyRate == null || (usdJpyRate.isFinite() && usdJpyRate > 0.0))) {
            "バックアップの監視銘柄データが正しくありません"
        }
        WatchlistStock(
            code = code,
            name = name,
            market = item.optString("market"),
            sector = item.optString("sector"),
            category = category,
            price = price,
            currency = item.optString("currency", "¥"),
            dps = dps,
            currentYield = currentYield,
            targetYield = targetYield,
            targetPrice = targetPrice,
            dailyChange = item.optString("dailyChange"),
            isPositive = item.optBoolean("isPositive"),
            payoutMonths = payoutMonths,
            payoutTiming = item.optString("payoutTiming"),
            desc = item.optString("desc"),
            alertEnabled = item.optBoolean("alertEnabled"),
            badge = if (item.isNull("badge")) null else item.optString("badge"),
            defaultShares = defaultShares,
            usdJpyRate = usdJpyRate,
            isNoDividendConfirmed = item.optBoolean("isNoDividendConfirmed")
        )
    }

    private fun String.toNumber(): Double {
        if (isBlank() || this == "null") return 0.0
        val number = replace(",", "").replace("¥", "").replace("$", "").trim().toDoubleOrNull()
        require(number != null && PortfolioMath.validMoney(number)) { "バックアップの単価が正しくありません" }
        return number
    }
}
