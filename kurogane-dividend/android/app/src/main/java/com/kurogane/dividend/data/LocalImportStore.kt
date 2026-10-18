package com.kurogane.dividend.data

import android.content.Context
import android.app.backup.BackupManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.ShareholderBenefit
import com.kurogane.dividend.model.StockClimate
import com.kurogane.dividend.model.WatchlistStock
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.io.File
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private encrypted storage. Nothing in this class performs network access. */
class LocalImportStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("local_import_store", Context.MODE_PRIVATE)
    private val autoBackup = AutoBackupSnapshot(File(appContext.filesDir, "diview_auto_backup.json"))
    private var cachedKey: SecretKey? = null

    fun hasHoldings(): Boolean = prefs.contains("holdings") || autoBackup.exists()

    fun saveHoldings(holdings: List<Holding>) {
        save("holdings", JSONArray().apply { holdings.forEach { put(it.toJson()) } }.toString().toByteArray())
        writeAutoBackupSnapshot(holdings, loadWatchlist().orEmpty())
    }
    fun loadHoldings(): List<Holding>? = load("holdings")?.let { bytes ->
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        (0 until array.length()).map { array.getJSONObject(it).toHolding() }
    } ?: autoBackup.readHoldings()?.let { array ->
        (0 until array.length()).map { array.getJSONObject(it).toHolding() }.also { restored ->
            save("holdings", JSONArray().apply { restored.forEach { put(it.toJson()) } }.toString().toByteArray())
        }
    }
    fun saveBackup(kind: DvmBackupKind, plaintext: ByteArray) {
        save("dvm_${kind.name}", plaintext)
        writeAutoBackupSnapshot(loadHoldings().orEmpty(), loadWatchlist().orEmpty())
    }
    fun readBackup(kind: DvmBackupKind): ByteArray? = load("dvm_${kind.name}")
        ?: autoBackup.readBackup(kind)?.also { bytes ->
            save("dvm_${kind.name}", bytes)
        }
    fun saveDriveImportUris(uris: List<String>) {
        save("drive_import_uris", JSONArray(uris.distinct()).toString().toByteArray(Charsets.UTF_8))
    }
    fun loadDriveImportUris(): List<String> = load("drive_import_uris")?.let { bytes ->
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        (0 until array.length()).map { array.getString(it) }
    }.orEmpty()
    fun saveFeedbackDraft(draft: JSONObject) = save("feedback_draft", draft.toString().toByteArray(Charsets.UTF_8))
    fun loadFeedbackDraft(): JSONObject? = load("feedback_draft")?.let { JSONObject(it.toString(Charsets.UTF_8)) }

    fun saveShareholderBenefits(benefitsByCode: Map<String, List<ShareholderBenefit>>) {
        val json = JSONObject().apply {
            benefitsByCode.forEach { (code, benefits) ->
                put(code, JSONArray().apply {
                    benefits.forEach { benefit ->
                        put(JSONObject().put("name", benefit.name).put("marketValue", benefit.marketValue))
                    }
                })
            }
        }
        save("shareholder_benefits", json.toString().toByteArray(Charsets.UTF_8))
        writeAutoBackupSnapshot(loadHoldings().orEmpty(), loadWatchlist().orEmpty())
    }

    fun loadShareholderBenefits(): Map<String, List<ShareholderBenefit>> =
        loadPortableOverride("shareholder_benefits")?.let { bytes ->
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            buildMap {
                val codes = json.keys()
                while (codes.hasNext()) {
                    val code = codes.next()
                    val entries = json.optJSONArray(code) ?: continue
                    put(code, (0 until entries.length()).map { index ->
                        val item = entries.getJSONObject(index)
                        ShareholderBenefit(item.optString("name"), item.optString("marketValue"))
                    })
                }
            }
        }.orEmpty()

    fun saveStockClimateOverrides(overrides: Map<String, StockClimate>) {
        val json = JSONObject().apply { overrides.forEach { (code, climate) -> put(code, climate.name) } }
        save("stock_climate_overrides", json.toString().toByteArray(Charsets.UTF_8))
        writeAutoBackupSnapshot(loadHoldings().orEmpty(), loadWatchlist().orEmpty())
    }

    fun loadStockClimateOverrides(): Map<String, StockClimate> =
        loadPortableOverride("stock_climate_overrides")?.let { bytes ->
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            buildMap {
                val codes = json.keys()
                while (codes.hasNext()) {
                    val code = codes.next()
                    runCatching { StockClimate.valueOf(json.getString(code)) }.getOrNull()?.let { put(code, it) }
                }
            }
        }.orEmpty()

    fun saveDividendHistoryOverrides(overridesByCode: Map<String, List<DividendHistoryOverride>>) {
        val json = JSONObject().apply {
            overridesByCode.forEach { (rawCode, overrides) ->
                val code = rawCode.trim().uppercase(java.util.Locale.ROOT).removeSuffix(".T")
                if (!code.matches(Regex("[A-Z0-9]{4}"))) return@forEach
                put(code, JSONArray().apply {
                    overrides.filter { it.year in 1900..2100 && (it.hasStockPriceOverride || it.hasAnnualDividendOverride) }
                        .sortedBy { it.year }
                        .forEach { row ->
                            put(JSONObject().apply {
                                put("year", row.year)
                                put("stock_price_overridden", row.hasStockPriceOverride)
                                put("stock_price", if (row.stockPrice == null) JSONObject.NULL else row.stockPrice)
                                put("annual_dividend_overridden", row.hasAnnualDividendOverride)
                                put("annual_dividend", if (row.annualDividend == null) JSONObject.NULL else row.annualDividend)
                            })
                        }
                })
            }
        }
        save("dividend_history_overrides", json.toString().toByteArray(Charsets.UTF_8))
        writeAutoBackupSnapshot(loadHoldings().orEmpty(), loadWatchlist().orEmpty())
    }

    fun loadDividendHistoryOverrides(): Map<String, List<DividendHistoryOverride>> =
        loadPortableOverride("dividend_history_overrides")?.let { bytes ->
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            buildMap {
                val codes = json.keys()
                while (codes.hasNext()) {
                    val rawCode = codes.next()
                    val code = rawCode.trim().uppercase(java.util.Locale.ROOT).removeSuffix(".T")
                    if (!code.matches(Regex("[A-Z0-9]{4}"))) continue
                    val rows = json.optJSONArray(rawCode) ?: continue
                    put(code, (0 until rows.length()).mapNotNull { index ->
                        val row = rows.optJSONObject(index) ?: return@mapNotNull null
                        val hasPrice = row.optBoolean("stock_price_overridden")
                        val hasDividend = row.optBoolean("annual_dividend_overridden")
                        val price = if (row.isNull("stock_price")) null else row.optDouble("stock_price", Double.NaN)
                            .takeIf { it.isFinite() && it >= 0.0 && PortfolioMath.validMoney(it) }
                        val dividend = if (row.isNull("annual_dividend")) null else row.optDouble("annual_dividend", Double.NaN)
                            .takeIf { it.isFinite() && it >= 0.0 && PortfolioMath.validMoney(it) }
                        if (row.optInt("year") !in 1900..2100 || (!hasPrice && !hasDividend)) null
                        else DividendHistoryOverride(row.optInt("year"), price, dividend, hasPrice, hasDividend)
                    }.sortedBy { it.year })
                }
            }
        }.orEmpty()

    fun clearFeedbackDraft() { check(prefs.edit().remove("feedback_draft").commit()) }

    fun clear() {
        check(prefs.edit().clear().commit()) { "データを初期化できませんでした" }
        autoBackup.delete()
        BackupManager(appContext).dataChanged()
    }

    fun saveWatchlist(stocks: List<WatchlistStock>) {
        save("watchlist", JSONArray().apply { stocks.forEach { put(it.toJson()) } }
            .toString().toByteArray(Charsets.UTF_8))
        writeAutoBackupSnapshot(loadHoldings().orEmpty(), stocks)
    }

    fun loadWatchlist(): List<WatchlistStock>? = load("watchlist")?.let { bytes ->
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            row.toWatchlistStock()
        }
    } ?: autoBackup.readWatchlist()?.let { array ->
        (0 until array.length()).map { array.getJSONObject(it).toWatchlistStock() }.also { restored ->
            save("watchlist", JSONArray().apply { restored.forEach { put(it.toJson()) } }.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun writeAutoBackupSnapshot(holdings: List<Holding>, watchlist: List<WatchlistStock>) {
        // Recover transferred payloads before AtomicFile starts a replacement. In particular,
        // API 26 openRead() must not recover its .bak file in the middle of startWrite().
        DvmBackupKind.entries.filterNot { prefs.contains("dvm_${it.name}") }.forEach { readBackup(it) }
        autoBackup.write(
            JSONArray().apply { holdings.forEach { put(it.toJson()) } },
            JSONArray().apply { watchlist.forEach { put(it.toJson()) } },
            overrides = JSONObject().apply {
                PORTABLE_OVERRIDE_KEYS.forEach { key ->
                    put(key, loadPortableOverride(key)?.let { JSONObject(it.toString(Charsets.UTF_8)) } ?: JSONObject())
                }
            },
            backup = { kind -> load("dvm_${kind.name}") }
        )
        BackupManager(appContext).dataChanged()
    }

    private fun loadPortableOverride(key: String): ByteArray? {
        load(key)?.let { return it }
        // Restore the complete optional group before any replacement snapshot is started.
        // Older version-one snapshots without the group intentionally retain empty defaults.
        val transferred = autoBackup.readOverrides() ?: return null
        PORTABLE_OVERRIDE_KEYS.filterNot(prefs::contains).forEach { name ->
            transferred.optJSONObject(name)?.let { json -> save(name, json.toString().toByteArray(Charsets.UTF_8)) }
        }
        return load(key)
    }

    private fun WatchlistStock.toJson() = JSONObject().apply {
        put("code", code); put("name", name); put("market", market); put("sector", sector); put("category", category)
        put("price", price); put("currency", currency); put("dps", dps); put("currentYield", currentYield)
        put("targetYield", targetYield); put("targetPrice", targetPrice); put("dailyChange", dailyChange)
        put("isPositive", isPositive); put("payoutMonths", JSONArray(payoutMonths)); put("payoutTiming", payoutTiming)
        put("desc", desc); put("alertEnabled", alertEnabled); put("badge", badge); put("defaultShares", defaultShares)
        put("isNoDividendConfirmed", isNoDividendConfirmed)
    }

    private fun JSONObject.toWatchlistStock() = WatchlistStock(
        getString("code"), getString("name"), getString("market"), getString("sector"), getString("category"),
        getDouble("price"), getString("currency"), getDouble("dps"), getDouble("currentYield"), getDouble("targetYield"),
        getDouble("targetPrice"), getString("dailyChange"), getBoolean("isPositive"),
        getJSONArray("payoutMonths").let { months -> (0 until months.length()).map { months.getInt(it) } },
        getString("payoutTiming"), getString("desc"), optBoolean("alertEnabled"),
        if (isNull("badge")) null else optString("badge"), optInt("defaultShares", 100),
        isNoDividendConfirmed = optBoolean("isNoDividendConfirmed", false)
    ).also {
        require(PortfolioMath.validCode(it.code) && listOf(it.price, it.dps, it.targetPrice).all(PortfolioMath::validMoney) &&
            it.currentYield.isFinite() && it.targetYield.isFinite() && it.targetYield > 0) { "監視銘柄の保存データが正しくありません" }
    }

    private fun save(key: String, clear: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.processChunks(clear)
        check(prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) { "端末にデータを保存できませんでした" }
    }
    private fun load(key: String): ByteArray? {
        val data = prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        require(data.size >= 28) { "保存データが破損しています" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, 12))
        return cipher.processChunks(data, 12, data.size - 12)
    }

    private fun Cipher.processChunks(input: ByteArray, offset: Int = 0, length: Int = input.size): ByteArray {
        // Android 8.0's Keystore streamer can drop intermediate output when one call
        // spans multiple 64 KiB chunks. Keep the same IV/ciphertext/tag format and
        // collect every update; doFinal still authenticates the complete message.
        val output = ByteArrayOutputStream(length + 16)
        var position = offset
        val end = offset + length
        while (position < end) {
            val count = minOf(64 * 1024, end - position)
            update(input, position, count)?.let(output::write)
            position += count
        }
        output.write(doFinal())
        return output.toByteArray()
    }

    private fun secretKey(): SecretKey = synchronized(KEY_LOCK) {
        cachedKey?.let { return it }
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { cachedKey = it; return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey().also { cachedKey = it }
    }

    private fun Holding.toJson() = JSONObject().apply {
        put("id", id); put("code", code); put("name", name); put("sector", sector); put("market", market); put("category", category.name)
        put("price", price); put("currency", currency); put("shares", shares); put("acqPrice", acqPrice); put("dps", dps)
        put("yieldRate", yieldRate); put("yoc", yoc); put("nextPayoutTiming", nextPayoutTiming); put("nextPayoutAmount", nextPayoutAmount)
        put("dividendMonths", JSONArray(dividendMonths)); put("dividendType", dividendType); put("accountType", accountType)
        put("dailyChange", dailyChange); put("isPositive", isPositive)
        put("isNoDividendConfirmed", isNoDividendConfirmed)
        taxFreeShares?.let { put("taxFreeShares", it) }
    }
    private fun JSONObject.toHolding() = Holding(
        id = optString("id"), code = optString("code"), name = optString("name"), sector = optString("sector"), market = optString("market"),
        category = Category.valueOf(getString("category")), price = getDouble("price"),
        currency = optString("currency", "¥"), shares = requireNotNull(PortfolioMath.wholeShares(getDouble("shares"))), acqPrice = getDouble("acqPrice"), dps = getDouble("dps"),
        yieldRate = optDouble("yieldRate"), yoc = optDouble("yoc"), nextPayoutTiming = optString("nextPayoutTiming"), nextPayoutAmount = optString("nextPayoutAmount"),
        dividendMonths = optJSONArray("dividendMonths")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
        dividendType = optString("dividendType"), accountType = optString("accountType"), dailyChange = optString("dailyChange"), isPositive = optBoolean("isPositive"),
        taxFreeShares = if (has("taxFreeShares")) getInt("taxFreeShares") else null,
        isNoDividendConfirmed = optBoolean("isNoDividendConfirmed", false)
    ).also {
        require(PortfolioMath.validCode(it.code) && it.name.isNotBlank() && it.shares >= 0 &&
            listOf(it.price, it.acqPrice, it.dps).all(PortfolioMath::validMoney) &&
            (it.taxFreeShares == null || it.taxFreeShares in 0..it.shares)) { "保存データの保有情報が正しくありません" }
    }
    private companion object {
        val PORTABLE_OVERRIDE_KEYS = listOf("shareholder_benefits", "stock_climate_overrides", "dividend_history_overrides")
        const val KEY_ALIAS = "diview.local.import.aes"
        val KEY_LOCK = Any()
    }
}
