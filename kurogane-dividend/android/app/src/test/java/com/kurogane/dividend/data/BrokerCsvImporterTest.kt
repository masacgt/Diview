package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test
import java.nio.charset.Charset

class BrokerCsvImporterTest {
    @Test fun parsesQuotedCommasAndEscapedQuotes() {
        val csv = "銘柄コード,銘柄名,保有数量,平均取得価額,現在値,口座区分\n7203,\"トヨタ,自動車\",100,2450,2600,特定\n8316,\"三井住友\"\"FG\",20,3000,3150,NISA"
        val parsed = BrokerCsvImporter.parse(csv.toByteArray(Charsets.UTF_8), CsvBroker.SBI)
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals("トヨタ,自動車", parsed.rows[0].name)
        assertEquals("三井住友\"FG", parsed.rows[1].name)
        assertEquals(100.0, parsed.rows[0].shares!!, 0.0)
        assertEquals("NISA", parsed.rows[1].account)
    }

    @Test fun detectsBrokerLegacyJapaneseEncoding() {
        val csv = "銘柄コード,銘柄名,保有数量,平均取得価額\r\n7203,トヨタ自動車,100,2450".toByteArray(Charset.forName("MS932"))
        val parsed = BrokerCsvImporter.parse(csv, CsvBroker.RAKUTEN)
        assertEquals(CsvEncoding.SHIFT_JIS, parsed.encoding)
        assertEquals("トヨタ自動車", parsed.rows.single().name)
    }

    @Test fun monexPrefersItsLegacyShiftJisFormatUnlessUtf8BomIsPresent() {
        val legacy = "銘柄コード,銘柄名,保有数量,取得単価\n7203,トヨタ自動車,100,2450".toByteArray(Charset.forName("Shift_JIS"))
        assertEquals(CsvEncoding.SHIFT_JIS, BrokerCsvImporter.parse(legacy, CsvBroker.MONEX).encoding)
        val utf8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "銘柄コード,銘柄名,保有数量,取得単価\n7203,トヨタ自動車,100,2450".toByteArray()
        assertEquals(CsvEncoding.UTF8, BrokerCsvImporter.parse(utf8, CsvBroker.MONEX).encoding)
    }

    @Test fun matchesNormalizedFundNameAndSeparatesUnknownFunds() {
        val csv = "商品コード,商品名,保有口数,個別元本\n, eMAXIS Slim 全世界株式（オール・カントリー）,50000,24500\n,架空の未登録ファンド,100,1000"
        val parsed = BrokerCsvImporter.parse(csv.toByteArray(), CsvBroker.MONEX, fundNames = listOf("eMAXIS Slim 全世界株式 オールカントリー"))
        assertEquals(1, parsed.rows.size)
        assertTrue(parsed.rows.single().isFund)
        assertEquals(1, parsed.unmatchedFunds.size)
        assertEquals("架空の未登録ファンド", parsed.unmatchedFunds.single().sourceName)
    }

    @Test fun supportsUtf8BomAndMultilineQuotedCells() {
        val body = "銘柄コード,銘柄名,保有数量,平均取得価額\r\n7203,\"トヨタ\n自動車\",1,2".toByteArray(Charsets.UTF_8)
        val parsed = BrokerCsvImporter.parse(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + body, CsvBroker.MATSUI)
        assertEquals(CsvEncoding.UTF8, parsed.encoding)
        assertEquals("トヨタ\n自動車", parsed.rows.single().name)
    }

    @Test fun recognizesBrokerSpecificShareAndCostHeaderVariants() {
        val rakuten = "銘柄コード,銘柄名,保有数量［株］,平均取得額［円］,現在値［円］\n7203,トヨタ自動車,100,2450,2600"
        val parsed = BrokerCsvImporter.parse(rakuten.toByteArray(Charsets.UTF_8), CsvBroker.RAKUTEN)
        assertTrue(parsed.errors.toString(), parsed.errors.isEmpty())
        assertEquals(100.0, parsed.rows.single().shares!!, 0.0)
        assertEquals(2450.0, parsed.rows.single().averageCost!!, 0.0)

        val matsui = "銘柄コード,銘柄名称,保有数[株],取得平均[円]\n7203,トヨタ自動車,100,2450"
        val second = BrokerCsvImporter.parse(matsui.toByteArray(Charsets.UTF_8), CsvBroker.MATSUI)
        assertEquals(100.0, second.rows.single().shares!!, 0.0)
        assertEquals(2450.0, second.rows.single().averageCost!!, 0.0)
    }
}
