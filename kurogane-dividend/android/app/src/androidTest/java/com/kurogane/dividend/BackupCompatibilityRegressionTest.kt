package com.kurogane.dividend

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurogane.dividend.data.DvmBackupImporter
import com.kurogane.dividend.data.DvmBackupKind
import com.kurogane.dividend.data.LocalImportStore
import com.kurogane.dividend.data.AutoBackupSnapshot
import com.kurogane.dividend.model.Category
import com.kurogane.dividend.model.Holding
import com.kurogane.dividend.model.WatchlistStock
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Only synthetic files in AuditApplication's isolated storage are modified. */
@RunWith(AndroidJUnit4::class)
class BackupCompatibilityRegressionTest {
    private val application = AuditApplication()
    private val snapshot get() = File(application.filesDir, "diview_auto_backup.json")

    @After fun cleanUp() = application.cleanUp()

    private fun holding(index: Int = 0) = Holding(
        id = "backup-$index", code = "ZTEST$index", name = "復元検証銘柄 $index",
        sector = "検証", market = "東証", category = Category.JAPAN,
        price = 2000.0, shares = 100, acqPrice = 1000.0, dps = 80.0,
        yieldRate = 4.0, yoc = 8.0, nextPayoutTiming = "6月", nextPayoutAmount = "¥4,000",
        dividendMonths = listOf(6, 12), dividendType = "配当金", accountType = "特定口座",
        dailyChange = "—", isPositive = true, taxFreeShares = 40
    )

    private fun watchlist() = WatchlistStock(
        code = "ZBACKUP", name = "無配の復元検証", market = "東証", sector = "検証", category = "jp",
        price = 100.0, dps = 0.0, currentYield = 0.0, targetYield = 4.0, targetPrice = 0.0,
        dailyChange = "—", isPositive = true, payoutMonths = emptyList(), payoutTiming = "",
        desc = "", isNoDividendConfirmed = true
    )

    private fun removeDeviceEncryptedPreferences() {
        assertTrue(application.getSharedPreferences("local_import_store", Context.MODE_PRIVATE).edit().clear().commit())
    }

    @Test fun noDividendConfirmationSurvivesEncryptedSaveAndSnapshotRestore() {
        val store = LocalImportStore(application)
        store.saveWatchlist(listOf(watchlist()))
        assertEquals(listOf(watchlist()), LocalImportStore(application).loadWatchlist())
        removeDeviceEncryptedPreferences()
        assertEquals(listOf(watchlist()), LocalImportStore(application).loadWatchlist())
    }

    @Test fun snapshotLargerThanTenMiBRestoresHoldingsWatchlistAndLegacyPayload() {
        val store = LocalImportStore(application)
        val payload = ByteArray(8 * 1024 * 1024 + 1) { ' '.code.toByte() }
        payload[0] = '['.code.toByte()
        payload[payload.lastIndex] = ']'.code.toByte()
        store.saveHoldings(listOf(holding()))
        store.saveWatchlist(listOf(watchlist()))
        store.saveBackup(DvmBackupKind.RECORDS, payload)
        assertTrue("Fixture must exceed the previous read limit", snapshot.length() > 10L * 1024 * 1024)
        removeDeviceEncryptedPreferences()
        val restored = LocalImportStore(application)
        assertEquals(listOf(holding()), restored.loadHoldings())
        assertEquals(listOf(watchlist()), restored.loadWatchlist())
        assertTrue("The complete imported payload must be retained", payload.contentEquals(restored.readBackup(DvmBackupKind.RECORDS)))
    }

    @Test fun malformedSnapshotIsReportedWithoutChangingTheFile() {
        for (json in listOf("{\"version\":1,\"holdings\":[", "{\"version\":2,\"holdings\":[]}")) {
            snapshot.writeText(json, Charsets.UTF_8)
            val original = snapshot.readBytes()
            assertThrows(IOException::class.java) { LocalImportStore(application).loadHoldings() }
            assertTrue(original.contentEquals(snapshot.readBytes()))
        }
    }

    @Test fun manyHoldingsAndTheExistingVersionOneFormatRemainReadable() {
        val holdings = (0 until 2_000).map(::holding)
        LocalImportStore(application).saveHoldings(holdings)
        val json = JSONObject(snapshot.readText(Charsets.UTF_8))
        assertEquals(1, json.getInt("version"))
        assertEquals(holdings.size, json.getJSONArray("holdings").length())
        assertTrue(json.has("watchlist"))
        assertTrue(json.has("dvmBackups"))
        removeDeviceEncryptedPreferences()
        assertEquals(holdings, LocalImportStore(application).loadHoldings())
    }

