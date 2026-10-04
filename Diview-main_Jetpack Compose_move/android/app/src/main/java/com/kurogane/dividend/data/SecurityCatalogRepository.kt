package com.kurogane.dividend.data

import android.content.Context

data class CatalogSecurity(val code: String, val name: String)

/** Names and codes bundled from the project's securities catalog for offline search. */
class SecurityCatalogRepository(context: Context) {
    val securities: List<CatalogSecurity> = context.assets.open("security_catalog.tsv").bufferedReader().useLines { lines ->
        lines.mapNotNull { line ->
            val separator = line.indexOf('\t')
            if (separator <= 0) null else CatalogSecurity(line.substring(0, separator), line.substring(separator + 1))
        }.toList()
    }
}
