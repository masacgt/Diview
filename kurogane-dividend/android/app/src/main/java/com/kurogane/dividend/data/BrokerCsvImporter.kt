package com.kurogane.dividend.data

import com.kurogane.dividend.model.PortfolioMath
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

enum class CsvBroker(val label: String, val legacyCharset: String) {
    SBI("SBI証券", "MS932"),
    RAKUTEN("楽天証券", "MS932"),
    MONEX("マネックス証券", "MS932"),
    MATSUI("松井証券", "MS932"),
    MITSUBISHI_UFJ("三菱UFJ eスマート証券", "MS932"),
    NOMURA("野村證券", "MS932"),
    DIVIEW("Diview CSVバックアップ", "UTF-8")
}

enum class CsvEncoding(val charset: Charset) { UTF8(StandardCharsets.UTF_8), SHIFT_JIS(Charset.forName("MS932")), EUC_JP(Charset.forName("EUC-JP")) }

data class CsvHoldingRow(
    val code: String, val name: String, val shares: Double?, val averageCost: Double?, val currentPrice: Double?,
    val account: String?, val isFund: Boolean, val selected: Boolean = true, val matchedName: String? = null,
    val currency: String? = null
)
data class UnmatchedFund(val sourceName: String, val shares: Double?, val averageCost: Double?)
data class CsvImportResult(val broker: CsvBroker, val encoding: CsvEncoding, val rows: List<CsvHoldingRow>, val unmatchedFunds: List<UnmatchedFund>, val errors: List<String>)

object BrokerCsvImporter {
    private val aliases = mapOf(
        "code" to listOf("銘柄コード", "証券コード", "コード", "銘柄ｺｰﾄﾞ", "商品コード", "ファンドコード", "銘柄番号", "ティッカー", "ISIN"),
        "name" to listOf("銘柄名", "商品名", "ファンド名", "銘柄名称", "ファンド名称", "投資信託名", "銘柄/ファンド名", "銘柄"),
        "shares" to listOf("保有数量", "保有株数", "保有数", "数量", "残高数量", "口数", "保有口数", "預り数量", "残高", "保有額面"),
        "cost" to listOf("平均取得価額", "取得単価", "平均取得単価", "平均取得額", "取得平均", "個別元本", "取得価額", "参考単価", "参考取得単価", "取得単価(円)", "取得コスト(円)", "取得コスト", "買付単価", "平均買付単価"),
        "price" to listOf("現在値", "時価", "基準価額", "現在単価", "終値", "評価単価", "参考時価"),
        "account" to listOf("預り区分", "口座", "口座区分", "預り", "勘定"),
        "kind" to listOf("種別", "商品分類", "商品", "銘柄区分", "資産区分"),
        "market" to listOf("市場", "取引市場"),
        "currency" to listOf("通貨", "通貨単位")
    )

    private data class CsvColumns(
        val codes: List<Int>, val name: Int, val shares: Int, val costs: List<Int>, val prices: List<Int>,
        val accounts: List<Int>, val kinds: List<Int>, val markets: List<Int>, val currencies: List<Int>
    )

    /** Returns a broker only when the file name or its header contains a distinctive marker. */
    fun detectBroker(bytes: ByteArray, fileName: String): CsvBroker? {
        val name = normalizeDetectionText(fileName)
        val text = runCatching { decode(bytes, CsvBroker.SBI).first }.getOrDefault("")
        val body = normalizeDetectionText(text)
        if (isDiviewPortfolioCsv(text)) return CsvBroker.DIVIEW
        return when {
            listOf("stockposition", "usspotbalance", "fundsellamountposition", "マネックス", "monex").any(name::contains) -> CsvBroker.MONEX
            listOf("残高一覧現物株式", "残高一覧米国株式", "残高一覧投資信託", "三菱ufj", "eスマート証券").any(name::contains) -> CsvBroker.MITSUBISHI_UFJ
            listOf("new_file_nomura", "野村證券", "nomura").any(name::contains) || "預り資産預り証券" in body -> CsvBroker.NOMURA
            listOf("sbicsvdata", "sbi証券", "sbisec").any(name::contains) -> CsvBroker.SBI
            listOf("楽天証券", "rakuten", "rakutensec").any(name::contains) || "楽天銀行普通預金残高" in body -> CsvBroker.RAKUTEN
            listOf("松井証券", "matsui").any(name::contains) || "松井証券" in body -> CsvBroker.MATSUI
            "保有証券一覧" in body && "ファンド名" in body && "株式特定預り" in body -> CsvBroker.SBI
            else -> null
        }
    }

