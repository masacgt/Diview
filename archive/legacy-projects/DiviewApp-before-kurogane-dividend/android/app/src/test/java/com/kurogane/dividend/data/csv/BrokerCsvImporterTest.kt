package com.kurogane.dividend.data.csv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class BrokerCsvImporterTest {
    private val importer = BrokerCsvImporter()

    @Test
    fun parsesSbiJapaneseStockCsv() {
        val csv = """銘柄コード,銘柄名称,保有株数,売却注文中,取得単価,現在値,取得金額,評価額,評価損益
7203,"トヨタ,自動車",100,0,2500,2800,250000,280000,30000
"""
        val result = importer.parse(csv.toByteArray(Charset.forName("Shift_JIS")), CsvBroker.SBI)

        assertEquals(1, result.holdings.size)
        assertEquals("7203", result.holdings.single().code)
        assertEquals("トヨタ,自動車", result.holdings.single().name)
        assertEquals(100L, result.holdings.single().shares)
        assertEquals(2500.0, result.holdings.single().averageCost, 0.0)
        assertEquals(2800.0, result.holdings.single().currentPrice, 0.0)
    }

    @Test
    fun parsesRakutenSectionedCsvAndAccountType() {
        val csv = """■NISA口座（成長投資枠）
銘柄コード,銘柄名,保有数量,注文中,取得額,取得額,平均取得価額,評価損益,現在値
7203,トヨタ自動車,50,0,0,0,2400,5000,2500
口座合計,,,,,,,,
"""
        val result = importer.parse(csv.toByteArray(Charsets.UTF_8), CsvBroker.RAKUTEN)

        assertEquals(1, result.holdings.size)
        assertEquals("新NISA（成長投資枠）", result.holdings.single().accountType)
        assertEquals(50L, result.holdings.single().shares)
        assertEquals(2400.0, result.holdings.single().averageCost, 0.0)
        assertEquals(2500.0, result.holdings.single().currentPrice, 0.0)
    }

    @Test
    fun detectsAndParsesMonexUsCsv() {
        val csv = """ティッカー,銘柄,市場,口座区分,保有数[株],発注数[株],取得平均[ドル],取得平均[円]
AAPL,Apple Inc.,NASDAQ,特定,12,0,180.5,27800
"""
        val result = importer.parse(csv.toByteArray(Charset.forName("Shift_JIS")))

        assertEquals(CsvBroker.MONEX, result.broker)
        assertEquals(1, result.holdings.size)
        assertEquals("AAPL", result.holdings.single().code)
        assertEquals(CsvMarket.US, result.holdings.single().market)
        assertEquals("USD", result.holdings.single().currency)
        assertEquals(180.5, result.holdings.single().averageCost, 0.0)
    }

    @Test
    fun parsesMatsuiJapaneseCsvInShiftJis() {
        val csv = """銘柄コード,銘柄名,市場,口座区分,預り区分,保有数,発注数,取得平均,評価単価
8316,三井住友フィナンシャルグループ,東証,特定,保護,25,0,7200,8500
"""
        val bytes = csv.toByteArray(Charset.forName("windows-31j"))
        val result = importer.parse(bytes, CsvBroker.MATSUI)

        assertEquals(1, result.holdings.size)
        assertEquals("8316", result.holdings.single().code)
        assertEquals("特定口座", result.holdings.single().accountType)
        assertEquals(25L, result.holdings.single().shares)
        assertEquals(7200.0, result.holdings.single().averageCost, 0.0)
        assertEquals(8500.0, result.holdings.single().currentPrice, 0.0)
    }

    @Test
    fun leavesUnknownFundsSeparateUntilCatalogMatch() {
        val csv = """ファンド名,保有口数,売却注文中,取得単価,基準価額,取得金額,評価額,評価損益,分配金受取方法
テスト投資信託,10000,0,10000,11000,10000,11000,1000,受取
"""
        val result = importer.parse(csv.toByteArray(Charset.forName("Shift_JIS")), CsvBroker.SBI)

        assertTrue(result.holdings.isEmpty())
        assertEquals("テスト投資信託", result.unmatchedFunds.single().name)
        assertTrue(result.unmatchedFunds.single().needsCatalogMatch)

        val matched = BrokerCsvImporter(listOf(FundCatalogEntry("FUND-1", "テスト投資信託")))
            .parse(csv.toByteArray(Charset.forName("Shift_JIS")), CsvBroker.SBI)
        assertEquals("FUND-1", matched.holdings.single().code)
        assertFalse(matched.holdings.single().needsCatalogMatch)
    }

    @Test
    fun fundMatcherAcceptsStrongPartialNameMatchesLikeApk() {
        val csv = """ファンド名,保有口数,売却注文中,取得単価,基準価額,取得金額,評価額,評価損益,分配金受取方法
テスト投資信託インデックスＡ,10000,0,10000,11000,10000,11000,1000,受取
"""
        val matcher = BrokerCsvImporter(listOf(FundCatalogEntry("FUND-2", "テスト投資信託インデックス")))
        val result = matcher.parse(csv.toByteArray(Charset.forName("Shift_JIS")), CsvBroker.SBI)

        assertEquals("FUND-2", result.holdings.single().code)
        assertTrue((result.holdings.single().catalogMatchScore ?: 0.0) >= 0.75)
    }

    @Test
    fun parsesBrokerSamplesBundledInSourceApk() {
        assertApkSample("SBI_csv_data.csv", CsvBroker.SBI)
        assertApkSample("assetbalance(JP)_20260816_060652-1.csv", CsvBroker.RAKUTEN)
        assertApkSample("assetbalance(all)_20260819_012551.csv", CsvBroker.RAKUTEN)
        assertApkSample("stockposition_20260226181614.csv", CsvBroker.MONEX)
        assertApkSample("us_spotbalance_20260128.csv", CsvBroker.MONEX)
        assertApkSample("spotbalance_20260127.csv", CsvBroker.MATSUI)
    }

    private fun assertApkSample(fileName: String, broker: CsvBroker) {
        val path = "apk-fixtures/assets/sampleCsv/$fileName"
        val bytes = checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "Missing APK fixture: $fileName" }
            .use { it.readBytes() }
        val result = importer.parse(bytes, broker, fileName)
        assertTrue("$fileName should produce preview records", result.holdings.isNotEmpty() || result.unmatchedFunds.isNotEmpty())
    }
}
