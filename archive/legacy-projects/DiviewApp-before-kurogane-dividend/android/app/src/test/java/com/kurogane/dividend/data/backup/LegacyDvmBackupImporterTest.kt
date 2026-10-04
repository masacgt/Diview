package com.kurogane.dividend.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDvmBackupImporterTest {
    private val importer = LegacyDvmBackupImporter()

    @Test
    fun readsPlainLegacyJsonAndDetectsKindFromFields() {
        val json = """[{"code":"7203","name":"Toyota","numOfStocksHeld":100}]"""
        val payload = importer.read("export.dvm", json.toByteArray())

        assertEquals(LegacyBackupKind.STOCKS, payload.kind)
        assertEquals(1, payload.records.size())
    }

    @Test
    fun encryptedLocalCopyCanBeDecryptedAgain() {
        val json = """[{"recordDate":"2025-01-01","totalHaitouKin":1200}]"""
        val parsed = importer.read("history.json", json.toByteArray())
        val encrypted = importer.encryptedForLocalStorage(parsed)

        assertTrue(encrypted.copyOfRange(0, 5).contentEquals("DVMV1".toByteArray()))
        val restored = importer.read("history.dvm", encrypted)
        assertEquals(LegacyBackupKind.RECORDS, restored.kind)
        assertEquals(json, restored.json)
    }

    @Test
    fun rejectsTamperedEncryptedBackup() {
        val json = """[{"recordDate":"2025-01-01","totalHaitouKin":1200}]"""
        val encrypted = importer.encryptedForLocalStorage(importer.read("history.json", json.toByteArray()))
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        assertThrows(LegacyBackupFormatException::class.java) {
            importer.read("history.dvm", encrypted)
        }
    }

    @Test
    fun mapsLegacyStockFieldsIntoHolding() {
        val json = """[{"code":"7203","name":"Toyota","country":"日本","numOfStocksHeld":100,"priceOfStock":2600,"acquisitionUnitPrice":2400,"numOfHaitou":75,"yieldRimawari":2.88,"monthOfHaitou":"3,9"}]"""
        val payload = importer.read(LegacyBackupKind.STOCKS.fileName, json.toByteArray())
        val holding = payload.toHoldings().single()

        assertEquals("7203", holding.code)
        assertEquals(100, holding.shares)
        assertEquals(2400.0, holding.acqPrice, 0.0)
        assertEquals(75.0, holding.dps, 0.0)
        assertEquals(listOf(3, 9), holding.dividendMonths)
    }
}
