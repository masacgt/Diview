package com.kurogane.dividend.data.csv

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

/** Broker CSV parsing kept separate from UI and portfolio persistence. */
enum class CsvBroker { RAKUTEN, SBI, MONEX, MATSUI }

enum class CsvMarket { JAPAN, US, FUND }

data class FundCatalogEntry(val code: String, val name: String)

data class ImportedHolding(
    val code: String,
    val name: String,
    val shares: Long,
    val averageCost: Double,
    val currentPrice: Double,
    val accountType: String,
    val market: CsvMarket,
    val currency: String = "JPY",
    val needsCatalogMatch: Boolean = false,
    val catalogMatchScore: Double? = null
)

data class BrokerCsvImportResult(
    val broker: CsvBroker,
    val holdings: List<ImportedHolding>,
    val unmatchedFunds: List<ImportedHolding>,
    val skippedRows: Int,
    val warnings: List<String>
)

class UnsupportedBrokerCsvException(message: String) : IllegalArgumentException(message)

/**
 * Parses the four broker formats supported by the existing Android importer.
 * The source file remains local: callers may pass a ContentResolver stream and
 * persist the returned preview only after the user confirms it.
 */
class BrokerCsvImporter(
    private val fundCatalog: Collection<FundCatalogEntry> = emptyList()
) {
    fun parse(
        input: InputStream,
        broker: CsvBroker? = null,
        fileName: String? = null
    ): BrokerCsvImportResult = parse(input.readBytes(), broker, fileName)

    fun parse(
        bytes: ByteArray,
        broker: CsvBroker? = null,
        fileName: String? = null
    ): BrokerCsvImportResult {
        require(bytes.isNotEmpty()) { "CSVファイルが空です。" }
        var rows = parseCsv(decode(bytes, broker))
        val selectedBroker = broker ?: detectBroker(rows, fileName)
        if (broker == null) rows = parseCsv(decode(bytes, selectedBroker))
        val parser = when (selectedBroker) {
            CsvBroker.RAKUTEN -> ParserKind.RAKUTEN
            CsvBroker.SBI -> ParserKind.SBI
            CsvBroker.MONEX -> ParserKind.MONEX
            CsvBroker.MATSUI -> ParserKind.MATSUI
        }
        return parseRows(rows, selectedBroker, parser)
    }

    private fun parseRows(
        rows: List<List<String>>,
        broker: CsvBroker,
        parser: ParserKind
    ): BrokerCsvImportResult {
        val imported = mutableListOf<ImportedHolding>()
        val unmatchedFunds = mutableListOf<ImportedHolding>()
        val warnings = mutableListOf<String>()
        var skipped = 0
        var accountFromSection = ""
        var header: Header? = null

        for (row in rows) {
            val cells = row.map(::clean)
            val joined = cells.joinToString(",")
            if (cells.all(String::isBlank)) continue

            val detectedSection = detectAccountSection(joined)
            if (detectedSection != null) {
                accountFromSection = detectedSection
                header = null
                continue
            }
            if (joined.contains("口座合計")) {
                header = null
                continue
            }

            val nextHeader = identifyHeader(cells, parser)
            if (nextHeader != null) {
                header = nextHeader
                continue
            }
            val activeHeader = header ?: continue
            val parsed = try {
                parseHolding(cells, activeHeader, parser, accountFromSection)
            } catch (_: RuntimeException) {
                null
            }
            if (parsed == null) {
                // Footer and summary rows are ignored; count only rows that look like security rows.
                if (looksLikeData(cells, activeHeader)) skipped++
                continue
            }
            if (parsed.market == CsvMarket.FUND && parsed.needsCatalogMatch) {
                unmatchedFunds += parsed
            } else {
                imported += parsed
            }
        }

        if (imported.isEmpty() && unmatchedFunds.isEmpty()) {
            warnings += "対応する見出しまたは有効な保有銘柄行が見つかりませんでした。証券会社から出力した保有残高CSVを選択してください。"
        }
        if (skipped > 0) warnings += "形式を確認できず読み飛ばした保有行: ${skipped}件"
        return BrokerCsvImportResult(broker, imported, unmatchedFunds, skipped, warnings)
    }

    private fun identifyHeader(cells: List<String>, parser: ParserKind): Header? {
        val normalized = cells.map(::normalizeHeader)
        fun has(vararg terms: String) = terms.all { term -> normalized.any { it.contains(normalizeHeader(term)) } }

        val isMonexUs = has("ティッカー", "保有数[株]") || has("ティッカー", "取得平均[ドル]")
        val isMonexFund = has("基準価額", "分配金の取扱い")
        val isRakutenDetailed = has("銘柄コード・ティッカー", "保有数量")
        val isRakutenAccountTable = has("銘柄コード", "保有数量")
        val isMatsuiUs = has("ティッカー", "銘柄") && has("保有数")
        val isMatsuiJapan = has("銘柄コード", "銘柄名") && has("保有数")
        val isMonexJapan = has("銘柄コード", "平均取得単価") && has("保有数")
        val isSbiFund = has("ファンド名", "保有口数")
        val isSbiStock = has("銘柄コード", "保有株数") || has("コード", "保有株数")

        val kind = when (parser) {
            ParserKind.RAKUTEN -> when {
                isRakutenDetailed -> RowKind.RAKUTEN_DETAILED
                isRakutenAccountTable -> RowKind.RAKUTEN_ACCOUNT
                isSbiFund -> RowKind.SBI_FUND
                else -> null
            }
            ParserKind.SBI -> when {
                isSbiFund -> RowKind.SBI_FUND
                isSbiStock -> RowKind.SBI_STOCK
                else -> null
            }
            ParserKind.MONEX -> when {
                isMonexUs -> RowKind.MONEX_US
                isMonexFund -> RowKind.MONEX_FUND
                isMonexJapan -> RowKind.MONEX_JAPAN
                else -> null
            }
            ParserKind.MATSUI -> when {
                isMatsuiUs -> RowKind.MATSUI_US
                isMatsuiJapan -> RowKind.MATSUI_JAPAN
                else -> null
            }
        } ?: return null

        fun index(vararg aliases: String): Int {
            val keys = aliases.map(::normalizeHeader)
            keys.forEach { key -> normalized.indexOf(key).takeIf { it >= 0 }?.let { return it } }
            keys.forEach { key -> normalized.indexOfFirst { it.contains(key) }.takeIf { it >= 0 }?.let { return it } }
            return -1
        }
        val fallback = when (kind) {
            RowKind.RAKUTEN_ACCOUNT -> intArrayOf(0, 1, 2, 6, 8, -1)
            RowKind.RAKUTEN_DETAILED -> intArrayOf(1, 2, 4, 6, 8, 3)
            RowKind.SBI_STOCK -> intArrayOf(0, 1, 2, 4, 5, -1)
            RowKind.SBI_FUND -> intArrayOf(-1, 0, 1, 3, 4, -1)
            RowKind.MONEX_JAPAN -> intArrayOf(3, 2, 9, 8, 7, 5)
            RowKind.MONEX_US -> intArrayOf(0, 1, 4, 7, -1, 3)
            RowKind.MONEX_FUND -> intArrayOf(-1, 2, 7, 11, -1, 3)
            RowKind.MATSUI_JAPAN -> intArrayOf(0, 1, 5, 7, 8, 3)
            RowKind.MATSUI_US -> intArrayOf(0, 1, 4, 6, 9, 3)
        }
        fun resolve(position: Int, vararg aliases: String): Int = index(*aliases).takeIf { it >= 0 } ?: fallback[position]

        return Header(
            kind = kind,
            code = resolve(0, "銘柄コード・ティッカー", "銘柄コード", "コード", "ティッカー"),
            name = resolve(1, "銘柄名", "銘柄", "銘柄名称", "ファンド名"),
            shares = resolve(2, "保有数量", "保有株数", "保有数[株]", "保有数", "保有口数"),
            averageCost = resolve(3, "平均取得価額", "平均取得単価", "取得平均[ドル]", "取得単価", "取得平均", "基準価額"),
            currentPrice = resolve(4, "現在値", "評価単価", "時価", "評価額", "時価評価額"),
            account = resolve(5, "口座", "口座区分", "預り区分"),
            headers = normalized
        )
    }

    private fun parseHolding(cells: List<String>, header: Header, parser: ParserKind, sectionAccount: String): ImportedHolding? {
        fun cell(index: Int): String = cells.getOrNull(index)?.let(::clean).orEmpty()
        val kind = header.kind
        val fund = kind == RowKind.SBI_FUND || kind == RowKind.MONEX_FUND ||
            (kind == RowKind.RAKUTEN_DETAILED && cell(0).contains("投資信託"))
        val code = cell(header.code)
        val name = cell(header.name)
        val shares = number(cell(header.shares))?.toLong() ?: return null
        if (shares <= 0 || name.isBlank()) return null
        val accountRaw = cell(header.account).ifBlank { sectionAccount }
        val account = normalizeAccount(accountRaw)
        val market = when {
            fund -> CsvMarket.FUND
            kind == RowKind.MONEX_US || kind == RowKind.MATSUI_US -> CsvMarket.US
            kind == RowKind.RAKUTEN_DETAILED && (code.matches(Regex("[A-Za-z]{1,5}(\\.[A-Za-z])?")) || cell(5).equals("USD", true)) -> CsvMarket.US
            else -> CsvMarket.JAPAN
        }
        val avg = number(cell(header.averageCost)) ?: 0.0
        val price = number(cell(header.currentPrice)) ?: 0.0
        if (!fund && code.isBlank()) return null

        if (fund) {
            val match = matchFund(name)
            val catalog = match?.first
            return ImportedHolding(
                code = catalog?.code.orEmpty(),
                name = catalog?.name ?: name,
                shares = shares,
                averageCost = avg,
                currentPrice = price,
                accountType = account,
                market = CsvMarket.FUND,
                needsCatalogMatch = catalog == null,
                catalogMatchScore = match?.second
            )
        }
        return ImportedHolding(
            code = code,
            name = name,
            shares = shares,
            averageCost = avg,
            currentPrice = price,
            accountType = account,
            market = market,
            currency = if (market == CsvMarket.US || cell(5).equals("USD", true)) "USD" else "JPY"
        )
    }

    private fun looksLikeData(cells: List<String>, header: Header): Boolean =
        cells.getOrNull(header.code).orEmpty().matches(Regex("[0-9]{3,5}[A-Za-z]?|[A-Za-z]{1,5}(\\.[A-Za-z])?"))

    private fun detectBroker(rows: List<List<String>>, fileName: String?): CsvBroker {
        val sample = (rows.take(40).flatten().joinToString(" ") + " " + fileName.orEmpty()).lowercase(Locale.ROOT)
        return when {
            sample.contains("stockposition_") || sample.contains("us_spotbalance_") || sample.contains("fundsellamountposition_") ||
                sample.contains("取得平均[ドル]") || sample.contains("保有数[株]") -> CsvBroker.MONEX
            sample.contains("保有商品詳細") || sample.contains("銘柄コード・ティッカー") ||
                (sample.contains("■nisa口座") && sample.contains("保有数量")) -> CsvBroker.RAKUTEN
            sample.contains("spotbalance_") || sample.contains("ティッカー") && sample.contains("銘柄名") -> CsvBroker.MATSUI
            sample.contains("ファンド名") || sample.contains("保有株数") || sample.contains("売却注文中") -> CsvBroker.SBI
            else -> throw UnsupportedBrokerCsvException("証券会社とCSV形式を判定できません。証券会社を指定するか、対応する保有残高CSVを選択してください。")
        }
    }

    private fun decode(bytes: ByteArray, broker: CsvBroker?): String {
        val offset = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        val content = bytes.copyOfRange(offset, bytes.size)
        val shiftJis = Charset.forName("Shift_JIS")
        when (broker) {
            CsvBroker.SBI, CsvBroker.MONEX -> return String(content, shiftJis)
            CsvBroker.MATSUI -> {
                if (offset > 0) return String(content, StandardCharsets.UTF_8)
                val utf8 = String(content, StandardCharsets.UTF_8)
                if (listOf("銘柄", "口座", "保有").any(utf8::contains)) return utf8
                val sjis = String(content, shiftJis)
                return if (listOf("銘柄", "口座", "保有").any(sjis::contains)) sjis else utf8
            }
            CsvBroker.RAKUTEN -> {
                if (offset > 0) return String(content, StandardCharsets.UTF_8)
                val sampleSize = minOf(1024, content.size)
                val utf8Sample = String(content, 0, sampleSize, StandardCharsets.UTF_8)
                if (listOf("■", "保有", "種別", "資産合計", "銘柄").any(utf8Sample::contains)) {
                    return String(content, StandardCharsets.UTF_8)
                }
                val sjisSample = String(content, 0, sampleSize, shiftJis)
                return if (listOf("資産", "銘柄", "保有").any(sjisSample::contains)) {
                    String(content, shiftJis)
                } else String(content, shiftJis)
            }
            null -> Unit
        }
        return tryDecode(content, StandardCharsets.UTF_8)
            ?: tryDecode(content, shiftJis)
            ?: String(content, shiftJis)
    }

    private fun tryDecode(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' && quoted && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { row += field.toString(); field.setLength(0) }
                (ch == '\n' || ch == '\r') && !quoted -> {
                    row += field.toString(); field.setLength(0)
                    if (row.any(String::isNotBlank)) rows += row.toList()
                    row.clear()
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                }
                else -> field.append(ch)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString()
            if (row.any(String::isNotBlank)) rows += row.toList()
        }
        if (quoted) throw UnsupportedBrokerCsvException("CSVの引用符が閉じられていません。ファイル形式を確認してください。")
        return rows
    }

    private fun detectAccountSection(row: String): String? = when {
        row.contains("株式（特定預り）") || row.contains("特定預り") -> "特定口座"
        row.contains("株式（一般預り）") || row.contains("一般預り") -> "一般口座"
        row.contains("株式（旧NISA預り）") || row.contains("旧NISA") -> "旧NISA"
        row.contains("株式（NISA預り") || row.contains("成長投資枠") -> "新NISA（成長投資枠）"
        row.contains("つみたて投資枠") -> "新NISA（つみたて投資枠）"
        row.contains("■NISA口座") && row.contains("成長") -> "新NISA（成長投資枠）"
        row.contains("■NISA口座") && row.contains("つみたて") -> "新NISA（つみたて投資枠）"
        row.contains("■NISA口座") || row.contains("■旧NISA") -> "旧NISA"
        row.contains("■特定口座") -> "特定口座"
        row.contains("■一般口座") -> "一般口座"
        else -> null
    }

    private fun normalizeAccount(value: String): String = when {
        value.contains("成長投資枠") -> "新NISA（成長投資枠）"
        value.contains("つみたて") -> "新NISA（つみたて投資枠）"
        value.contains("旧NISA") || value.contains("旧つみたて") -> "旧NISA"
        value.contains("NISA") -> "NISA"
        value.contains("一般") -> "一般口座"
        value.contains("特定") -> "特定口座"
        else -> value
    }

    private fun normalizeHeader(value: String): String = clean(value)
        .replace("［", "[").replace("］", "]")
        .replace("　", "").replace(" ", "").lowercase(Locale.ROOT)

    private fun matchFund(name: String): Pair<FundCatalogEntry, Double>? {
        if (fundCatalog.isEmpty()) return null
        val normalized = normalizeFundName(name)
        val candidates = fundCatalog.map { it to normalizeFundName(it.name) }
        candidates.firstOrNull { it.second == normalized }?.let { return it.first to 1.0 }

        val nameContainsCatalog = candidates.filter { normalized.contains(it.second) }
        nameContainsCatalog.maxByOrNull { similarity(normalized, it.second) }?.let {
            val score = similarity(normalized, it.second)
            if (score >= 0.6) return it.first to score
        }
        val catalogContainsName = candidates.filter { it.second.contains(normalized) }
        catalogContainsName.maxByOrNull { similarity(normalized, it.second) }?.let {
            val score = if (normalized.length >= 15) 0.9 else similarity(normalized, it.second)
            if (score >= 0.8) return it.first to score
        }
        return candidates.asSequence()
            .map { it.first to similarity(normalized, it.second) }
            .filter { it.second >= 0.75 }
            .maxByOrNull { it.second }
    }

    private fun normalizeFundName(value: String): String {
        var normalized = Normalizer.normalize(clean(value), Normalizer.Form.NFKC)
        normalized = normalized
            .replace(Regex("[（(]\\s*Hなし\\s*[）)]", RegexOption.IGNORE_CASE), "（為替ヘッジなし）")
            .replace(Regex("[（(]\\s*Hあり\\s*[）)]", RegexOption.IGNORE_CASE), "（為替ヘッジあり）")
            .replace("為替H無", "為替ヘッジなし").replace("為替H有", "為替ヘッジあり")
            .replace(Regex("毎月$"), "毎月決算型")
            .replace(Regex("隔月$"), "隔月決算型")
            .replace(Regex("予想分配金?$"), "予想分配金提示型")
        return normalized.replace(Regex("[\\s　]+"), "")
            .replace("・", "").replace("･", "").replace("-", "").replace("ー", "")
            .lowercase(Locale.ROOT)
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isEmpty() || right.isEmpty()) return if (left == right) 1.0 else 0.0
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            current.copyInto(previous)
        }
        return 1.0 - previous[right.length].toDouble() / maxOf(left.length, right.length)
    }

    private fun clean(value: String): String = value.trim().removePrefix("\uFEFF").trim().removeSurrounding("\"")

    private fun number(value: String): Double? = value
        .replace(",", "").replace("円", "").replace("株", "").replace("口", "")
        .replace("\u00A0", "").trim().takeIf(String::isNotEmpty)?.toDoubleOrNull()

    private data class Header(
        val kind: RowKind,
        val code: Int,
        val name: Int,
        val shares: Int,
        val averageCost: Int,
        val currentPrice: Int,
        val account: Int,
        val headers: List<String>
    )

    private enum class ParserKind { RAKUTEN, SBI, MONEX, MATSUI }
    private enum class RowKind {
        RAKUTEN_ACCOUNT, RAKUTEN_DETAILED, SBI_STOCK, SBI_FUND,
        MONEX_JAPAN, MONEX_US, MONEX_FUND, MATSUI_JAPAN, MATSUI_US
    }
}
