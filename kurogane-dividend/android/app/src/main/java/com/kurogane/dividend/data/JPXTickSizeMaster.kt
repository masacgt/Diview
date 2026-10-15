package com.kurogane.dividend.data

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** Official JPX tick-table eligibility used only to choose the displayed precision. */
object JPXTickSizeMaster {
    private val lock = Any()
    @Volatile private var periods: List<EligibilityPeriod> = emptyList()
    @Volatile private var etfCodes: Set<String> = emptySet()
    @Volatile private var etfValidFrom: LocalDate? = null
    @Volatile private var etfValidThrough: LocalDate? = null
    @Volatile private var etfUnitChanges: List<EtfUnitChange> = emptyList()

    fun load(context: Context) {
        if (periods.isNotEmpty()) return
        synchronized(lock) {
            if (periods.isNotEmpty()) return
            val loaded = runCatching {
                val root = context.applicationContext.assets.open("jpx_tick_eligibility.json")
                    .bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
                val rows = root.getJSONArray("periods")
                val equityPeriods = buildList {
                    for (index in 0 until rows.length()) {
                        val row = rows.getJSONObject(index)
                        val codes = row.getJSONArray("codes")
                        add(EligibilityPeriod(
                            validFrom = LocalDate.parse(row.getString("valid_from")),
                            validThrough = LocalDate.parse(row.getString("valid_through")),
                            codes = buildSet {
                                for (codeIndex in 0 until codes.length()) {
                                    normalizeCode(codes.getString(codeIndex))?.let(::add)
                                }
                            }
                        ))
                    }
                }
                val etf = root.optJSONObject("etf_10_plus")
                val etfBaseCodes = etf?.optJSONArray("codes")?.let { codes ->
                    buildSet {
                        for (index in 0 until codes.length()) {
                            normalizeCode(codes.getString(index))?.let(::add)
                        }
                    }
                }.orEmpty()
                val changes = etf?.optJSONArray("scheduled_unit_changes")?.let { rows ->
                    buildList {
                        for (index in 0 until rows.length()) {
                            val row = rows.getJSONObject(index)
                            add(EtfUnitChange(
                                effectiveFrom = LocalDate.parse(row.getString("effective_from")),
                                addedCodes = readCodes(row.optJSONArray("add_codes")),
                                removedCodes = readCodes(row.optJSONArray("remove_codes"))
                            ))
                        }
                    }.sortedBy(EtfUnitChange::effectiveFrom)
                }.orEmpty()
                LoadedMaster(
                    equityPeriods = equityPeriods,
                    etfCodes = etfBaseCodes,
                    etfValidFrom = etf?.optString("valid_from")?.takeIf(String::isNotBlank)?.let(LocalDate::parse),
                    etfValidThrough = etf?.optString("valid_through")?.takeIf(String::isNotBlank)?.let(LocalDate::parse),
                    etfUnitChanges = changes
                )
            }.getOrElse { LoadedMaster() }
            etfCodes = loaded.etfCodes
            etfValidFrom = loaded.etfValidFrom
            etfValidThrough = loaded.etfValidThrough
            etfUnitChanges = loaded.etfUnitChanges
            periods = loaded.equityPeriods
        }
    }

    fun hasTenthYenTick(
        code: String,
        price: Double,
        date: LocalDate = LocalDate.now(JAPAN_ZONE)
    ): Boolean {
        if (!price.isFinite() || price <= 0.0 || price > 1_000.0) return false
        val normalized = normalizeCode(code) ?: return false
        val eligibleEquity = periods.any { date in it.validFrom..it.validThrough && normalized in it.codes }
        if (eligibleEquity) return true

        val from = etfValidFrom ?: return false
        val through = etfValidThrough ?: return false
        if (date !in from..through) return false
        var eligibleEtf = normalized in etfCodes
        etfUnitChanges.asSequence().filter { date >= it.effectiveFrom }.forEach { change ->
            if (normalized in change.addedCodes) eligibleEtf = true
            if (normalized in change.removedCodes) eligibleEtf = false
        }
        return eligibleEtf
    }

    private fun normalizeCode(value: String): String? = value.trim().uppercase(Locale.ROOT)
        .removeSuffix(".T")
        .takeIf { it.matches(Regex("(?:[0-9]{4}|[0-9]{3}[A-Z])")) }

    private fun readCodes(codes: org.json.JSONArray?): Set<String> = codes?.let {
        buildSet {
            for (index in 0 until it.length()) {
                normalizeCode(it.getString(index))?.let(::add)
            }
        }
    }.orEmpty()

    private data class LoadedMaster(
        val equityPeriods: List<EligibilityPeriod> = emptyList(),
        val etfCodes: Set<String> = emptySet(),
        val etfValidFrom: LocalDate? = null,
        val etfValidThrough: LocalDate? = null,
        val etfUnitChanges: List<EtfUnitChange> = emptyList()
    )

    private data class EtfUnitChange(
        val effectiveFrom: LocalDate,
        val addedCodes: Set<String>,
        val removedCodes: Set<String>
    )

    private data class EligibilityPeriod(
        val validFrom: LocalDate,
        val validThrough: LocalDate,
        val codes: Set<String>
    )

    private val JAPAN_ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
}
