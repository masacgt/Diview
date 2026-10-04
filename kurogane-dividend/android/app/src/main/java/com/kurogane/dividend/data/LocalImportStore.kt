package com.kurogane.dividend.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
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

    fun saveHoldings(holdings: List<Holding>) = save("holdings", JSONArray().apply { holdings.forEach { put(it.toJson()) } }.toString().toByteArray())
    fun loadHoldings(): List<Holding>? = load("holdings")?.let { bytes ->
        runCatching { val array = JSONArray(bytes.toString(Charsets.UTF_8)); (0 until array.length()).mapNotNull { array.optJSONObject(it)?.toHolding() } }.getOrNull()
    }
    fun saveBackup(kind: DvmBackupKind, plaintext: ByteArray) = save("dvm_${kind.name}", plaintext)
    fun readBackup(kind: DvmBackupKind): ByteArray? = load("dvm_${kind.name}")

    private fun save(key: String, clear: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.iv + cipher.doFinal(clear)
        prefs.edit().putString(key, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }
    private fun load(key: String): ByteArray? = runCatching {
        val data = prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, data, 0, 12))
        cipher.doFinal(data, 12, data.size - 12)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }

    private fun Holding.toJson() = JSONObject().apply {
        put("id", id); put("code", code); put("name", name); put("sector", sector); put("market", market); put("category", category.name)
        put("price", price); put("currency", currency); put("shares", shares); put("acqPrice", acqPrice); put("dps", dps)
        put("yieldRate", yieldRate); put("yoc", yoc); put("nextPayoutTiming", nextPayoutTiming); put("nextPayoutAmount", nextPayoutAmount)
        put("dividendMonths", JSONArray(dividendMonths)); put("dividendType", dividendType); put("accountType", accountType)
        put("dailyChange", dailyChange); put("isPositive", isPositive)
    }
    private fun JSONObject.toHolding() = Holding(
        id = optString("id"), code = optString("code"), name = optString("name"), sector = optString("sector"), market = optString("market"),
        category = runCatching { Category.valueOf(optString("category")) }.getOrDefault(Category.JAPAN), price = optDouble("price"),
        currency = optString("currency", "¥"), shares = optInt("shares"), acqPrice = optDouble("acqPrice"), dps = optDouble("dps"),
        yieldRate = optDouble("yieldRate"), yoc = optDouble("yoc"), nextPayoutTiming = optString("nextPayoutTiming"), nextPayoutAmount = optString("nextPayoutAmount"),
        dividendMonths = optJSONArray("dividendMonths")?.let { a -> (0 until a.length()).map { a.optInt(it) } } ?: emptyList(),
        dividendType = optString("dividendType"), accountType = optString("accountType"), dailyChange = optString("dailyChange"), isPositive = optBoolean("isPositive")
    )
    private companion object { const val KEY_ALIAS = "diview.local.import.aes" }
}
