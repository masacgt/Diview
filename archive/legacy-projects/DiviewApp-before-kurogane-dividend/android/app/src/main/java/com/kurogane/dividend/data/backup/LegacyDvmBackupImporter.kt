package com.kurogane.dividend.data.backup

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class LegacyBackupKind(val fileName: String, val title: String) {
    STOCKS("BackUpStockInfoAndroid.dvm", "登録銘柄"),
    RECORDS("BackUpRecordInfoAndroid.dvm", "資産・配当推移"),
    CUSTOM_STOCKS("BackUpCustomStockInfoAndroid.dvm", "カスタム銘柄"),
    YIELD_ALERTS("BackUpYieldAlertAndroid.dvm", "目標利回りアラート")
}

data class LegacyBackupPayload(
    val kind: LegacyBackupKind,
    val sourceName: String,
    val json: String,
    val records: JsonArray,
    /** Original encrypted bytes where available; plaintext JSON is encrypted before it is stored. */
    internal val originalBytes: ByteArray,
    internal val wasEncrypted: Boolean
)

class LegacyBackupFormatException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

/** Reads the legacy APK's local DVMV1 AES-GCM backups and its plain JSON exports. */
class LegacyDvmBackupImporter {
    fun read(fileName: String, bytes: ByteArray): LegacyBackupPayload {
        if (bytes.isEmpty()) throw LegacyBackupFormatException("バックアップファイルが空です。")
        val encrypted = hasDvmHeader(bytes)
        val json = try {
            val plain = if (encrypted) decrypt(bytes) else bytes
            StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(plain)).toString()
        } catch (e: Exception) {
            throw LegacyBackupFormatException("復号できません。ファイルが破損しているか、対応していない形式です。", e)
        }
        val array = try {
            JsonParser.parseString(json).takeIf { it.isJsonArray }?.asJsonArray
                ?: throw LegacyBackupFormatException("バックアップのJSON構造が正しくありません。")
        } catch (e: LegacyBackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw LegacyBackupFormatException("バックアップのJSONを読み取れません。", e)
        }
        val kind = identifyKind(fileName, array)
        validateRows(kind, array)
        return LegacyBackupPayload(kind, fileName, json, array, bytes.copyOf(), encrypted)
    }

    fun encryptedForLocalStorage(payload: LegacyBackupPayload): ByteArray =
        if (payload.wasEncrypted) payload.originalBytes.copyOf() else encrypt(payload.json.toByteArray(StandardCharsets.UTF_8))

    private fun identifyKind(fileName: String, array: JsonArray): LegacyBackupKind {
        if (array.size() > 0 && array[0].isJsonObject) {
            val fields = array[0].asJsonObject.keySet()
            return when {
                "numOfStocksHeld" in fields -> LegacyBackupKind.STOCKS
                "recordDate" in fields && "totalHaitouKin" in fields -> LegacyBackupKind.RECORDS
                "vestingMonthJson" in fields -> LegacyBackupKind.CUSTOM_STOCKS
                "alertId" in fields && "targetYieldRimawari" in fields -> LegacyBackupKind.YIELD_ALERTS
                else -> throw LegacyBackupFormatException("このバックアップの種類を判定できません。")
            }
        }
        val normalized = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        return when {
            "backupcustomstock" in normalized -> LegacyBackupKind.CUSTOM_STOCKS
            "backuprecord" in normalized || "backuptransitiongraph" in normalized -> LegacyBackupKind.RECORDS
            "backupyieldalert" in normalized -> LegacyBackupKind.YIELD_ALERTS
            "backupstockinfo" in normalized -> LegacyBackupKind.STOCKS
            else -> throw LegacyBackupFormatException("空のバックアップはファイル名から種類を判定できません。")
        }
    }

    private fun validateRows(kind: LegacyBackupKind, array: JsonArray) {
        array.forEachIndexed { index, value ->
            if (!value.isJsonObject) throw LegacyBackupFormatException("${kind.title}の${index + 1}件目がオブジェクト形式ではありません。")
            val fields = value.asJsonObject.keySet()
            val valid = when (kind) {
                LegacyBackupKind.STOCKS -> "code" in fields && "name" in fields
                LegacyBackupKind.RECORDS -> "recordDate" in fields && "totalHaitouKin" in fields
                LegacyBackupKind.CUSTOM_STOCKS -> "corporateCode" in fields || "corporateName" in fields
                LegacyBackupKind.YIELD_ALERTS -> "alertId" in fields && "targetYieldRimawari" in fields
            }
            if (!valid) throw LegacyBackupFormatException("${kind.title}の${index + 1}件目の項目が不足しています。")
        }
    }

    private fun hasDvmHeader(bytes: ByteArray): Boolean = bytes.size >= HEADER.size &&
        HEADER.indices.all { bytes[it] == HEADER[it] }

    private fun decrypt(bytes: ByteArray): ByteArray {
        if (!hasDvmHeader(bytes) || bytes.size < HEADER.size + NONCE_BYTES + TAG_BITS / 8) {
            throw LegacyBackupFormatException("DVMバックアップのヘッダーまたは長さが不正です。")
        }
        val nonceStart = HEADER.size
        val cipherStart = nonceStart + NONCE_BYTES
        val nonce = bytes.copyOfRange(nonceStart, cipherStart)
        val encrypted = bytes.copyOfRange(cipherStart, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, GCMParameterSpec(TAG_BITS, nonce))
        return try {
            cipher.doFinal(encrypted)
        } catch (e: AEADBadTagException) {
            throw LegacyBackupFormatException("認証タグが一致しません。ファイルが破損している可能性があります。", e)
        }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, GCMParameterSpec(TAG_BITS, nonce))
        return HEADER + nonce + cipher.doFinal(plain)
    }

    private companion object {
        val HEADER = "DVMV1".toByteArray(StandardCharsets.UTF_8)
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        val SECRET_KEY = SecretKeySpec(
            byteArrayOf(68, 118, 77, 103, 83, 121, 110, 99, 65, 69, 83, 50, 53, 54, 71, 67, 77, 75, 101, 121, 50, 48, 50, 54, 79, 116, 116, 105, 76, 97, 98, 33),
            "AES"
        )
    }
}

internal fun JsonObject.string(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString
