package com.kurogane.dividend.data.backup

import android.content.Context
import com.google.gson.JsonObject
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Stores only DVM-encrypted backup blobs in app-private storage. */
class LegacyBackupStore(context: Context) {
    private val directory = File(context.filesDir, "legacy-backups")
    private val importer = LegacyDvmBackupImporter()

    fun save(payloads: List<LegacyBackupPayload>) {
        require(payloads.map { it.kind }.distinct().size == payloads.size) {
            "同じ種類のバックアップが複数選ばれています。"
        }
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("端末内に保存先を作成できません。")
        val staged = payloads.map { payload ->
            val target = File(directory, payload.kind.fileName)
            val temp = File(directory, "${payload.kind.fileName}.tmp")
            FileOutputStream(temp).use { stream ->
                stream.write(importer.encryptedForLocalStorage(payload))
                stream.fd.sync()
            }
            temp to target
        }
        try {
            staged.forEach { (temp, target) ->
                if (target.exists() && !target.delete()) throw IllegalStateException("既存バックアップを更新できません。")
                if (!temp.renameTo(target)) throw IllegalStateException("バックアップを端末内に保存できません。")
            }
        } finally {
            staged.forEach { (temp, _) -> if (temp.exists()) temp.delete() }
        }
    }

    fun loadSaved(): List<LegacyBackupPayload> {
        if (!directory.isDirectory) return emptyList()
        return LegacyBackupKind.entries.mapNotNull { kind ->
            val file = File(directory, kind.fileName)
            if (!file.isFile) null else importer.read(file.name, file.readBytes())
        }
    }
}

fun LegacyBackupPayload.toHoldings(): List<Holding> {
    if (kind != LegacyBackupKind.STOCKS) return emptyList()
    return records.mapNotNull { element ->
        val row = element.asJsonObject
        val code = row.text("code")
        val name = row.text("name")
        if (code.isBlank() || name.isBlank()) return@mapNotNull null

        val isUs = row.bool("isUSD") || row.text("country").contains("米国") ||
            row.text("country").contains("アメリカ") || code.matches(Regex("[A-Za-z]{1,6}(\\.[A-Za-z])?"))
        val quantities = listOf(
            "numOfStocksHeld" to "普通口座/特定口座",
            "numOfStocksHeldNisa" to "旧NISA",
            "numOfStocksHeldNisaNew" to "新NISA成長枠",
            "numOfStocksHeldNisaNewTsumitate" to "新NISAつみたて投資枠",
            "numOfStocksHeldIdeco" to "iDeCo"
        ).mapNotNull { (field, account) ->
            val shares = row.number(field)
            if (shares > 0) Triple(shares, account, field) else null
        }
        val shares = quantities.sumOf { it.first }.toInt().takeIf { it > 0 } ?: return@mapNotNull null
        val price = row.number("priceOfStock")
        val dps = row.number("numOfHaitouCustom").takeIf { it > 0.0 } ?: row.number("numOfHaitou")
        val yield = row.number("yieldRimawari").takeIf { it > 0.0 } ?: row.number("yield")
        val acquisitionFields = mapOf(
            "numOfStocksHeld" to if (isUs) "acquisitionUnitPriceUSD" else "acquisitionUnitPrice",
            "numOfStocksHeldNisa" to if (isUs) "acquisitionUnitPriceUSDNisa" else "acquisitionUnitPriceNisa",
            "numOfStocksHeldNisaNew" to if (isUs) "acquisitionUnitPriceUSDNisaNew" else "acquisitionUnitPriceNisaNew",
            "numOfStocksHeldNisaNewTsumitate" to if (isUs) "acquisitionUnitPriceUSDNisaNewTsumitate" else "acquisitionUnitPriceNisaNewTsumitate",
            "numOfStocksHeldIdeco" to "acquisitionUnitPriceIdeco"
        )
        val weightedCost = quantities.sumOf { (qty, _, qtyField) -> qty * row.number(acquisitionFields.getValue(qtyField)) } / shares
        val months = Regex("\\d{1,2}").findAll(row.text("monthOfHaitouVesting").ifBlank { row.text("monthOfHaitou") })
            .mapNotNull { it.value.toIntOrNull()?.takeIf { month -> month in 1..12 } }
            .distinct().sorted().toList()
        val currency = if (isUs) "$" else "¥"
        val accountTypes = quantities.map { it.second }.distinct()
        val dividendMonthLabel = months.joinToString("月 / ") + if (months.isNotEmpty()) "月" else "未設定"

        Holding(
            id = "legacy-$code",
            code = code,
            name = name,
            sector = row.text("sector").ifBlank { if (isUs) "米国株" else "日本株" },
            market = row.text("country").ifBlank { if (isUs) "米国市場" else "国内市場" },
            category = if (isUs) Category.US else Category.JAPAN,
            price = price,
            currency = currency,
            shares = shares,
            acqPrice = weightedCost,
            dps = dps,
            yieldRate = yield,
            yoc = if (weightedCost > 0) dps / weightedCost * 100 else 0.0,
            nextPayoutTiming = dividendMonthLabel,
            nextPayoutAmount = "${currency}${(shares * dps / 2).toLong()}",
            dividendMonths = months,
            dividendType = row.text("haitouShikyu1").ifBlank { "配当" },
            accountType = accountTypes.joinToString("・").ifBlank { "口座不明" },
            dailyChange = "—",
            isPositive = true
        )
    }
}

private fun JsonObject.text(key: String): String {
    val value = get(key) ?: return ""
    return if (value.isJsonNull) "" else runCatching { value.asString }.getOrDefault(value.toString().trim('"'))
}

private fun JsonObject.bool(key: String): Boolean = runCatching { get(key)?.asBoolean == true }.getOrDefault(false)

private fun JsonObject.number(key: String): Double {
    val raw = text(key).replace(",", "").replace("−", "-")
    val cleaned = Regex("-?\\d+(?:\\.\\d+)?").find(raw)?.value ?: return 0.0
    return cleaned.toDoubleOrNull() ?: 0.0
}