    private fun isDiviewPortfolioCsv(text: String): Boolean {
        val header = runCatching { CsvTokenizer.parse(text.removePrefix("\uFEFF")).firstOrNull() }.getOrNull() ?: return false
        val expected = listOf("銘柄コード", "銘柄名", "保有数量", "平均取得価額", "現在値", "口座区分")
        return header.map(::normHeader) == expected.map(::normHeader)
    }

    private fun normalizeDetectionText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(Regex("[\\s　_()\\[\\]「」『』\"'・/／-]"), "")

    fun parse(bytes: ByteArray, broker: CsvBroker, securityNames: Map<String, String> = emptyMap(), fundNames: Collection<String> = emptyList()): CsvImportResult {
        require(bytes.size <= 10 * 1024 * 1024) { "CSVは10MB以下のファイルを選択してください" }
        val (text, encoding) = decode(bytes, broker)
        val records = CsvTokenizer.parse(text.removePrefix("\uFEFF"))
        if (records.isEmpty()) return CsvImportResult(broker, encoding, emptyList(), emptyList(), listOf("CSVにデータがありません"))
        val result = mutableListOf<CsvHoldingRow>()
        val unmatched = mutableListOf<UnmatchedFund>()
        val errors = mutableListOf<String>()
        var columns: CsvColumns? = null
        var sectionAccount: String? = null
        var unsupportedSection = false
        var foundHeader = false
        records.forEach { cells ->
            val candidate = columnsFor(cells)
            if (candidate != null) {
                columns = candidate
                foundHeader = true
                return@forEach
            }
            val rowText = cells.joinToString(" ")
            if (isSectionRow(cells)) {
                sectionAccount = accountFromSection(rowText)
                unsupportedSection = isUnsupportedSection(rowText)
                return@forEach
            }
            val currentColumns = columns ?: return@forEach
            val name = cells.getOrNull(currentColumns.name)?.trim().orEmpty()
            val rawShare = cells.getOrNull(currentColumns.shares)?.trim().orEmpty()
            if (name.isBlank() || name.startsWith("合計") || name.startsWith("総計") || rawShare.isBlank() || isPlaceholder(rawShare)) return@forEach
            val rawCode = firstValue(cells, currentColumns.codes).orEmpty()
            val code = Normalizer.normalize(rawCode, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
            val shares = rawShare.toNumber()
            val rawCost = firstValue(cells, currentColumns.costs)
            val cost = rawCost.toNumberOrNull()
            val rawPrice = firstValue(cells, currentColumns.prices)
            val price = rawPrice.toNumberOrNull()
            val type = firstValue(cells, currentColumns.kinds).orEmpty()
            val market = firstValue(cells, currentColumns.markets).orEmpty()
            val currencyText = firstValue(cells, currentColumns.currencies).orEmpty()
            if (isUnsupportedInstrument(type, name, unsupportedSection)) return@forEach
            val account = firstValue(cells, currentColumns.accounts)?.let(::canonicalAccount) ?: sectionAccount
            val masterName = securityNames[code] ?: securityNames[code.trimStart('0')]
            val isFundType = listOf("投資信託", "投信", "ファンド", "fund").any(type::contains)
            val isShareType = listOf("株式", "etf", "stock").any(type::contains)
            val isFund = masterName == null &&
                (isFundType || (!isShareType && code.isBlank()) || name.contains("投資信託") || name.contains("ファンド"))
            val isUsd = currencyText.contains("usd", ignoreCase = true) || currencyText.contains("$") ||
                market.contains("NASDAQ", ignoreCase = true) || market.contains("NYSE", ignoreCase = true) ||
                market.contains("AMEX", ignoreCase = true) || market.contains("米国") || type.contains("米国株") ||
                (!isFund && code.matches(Regex("[A-Z]{1,8}(\\.[A-Z])?")))
            val currency = if (isUsd && !isFund) "$" else null
            if (PortfolioMath.wholeShares(shares) == null ||
                (!rawCost.isNullOrBlank() && !isPlaceholder(rawCost) && (cost == null || !PortfolioMath.validMoney(cost))) ||
                (!rawPrice.isNullOrBlank() && !isPlaceholder(rawPrice) && (price == null || !PortfolioMath.validMoney(price))) ||
                (code.isNotBlank() && !code.matches(Regex("[A-Z0-9.\\-]{1,16}")))) {
                errors += "$name：数量・単価・銘柄コードが正しくありません"
                return@forEach
            }
            if (isFund) {
                val found = FundNameMatcher.match(name, fundNames)
                if (found == null) unmatched += UnmatchedFund(name, shares, cost)
                else result += CsvHoldingRow("FUND:${FundNameMatcher.normalize(found.second)}", found.second, shares, cost, price, account, true, matchedName = found.second)
            } else {
                result += CsvHoldingRow(code, masterName ?: name, shares, cost, price, account, false, matchedName = masterName, currency = currency)
            }
        }
        if (!foundHeader) return CsvImportResult(broker, encoding, emptyList(), emptyList(), listOf("銘柄名と保有数量の見出しを特定できませんでした"))
        if (result.isEmpty() && unmatched.isEmpty() && errors.isEmpty()) errors += "取込可能な銘柄行がありません"
        return CsvImportResult(broker, encoding, result, unmatched, errors)
    }

    private fun columnsFor(row: List<String>): CsvColumns? {
        val headers = row.map(::normHeader)
        fun indices(field: String) = headers.indices.filter { hasAlias(headers[it], field) }
        val names = indices("name")
        val shares = indices("shares")
        if (names.isEmpty() || shares.isEmpty()) return null
        return CsvColumns(
            codes = indices("code"), name = names.first(), shares = shares.first(), costs = indices("cost"),
            prices = indices("price"), accounts = indices("account"), kinds = indices("kind"),
            markets = indices("market"), currencies = indices("currency")
        )
    }

    private fun firstValue(row: List<String>, indices: List<Int>): String? =
        indices.asSequence().mapNotNull { row.getOrNull(it)?.trim()?.takeIf { value -> value.isNotBlank() && !isPlaceholder(value) } }.firstOrNull()

    private fun isSectionRow(row: List<String>): Boolean {
        val nonBlankCells = row.filter(String::isNotBlank)
        if (nonBlankCells.size > 2) return false
        val text = normalizeDetectionText(nonBlankCells.joinToString(" "))
        return listOf("nisa", "非課税", "特定預り", "特定口座", "一般預り", "一般口座", "株式(", "投資信託", "ファンド名", "債券(")
            .any { text.contains(normalizeDetectionText(it)) } || text.endsWith("合計")
    }

    private fun isUnsupportedSection(value: String): Boolean {
        val text = normalizeDetectionText(value)
        return listOf("債券", "預り金", "預金", "mrf", "外貨預り").any(text::contains)
    }

    private fun isUnsupportedInstrument(type: String, name: String, unsupportedSection: Boolean): Boolean {
        val normalizedType = normalizeDetectionText(type)
        val normalizedName = normalizeDetectionText(name)
            val supportedType = listOf("株", "投資信託", "投信", "ファンド", "stock", "fund", "etf").any(normalizedType::contains)
        if (type.isNotBlank() && !supportedType) return true
        if (supportedType) return false
        if (unsupportedSection) return true
        return listOf("預り金", "預金", "mrf", "国内債券", "外国債券", "社債", "国債")
            .any(normalizedName::contains) && !listOf("投資信託", "ファンド", "投信").any(normalizedName::contains)
    }

    private fun accountFromSection(value: String): String? {
        val text = normalizeDetectionText(value)
        return when {
            "つみたて" in text || "積立" in text -> "新NISA（つみたて投資枠）"
            "成長投資枠" in text -> "新NISA（成長投資枠）"
            "nisa" in text || "非課税" in text -> "NISA"
            "特定" in text -> "特定口座"
            "一般" in text -> "一般口座"
            else -> null
        }
    }

    private fun canonicalAccount(value: String): String {
        val text = normalizeDetectionText(value)
        return accountFromSection(text) ?: value.trim()
    }

    private fun isPlaceholder(value: String): Boolean = value.trim() in setOf("-", "−", "―", "—", "--", "未取得", "未連携", "N/A")

    fun decode(bytes: ByteArray, broker: CsvBroker): Pair<String, CsvEncoding> {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte())
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8) to CsvEncoding.UTF8
        if (bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())))
            return String(bytes, if (bytes[0] == 0xFF.toByte()) StandardCharsets.UTF_16LE else StandardCharsets.UTF_16BE) to CsvEncoding.UTF8
        val preferred = Charset.forName(broker.legacyCharset)
        strictDecode(bytes, StandardCharsets.UTF_8)?.let { return it to CsvEncoding.UTF8 }
        strictDecode(bytes, preferred)?.let { return it to CsvEncoding.SHIFT_JIS }
        strictDecode(bytes, Charset.forName("EUC-JP"))?.let { return it to CsvEncoding.EUC_JP }
        return String(bytes, preferred) to CsvEncoding.SHIFT_JIS
    }

    private fun strictDecode(bytes: ByteArray, charset: Charset): String? = runCatching {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }.getOrNull()
    private fun String?.toNumberOrNull(): Double? = this?.takeUnless(::isPlaceholder)?.toNumber()
    private fun String.toNumber(): Double? = Normalizer.normalize(this, Normalizer.Form.NFKC)
        .replace(",", "").replace("円", "").replace("株式", "").replace("株", "").replace("口", "")
        .replace("−", "-").replace("△", "-").trim().toDoubleOrNull()?.takeIf(Double::isFinite)
    private fun normHeader(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC).replace(" ", "").replace("　", "").lowercase(Locale.ROOT)
    private fun hasAlias(header: String, field: String) = aliases[field]!!.any { a ->
        val normalized = normHeader(a)
        header == normalized || (normalized.length >= 3 && header.contains(normalized))
    }
}

