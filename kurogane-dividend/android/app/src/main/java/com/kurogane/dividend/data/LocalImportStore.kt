package com.kurogane.dividend.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.PortfolioMath
import com.kurogane.dividend.model.WatchlistStock
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** App-private encrypted storage. Nothing in this class performs network access. */
class LocalImportStore(context: Context) {
    private val prefs = context.getSharedPreferences("local_import_store", Context.MODE_PRIVATE)
    private var cachedKey: SecretKey? = null

    fun hasHoldings(): Boolean = prefs.contains("holdings")

    fun saveHoldings(holdings: List<Holding>) = save("holdings", JSONArray().apply { holdings.forEach { put(it.toJson()) } }.toString().toByteArray())
    fun loadHoldings(): List<Holding>? = load("holdings")?.let { bytes ->
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        (0 until array.length()).map { array.getJSONObject(it).toHolding() }
    }
    fun saveBackup(kind: DvmBackupKind, plaintext: ByteArray) = save("dvm_${kind.name}", plaintext)
    fun readBackup(kind: DvmBackupKind): ByteArray? = load("dvm_${kind.name}")
    fun saveFeedbackDraft(draft: JSONObject) = save("feedback_draft", draft.toString().toByteArray(Charsets.UTF_8))
    fun loadFeedbackDraft(): JSONObject? = load("feedback_draft")?.let { JSONObject(it.toString(Charsets.UTF_8)) }

    fun clearFeedbackDraft() { check(prefs.edit().remove("feedback_draft").commit()) }

    fun clear() { check(prefs.edit().clear().commit()) { "データを初期化できませんでした" } }

    fun saveWatchlist(stocks: List<WatchlistStock>) = save("watchlist", JSONArray().apply {
        stocks.forEach { stock -> put(JSONObject().apply {
            put("code", stock.code); put("name", stock.name); put("market", stock.market); put("sector", stock.sector)
            put("category", stock.category); put("price", stock.price); put("currency", stock.currency); put("dps", stock.dps)
            put("currentYield", stock.currentYield); put("targetYield", stock.targetYield); put("targetPrice", stock.targetPrice)
            put("dailyChange", stock.dailyChange); put("isPositive", stock.isPositive); put("payoutMonths", JSONArray(stock.payoutMonths))
            put("payoutTiming", stock.payoutTiming); put("desc", stock.desc); put("alertEnabled", stock.alertEnabled)
            put("badge", stock.badge); put("defaultShares", stock.defaultShares)
        }) }
    }.toString().toByteArray(Charsets.UTF_8))

    fun loadWatchlist(): List<WatchlistStock>? = load("watchlist")?.let { bytes ->
        val array = JSONArray(bytes.toString(Charsets.UTF_8))
        (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            WatchlistStock(row.getString("code"), row.getString("name"), row.getString("market"), row.getString("sector"),
                row.getString("category"), row.getDouble("price"), row.getString("currency"), row.getDouble("dps"),
                row.getDouble("currentYield"), row.getDouble("targetYield"), row.getDouble("targetPrice"), row.getString("dailyChange"),
                row.getBoolean("isPositive"), row.getJSONArray("payoutMonths").let { a -> (0 until a.length()).map { a.getInt(it) } },
                row.getString("payoutTiming"), row.getString("desc"), row.optBoolean("alertEnabled"),
                if (row.isNull("badge")) null else row.optString("badge"), row.optInt("defaultShares", 100)).also {
                require(PortfolioMath.validCode(it.code) && listOf(it.price, it.dps, it.targetPrice).all(PortfolioMath::validMoney) &&
                    it.currentYield.isFinite() && it.targetYield.isFinite() && it.targetYield > 0) { "監視銘柄の保存データが正しくありません" }
            }
        }
    }

    private fun save(key: String, clear: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.doFinal(clear)
        check(prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).commit()) { "端末にデータを保存できませんでした" }
    }
    private fun load(key: String): ByteArray? {
        val data = prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        require(data.size >= 28) { "保存データが破損しています" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, 12))
        return cipher.doFinal(data, 12, data.size - 12)
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
        taxFreeShares?.let { put("taxFreeShares", it) }
    }
    private fun JSONObject.toHolding() = Holding(
        id = optString("id"), code = optString("code"), name = optString("name"), sector = optString("sector"), market = optString("market"),
        category = Category.valueOf(getString("category")), price = getDouble("price"),
        currency = optString("currency", "¥"), shares = requireNotNull(PortfolioMath.wholeShares(getDouble("shares"))), acqPrice = getDouble("acqPrice"), dps = getDouble("dps"),
        yieldRate = optDouble("yieldRate"), yoc = optDouble("yoc"), nextPayoutTiming = optString("nextPayoutTiming"), nextPayoutAmount = optString("nextPayoutAmount"),
        dividendMonths = optJSONArray("dividendMonths")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
        dividendType = optString("dividendType"), accountType = optString("accountType"), dailyChange = optString("dailyChange"), isPositive = optBoolean("isPositive"),
        taxFreeShares = if (has("taxFreeShares")) getInt("taxFreeShares") else null
    ).also {
        require(PortfolioMath.validCode(it.code) && it.name.isNotBlank() && it.shares >= 0 &&
            listOf(it.price, it.acqPrice, it.dps).all(PortfolioMath::validMoney) &&
            (it.taxFreeShares == null || it.taxFreeShares in 0..it.shares)) { "保存データの保有情報が正しくありません" }
    }
    private companion object {
        const val KEY_ALIAS = "diview.local.import.aes"
        val KEY_LOCK = Any()
    }
}
