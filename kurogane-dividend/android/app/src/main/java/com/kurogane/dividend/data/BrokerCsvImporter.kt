package com.kurogane.dividend.data

import com.kurogane.dividend.model.PortfolioMath
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale

enum class CsvBroker(val label: String, val legacyCharset: String) {
    SBI("SBI証券", "MS932"), RAKUTEN("楽天証券", "MS932"), MONEX("マネックス証券", "MS932"), MATSUI("松井証券", "MS932")
}

enum class CsvEncoding(val charset: Charset) { UTF8(StandardCharsets.UTF_8), SHIFT_JIS(Charset.forName("MS932")), EUC_JP(Charset.forName("EUC-JP")) }

data class CsvHoldingRow(
    val code: String, val name: String, val shares: Double?, val averageCost: Double?, val currentPrice: Double?,
    val account: String?, val isFund: Boolean, val selected: Boolean = true, val matchedName: String? = null
)
data class UnmatchedFund(val sourceName: String, val shares: Double?, val averageCost: Double?)
data class CsvImportResult(val broker: CsvBroker, val encoding: CsvEncoding, val rows: List<CsvHoldingRow>, val unmatchedFunds: List<UnmatchedFund>, val errors: List<String>)

object BrokerCsvImporter {
    private val aliases = mapOf(
        "code" to listOf("銘柄コード", "証券コード", "コード", "銘柄ｺｰﾄﾞ", "商品コード", "ファンドコード", "銘柄番号", "isin"),
        "name" to listOf("銘柄名", "商品名", "ファンド名", "銘柄名称", "ファンド名称", "投資信託名", "銘柄/ファンド名"),
        "shares" to listOf("保有数量", "保有株数", "保有数", "数量", "残高数量", "口数", "保有口数", "預り数量", "残高"),
        "cost" to listOf("平均取得価額", "取得単価", "平均取得単価", "平均取得額", "取得平均", "個別元本", "取得価額", "取得単価(円)", "平均買付単価"),
        "price" to listOf("現在値", "時価", "基準価額", "現在単価", "終値", "評価単価"),
        "account" to listOf("預り区分", "口座", "口座区分", "預り", "勘定")
    )

    /** Returns a broker only when the file name or its header contains a distinctive marker. */
    fun detectBroker(bytes: ByteArray, fileName: String): CsvBroker? {
        val name = normalizeDetectionText(fileName)
        val fileMatches = listOf(
            CsvBroker.SBI to listOf("sbi", "sbi証券", "sbisec"),
            CsvBroker.RAKUTEN to listOf("楽天証券", "rakuten", "rakutensec"),
            CsvBroker.MONEX to listOf("マネックス", "monex"),
            CsvBroker.MATSUI to listOf("松井証券", "matsui")
        ).filter { (_, markers) -> markers.any(name::contains) }.map { it.first }.distinct()
        if (fileMatches.isNotEmpty()) return fileMatches.singleOrNull()
        val text = runCatching { decode(bytes, CsvBroker.SBI).first }.getOrDefault("")
        val headerText = runCatching {
            CsvTokenizer.parse(text.removePrefix("\uFEFF")).take(8).flatten()
                .joinToString(" ", transform = ::normalizeDetectionText)
        }.getOrDefault("")
        val matches = buildList {
            if (listOf("預り区分", "預り残高").any(headerText::contains)) add(CsvBroker.SBI)
            if ("平均取得額" in headerText) add(CsvBroker.RAKUTEN)
            if ("保有残高一覧" in headerText) add(CsvBroker.MONEX)
            if (listOf("取得平均", "銘柄名称").any(headerText::contains)) add(CsvBroker.MATSUI)
        }.distinct()
        return matches.singleOrNull()
    }

    private fun normalizeDetectionText(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(Regex("[\\s　\"'「」『』]"), "")

    fun parse(bytes: ByteArray, broker: CsvBroker, securityNames: Map<String, String> = emptyMap(), fundNames: Collection<String> = emptyList()): CsvImportResult {
        require(bytes.size <= 10 * 1024 * 1024) { "CSVは10MB以下のファイルを選択してください" }
        val (text, encoding) = decode(bytes, broker)
        val records = CsvTokenizer.parse(text.removePrefix("\uFEFF"))
        if (records.isEmpty()) return CsvImportResult(broker, encoding, emptyList(), emptyList(), listOf("CSVにデータがありません"))
        val headerAt = records.indexOfFirst { row ->
            val cells = row.map(::normHeader)
            cells.any { hasAlias(it, "name") } && cells.any { hasAlias(it, "shares") }
        }
        if (headerAt < 0) return CsvImportResult(broker, encoding, emptyList(), emptyList(), listOf("銘柄名と保有数量の見出しを特定できませんでした"))
        val headers = records[headerAt].map(::normHeader)
        fun index(field: String) = headers.indexOfFirst { hasAlias(it, field) }
        val ci = index("code"); val ni = index("name"); val si = index("shares"); val ai = index("cost"); val pi = index("price"); val aci = index("account")
        val result = mutableListOf<CsvHoldingRow>()
        val unmatched = mutableListOf<UnmatchedFund>()
        val errors = mutableListOf<String>()
        records.drop(headerAt + 1).forEach { cells ->
            val name = cells.getOrNull(ni)?.trim().orEmpty()
            if (name.isBlank() || name.startsWith("合計") || name.startsWith("総計")) return@forEach
            val rawCode = cells.getOrNull(ci)?.trim().orEmpty()
            val code = Normalizer.normalize(rawCode, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
            val shares = cells.getOrNull(si)?.toNumber()
            val cost = cells.getOrNull(ai)?.toNumber()
            val price = cells.getOrNull(pi)?.toNumber()
            if (PortfolioMath.wholeShares(shares) == null ||
                (ai >= 0 && !cells.getOrNull(ai).isNullOrBlank() && (cost == null || !PortfolioMath.validMoney(cost))) ||
                (pi >= 0 && !cells.getOrNull(pi).isNullOrBlank() && (price == null || !PortfolioMath.validMoney(price))) ||
                (code.isNotBlank() && !code.matches(Regex("[A-Z0-9.\\-]{1,16}")))) {
                errors += "$name：数量・単価・銘柄コードが正しくありません"
                return@forEach
            }
            val likelyFund = code.matches(Regex("[A-Z]{2}\\d{6}")) || name.contains("投資信託") || name.contains("ファンド") || name.contains("インデックス")
            val isFund = code.isBlank() || likelyFund
            if (isFund) {
                val found = FundNameMatcher.match(name, fundNames)
                if (found == null) unmatched += UnmatchedFund(name, shares, cost)
                else result += CsvHoldingRow("FUND:${FundNameMatcher.normalize(found.second)}", found.second, shares, cost, price, cells.getOrNull(aci), true, matchedName = found.second)
            } else {
                val masterName = securityNames[code] ?: securityNames[code.trimStart('0')]
                result += CsvHoldingRow(code, masterName ?: name, shares, cost, price, cells.getOrNull(aci), false, matchedName = masterName)
            }
        }
        if (result.isEmpty() && unmatched.isEmpty() && errors.isEmpty()) errors += "取込可能な銘柄行がありません"
        return CsvImportResult(broker, encoding, result, unmatched, errors)
    }

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
    private fun String.toNumber(): Double? = Normalizer.normalize(this, Normalizer.Form.NFKC)
        .replace(",", "").replace("円", "").replace("株式", "").replace("株", "").replace("口", "")
        .replace("−", "-").trim().toDoubleOrNull()?.takeIf(Double::isFinite)
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