object CsvTokenizer {
    fun parse(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); val row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' && quoted && i + 1 < input.length && input[i + 1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { row += cell.toString(); cell.setLength(0) }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r' && i + 1 < input.length && input[i + 1] == '\n') i++
                    row += cell.toString(); cell.setLength(0)
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }
                else -> cell.append(c)
            }
            i++
        }
        require(!quoted) { "CSVの引用符が閉じられていません" }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); if (row.any { it.isNotBlank() }) rows += row.toList() }
        return rows
    }
}

object FundNameMatcher {
    fun normalize(value: String): String {
        var text = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace(Regex("[（(]\\s*[HＨ]なし\\s*[）)]", RegexOption.IGNORE_CASE), "（為替ヘッジなし）")
            .replace(Regex("[（(]\\s*[HＨ]あり\\s*[）)]", RegexOption.IGNORE_CASE), "（為替ヘッジあり）")
            .replace(Regex("為替[ＨH](無|なし)"), "為替ヘッジなし")
            .replace(Regex("為替[ＨH](有|あり)"), "為替ヘッジあり")
        text = text.replace(Regex("毎月$"), "毎月決算型").replace(Regex("隔月$"), "隔月決算型")
            .replace(Regex("予想分配金?$"), "予想分配金提示型")
        return text.uppercase(Locale.ROOT).replace(Regex("(投資信託|追加型投信|ファンド|投信|\\s|[・･.,，（）()「」『』【】\\[\\]_\\-ー])"), "")
    }

    fun match(source: String, candidates: Collection<String>, threshold: Double = 0.78): Pair<String, String>? {
        if (candidates.isEmpty()) return null
        val needle = normalize(source)
        if (needle.isBlank()) return null
        fun hedge(value: String) = when { value.contains("ヘッジなし") -> 0; value.contains("ヘッジあり") -> 1; else -> 2 }
        return candidates.asSequence().filter { normalize(it).isNotBlank() && hedge(normalize(it)) == hedge(needle) }
            .map { candidate -> candidate to similarity(needle, normalize(candidate)) }
            .maxByOrNull { it.second }?.takeIf { it.second >= threshold }?.let { it.first to it.first }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a.contains(b) || b.contains(a)) return minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length)
        val prev = IntArray(b.length + 1) { it }; val curr = IntArray(b.length + 1)
        a.forEachIndexed { i, ca ->
            curr[0] = i + 1
            b.forEachIndexed { j, cb -> curr[j + 1] = minOf(curr[j] + 1, prev[j + 1] + 1, prev[j] + if (ca == cb) 0 else 1) }
            for (j in prev.indices) prev[j] = curr[j]
        }
        return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
    }
}
