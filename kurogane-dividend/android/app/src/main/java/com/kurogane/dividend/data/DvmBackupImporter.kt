package com.kurogane.dividend.data

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class DvmBackupKind(val fileName: String, val label: String) {
    STOCKS("BackUpStockInfoAndroid.dvm", "銘柄一覧"),
    RECORDS("BackUpRecordInfoAndroid.dvm", "資産・配当推移"),
    CUSTOM_STOCKS("BackUpCustomStockInfoAndroid.dvm", "カスタム銘柄"),
    YIELD_ALERTS("BackUpYieldAlertAndroid.dvm", "目標利回りアラート");

    companion object {
        fun fromName(name: String): DvmBackupKind? = entries.firstOrNull { name.substringBeforeLast('.').equals(it.fileName.substringBeforeLast('.'), true) }
    }
}

data class DvmStock(val code: String, val name: String, val shares: Double, val averageCost: Double, val currentPrice: Double, val account: String)
data class DvmBackupPreview(val kind: DvmBackupKind, val itemCount: Int, val stocks: List<DvmStock>, val payload: ByteArray)

object DvmBackupImporter {
    private val magic = "DVMV1".toByteArray(StandardCharsets.US_ASCII)
    // This matches the AES-256-GCM key used by the legacy DVM backup format.
    private val legacyKey = byteArrayOf(68,118,77,103,83,121,110,99,65,69,83,50,53,54,71,67,77,75,101,121,50,48,50,54,79,116,116,105,76,97,98,33)

    fun decrypt(file: ByteArray): ByteArray {
        require(file.size >= magic.size + 12 + 16 && magic.indices.all { file[it] == magic[it] }) { "DVMバックアップの形式が正しくありません" }
        val nonce = file.copyOfRange(magic.size, magic.size + 12)
        val cipherText = file.copyOfRange(magic.size + 12, file.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(legacyKey, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(cipherText)
    }

    fun parse(fileName: String, bytes: ByteArray): DvmBackupPreview {
        val kind = requireNotNull(DvmBackupKind.fromName(fileName)) { "未対応のDVMバックアップです" }
        val plain = decrypt(bytes)
        val text = plain.toString(StandardCharsets.UTF_8).trim()
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
                }
                keys.firstNotNullOfOrNull { key -> root.optJSONArray(key) } ?: JSONArray()
            }
            else -> throw IllegalArgumentException("バックアップの中身を読み取れません")
        }
        val stocks = if (kind == DvmBackupKind.STOCKS) (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val holdingFields = listOf(
                "numOfStocksHeld" to "acquisitionUnitPrice", "numOfStocksHeldNisa" to "acquisitionUnitPriceNisa",
                "numOfStocksHeldNisaNew" to "acquisitionUnitPriceNisaNew", "numOfStocksHeldNisaNewTsumitate" to "acquisitionUnitPriceNisaNewTsumitate",
                "numOfStocksHeldIdeco" to "acquisitionUnitPriceIdeco"
            )
            val positions = holdingFields.map { (quantityKey, costKey) ->
                item.optDouble(quantityKey, 0.0).coerceAtLeast(0.0) to item.optString(costKey).toNumber()
            }.filter { it.first > 0.0 }
            val shares = positions.sumOf { it.first }
            val averageCost = if (shares > 0) positions.sumOf { (quantity, cost) -> quantity * cost } / shares else item.optString("acquisitionUnitPrice").toNumber()
            DvmStock(
                code = item.optString("corporateCode").trim(), name = item.optString("corporateName").trim(), shares = shares,
                averageCost = averageCost, currentPrice = item.optString("priceOfStock").toNumber(),
                account = when {
                    item.optInt("isUSD", 0) == 1 -> "米国株"
                    positions.size > 1 -> "複数口座"
                    positions.isNotEmpty() && holdingFields[holdingFields.indexOfFirst { (key, _) -> item.optDouble(key, 0.0) > 0 }].first.contains("Nisa") -> "NISA"
                    else -> "特定口座"
                }
            ).takeIf { it.code.isNotBlank() && it.name.isNotBlank() }
        } else emptyList()
        return DvmBackupPreview(kind, items.length(), stocks, plain)
    }

    private fun String.toNumber() = replace(",", "").replace("¥", "").replace("$", "").toDoubleOrNull() ?: 0.0
}
