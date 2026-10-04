package com.kurogane.dividend.data

import android.content.Context

object LocalSecurityCatalog {
    fun names(context: Context): Map<String, String> = runCatching {
        val rows = context.assets.open("securities_local.csv").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val records = CsvTokenizer.parse(rows)
        if (records.isEmpty()) return emptyMap()
        val header = records.first()
        val codeIndex = header.indexOf("security_code_display").takeIf { it >= 0 } ?: header.indexOf("security_code")
        val nameIndex = header.indexOf("name")
        records.drop(1).mapNotNull { row ->
            val code = row.getOrNull(codeIndex)?.trim()?.trimStart('0').orEmpty()
            val name = row.getOrNull(nameIndex)?.trim().orEmpty()
            if (code.isNotEmpty() && name.isNotEmpty()) code to name else null
        }.toMap()
    }.getOrDefault(emptyMap())

    fun fundNames(context: Context): List<String> = runCatching {
        context.assets.open("stockInfo_jp_trust.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line -> CsvTokenizer.parse(line).firstOrNull()?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }.toList()
        }
    }.getOrDefault(emptyList())
}