    @Test fun theExistingEncryptedLegacyFixtureStillImports() {
        val fixture = "44564D5631000102030405060708090A0B7CC569ACA4B657E9E16F8323CF5EBCE45E43"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val preview = DvmBackupImporter.parse(DvmBackupKind.STOCKS.fileName, fixture)
        assertEquals(0, preview.itemCount)
        assertEquals("[]", preview.payload.toString(Charsets.UTF_8))
    }

    @Test fun aFailedSnapshotReplacementKeepsThePreviousCompleteBackup() {
        val store = AutoBackupSnapshot(snapshot)
        store.write(JSONArray().put(JSONObject().put("id", "original")), JSONArray()) { null }
        val original = snapshot.readBytes()
        assertThrows(IOException::class.java) {
            store.write(JSONArray(), JSONArray()) { throw IOException("synthetic write failure") }
        }
        assertTrue(original.contentEquals(snapshot.readBytes()))
        assertEquals("original", store.readHoldings()!!.getJSONObject(0).getString("id"))
    }

    @Test fun savingImmediatelyAfterTransferRetainsAllFourLegacyPayloads() {
        val original = LocalImportStore(application)
        original.saveHoldings(listOf(holding()))
        DvmBackupKind.entries.forEach { original.saveBackup(it, "[${it.ordinal}]".toByteArray()) }
        removeDeviceEncryptedPreferences()
        LocalImportStore(application).saveHoldings(listOf(holding(1)))
        removeDeviceEncryptedPreferences()
        val restored = LocalImportStore(application)
        assertEquals(listOf(holding(1)), restored.loadHoldings())
        DvmBackupKind.entries.forEach { kind ->
            assertEquals("[${kind.ordinal}]", restored.readBackup(kind)!!.toString(Charsets.UTF_8))
        }
    }

    @Test fun keystoreRetainsEveryChunkAndStillRejectsTampering() {
        val store = LocalImportStore(application)
        val draft = JSONObject().put("message", "x".repeat(3 * 64 * 1024 + 1))
        store.saveFeedbackDraft(draft)
        val preferences = application.getSharedPreferences("local_import_store", Context.MODE_PRIVATE)
        val sealed = android.util.Base64.decode(preferences.getString("feedback_draft", null), android.util.Base64.NO_WRAP)
        assertEquals("IV plus complete ciphertext plus authentication tag", draft.toString().toByteArray(Charsets.UTF_8).size + 28, sealed.size)
        assertTrue("Every original byte must survive", draft.toString() == store.loadFeedbackDraft()?.toString())
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()
        assertTrue(preferences.edit().putString("feedback_draft", android.util.Base64.encodeToString(sealed, android.util.Base64.NO_WRAP)).commit())
        assertThrows(javax.crypto.AEADBadTagException::class.java) { store.loadFeedbackDraft() }
    }

    @Test fun interruptedLegacyAtomicWriteRecoversThePreviousSnapshot() {
        LocalImportStore(application).saveHoldings(listOf(holding()))
        val original = snapshot.readBytes()
        File(snapshot.path + ".bak").writeBytes(original)
        snapshot.writeText("{\"version\":1,", Charsets.UTF_8)
        removeDeviceEncryptedPreferences()
        assertEquals(listOf(holding()), LocalImportStore(application).loadHoldings())
        assertTrue(original.contentEquals(snapshot.readBytes()))
    }

    @Test fun invalidTransferredWatchlistIsRejectedBeforeSavingEncryptedData() {
        LocalImportStore(application).saveWatchlist(listOf(watchlist()))
        removeDeviceEncryptedPreferences()
        val invalid = JSONObject(snapshot.readText(Charsets.UTF_8))
        invalid.getJSONArray("watchlist").getJSONObject(0).put("targetYield", -1.0)
        snapshot.writeText(invalid.toString(), Charsets.UTF_8)
        val original = snapshot.readBytes()
        assertThrows(IllegalArgumentException::class.java) { LocalImportStore(application).loadWatchlist() }
        assertFalse(application.getSharedPreferences("local_import_store", Context.MODE_PRIVATE).contains("watchlist"))
        assertTrue(original.contentEquals(snapshot.readBytes()))
    }
}
