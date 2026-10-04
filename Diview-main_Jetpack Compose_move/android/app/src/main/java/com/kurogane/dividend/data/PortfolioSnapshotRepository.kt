package com.kurogane.dividend.data

import android.content.Context
import com.kurogane.dividend.model.YearlyAssetSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class PortfolioSnapshotRepository(context: Context) {

    private val prefs = context.getSharedPreferences("portfolio_snapshots", Context.MODE_PRIVATE)

    fun load(): List<YearlyAssetSnapshot> {
        val raw = prefs.getString(KEY_SNAPSHOTS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        YearlyAssetSnapshot(
                            year = item.getString("year"),
                            principal = item.getInt("principal"),
                            valuation = item.getInt("valuation")
                        )
                    )
                }
            }.sortedBy { it.year }
        }.getOrDefault(emptyList())
    }

    fun upsertCurrentYear(principal: Int, valuation: Int): List<YearlyAssetSnapshot> {
        val year = LocalDate.now().year.toString()
        val updated = load()
            .filterNot { it.year == year }
            .plus(YearlyAssetSnapshot(year, principal, valuation))
            .sortedBy { it.year }

        val array = JSONArray()
        updated.forEach { snapshot ->
            array.put(
                JSONObject()
                    .put("year", snapshot.year)
                    .put("principal", snapshot.principal)
                    .put("valuation", snapshot.valuation)
            )
        }
        prefs.edit().putString(KEY_SNAPSHOTS, array.toString()).apply()
        return updated
    }

    companion object {
        private const val KEY_SNAPSHOTS = "snapshots"
    }
}
