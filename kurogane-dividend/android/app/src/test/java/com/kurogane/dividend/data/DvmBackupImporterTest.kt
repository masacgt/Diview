package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test

class DvmBackupImporterTest {
    private fun encrypt(text: String): ByteArray {
        val key = "DvMgSyncAES256GCMKey2026OttiLab!".toByteArray(Charsets.US_ASCII)
        val nonce = ByteArray(12) { it.toByte() }
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, nonce))
        return "DVMV1".toByteArray(Charsets.US_ASCII) + nonce + cipher.doFinal(text.toByteArray())
    }

    @Test fun tamperedAndTruncatedBackupsFailAuthentication() {
        val file = encrypt("[]")
        file[file.lastIndex] = (file.last().toInt() xor 1).toByte()
        assertThrows(javax.crypto.AEADBadTagException::class.java) { DvmBackupImporter.decrypt(file) }
        assertThrows(IllegalArgumentException::class.java) { DvmBackupImporter.decrypt(file.take(20).toByteArray()) }
    }

    @Test fun parsesWeightedCostAndNisaSharesAcrossAccounts() {
        val text = """[{"corporateCode":"7203","corporateName":"トヨタ","numOfStocksHeld":100,"acquisitionUnitPrice":"1,000","numOfStocksHeldNisaNew":200,"acquisitionUnitPriceNisaNew":"2500","priceOfStock":"3000"}]"""
        val preview = DvmBackupImporter.parse(DvmBackupKind.STOCKS.fileName, encrypt(text))
        assertEquals(1, preview.itemCount)
        assertEquals(300.0, preview.stocks.single().shares, 0.0)
        assertEquals(2000.0, preview.stocks.single().averageCost, 0.0)
        assertEquals(200.0, preview.stocks.single().taxFreeShares!!, 0.0)
    }

    @Test fun rejectsInvalidPayloadStructureAndInvalidNumbers() {
        listOf("not-json", "{\"unexpected\":[]}", """[{"corporateCode":"7203","corporateName":"トヨタ","numOfStocksHeld":-1}]""",
            """[{"corporateCode":"7203","corporateName":"トヨタ","numOfStocksHeld":100,"priceOfStock":"NaN"}]""").forEach { body ->
            assertThrows(IllegalArgumentException::class.java) { DvmBackupImporter.parse(DvmBackupKind.STOCKS.fileName, encrypt(body)) }
        }
    }
    @Test fun decryptsValidLegacyPayloadAndCountsRows() {
        val fixture = "44564D5631000102030405060708090A0B7CC569ACA4B657E9E16F8323CF5EBCE45E43"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val result = DvmBackupImporter.decrypt(fixture)
        org.junit.Assert.assertEquals("[]", result.toString(Charsets.UTF_8))
    }

    @Test fun rejectsUnknownMagicBeforeDecryption() {
        assertThrows(IllegalArgumentException::class.java) {
            DvmBackupImporter.parse("BackUpStockInfoAndroid.dvm", ByteArray(40))
        }
    }

    @Test fun rejectsUnknownBackupType() {
        assertThrows(IllegalArgumentException::class.java) {
            DvmBackupImporter.parse("not-a-backup.dvm", ByteArray(40))
        }
    }
}
