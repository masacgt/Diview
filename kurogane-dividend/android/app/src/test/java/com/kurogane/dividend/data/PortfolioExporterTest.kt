package com.kurogane.dividend.data

import com.kurogane.dividend.model.PortfolioMathTest.Companion.holding
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortfolioExporterTest {
    @Test fun csvRoundTripKeepsMixedAccountQuantitiesAndCosts() {
        val stock = holding().copy(shares = 300, taxFreeShares = 100, accountType = "複数口座")
        val parsed = BrokerCsvImporter.parse(PortfolioExporter.csv(listOf(stock)).toByteArray(), CsvBroker.SBI)
        val restored = PortfolioImporter.importRows(listOf(stock.copy(shares = 0)), parsed.rows,
            setOf("shares", "averageCost", "currentPrice", "account")).single()
        assertEquals(stock.shares, restored.shares)
        assertEquals(stock.acqPrice, restored.acqPrice, 0.0)
        assertEquals(stock.effectiveTaxFreeShares, restored.effectiveTaxFreeShares)
    }

    @Test fun csvEscapesQuotesCommasAndFormulaPrefixes() {
        val csv = PortfolioExporter.csv(listOf(holding().copy(name = "=危険,\"文字列\"")))
        val row = CsvTokenizer.parse(csv.removePrefix("\uFEFF"))[1]
        assertEquals("'=危険,\"文字列\"", row[1])
    }

    @Test fun jsonContainsTheTaxSplitAndDividendSchedule() {
        val root = JSONObject(PortfolioExporter.json(listOf(holding().copy(taxFreeShares = 50))))
        val stock = root.getJSONArray("holdings").getJSONObject(0)
        assertEquals(50, stock.getInt("taxFreeShares"))
        assertEquals(2, stock.getJSONArray("dividendMonths").length())
        assertEquals("diview-portfolio-v1", root.getString("format"))
    }

    @Test fun emptyPortfolioStillProducesValidExportFiles() {
        assertEquals(0, JSONObject(PortfolioExporter.json(emptyList())).getJSONArray("holdings").length())
        assertEquals(1, CsvTokenizer.parse(PortfolioExporter.csv(emptyList())).size)
    }

    @Test fun fundsCanBeMatchedAgainAfterCsvExport() {
        val stock = holding().copy(code = "FUND:ABC", name = "ABCインデックス", category = com.kurogane.dividend.model.Category.FUND)
        val parsed = BrokerCsvImporter.parse(PortfolioExporter.csv(listOf(stock)).toByteArray(), CsvBroker.SBI,
            fundNames = listOf(stock.name))
        assertTrue(parsed.rows.single().isFund)
        assertEquals(stock.name, parsed.rows.single().name)
    }
}
