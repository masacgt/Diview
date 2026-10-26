package com.kurogane.dividend.data

import com.kurogane.dividend.model.Category
import org.junit.Assert.*
import org.junit.Test

class SecurityCategoryCacheTest {
    @Test fun persistsPerCodeExpiryAndRefreshesAtExactly45DaysIncludingClockRollback() {
        val now = 1_800_000_000_000L
        val cache = SecurityCategoryCache.decode(SecurityCategoryCache()
            .updated(mapOf("1306" to "014", "8951" to "013", "285A" to null), now).encode())
        assertTrue(cache.dueCodes(listOf("13060", "8951.T", "285a"), now + SECURITY_CATEGORY_CACHE_MILLIS - 1).isEmpty())
        assertEquals(listOf("1306", "8951"), cache.dueCodes(listOf("1306", "8951"), now + SECURITY_CATEGORY_CACHE_MILLIS))
        assertEquals(listOf("1306"), cache.dueCodes(listOf("1306"), now - 1))
        assertEquals(listOf("1540"), cache.dueCodes(listOf("1306", "1540"), now + 1))
        // Refreshing one new holding must not extend the other holding's expiry.
        val updated = cache.updated(mapOf("1540" to "014"), now + 10)
        assertEquals(listOf("1306"), updated.dueCodes(listOf("1306", "1540"), now + SECURITY_CATEGORY_CACHE_MILLIS))
        assertEquals("014", updated.entries["1306"]?.category)
    }

    @Test fun malformedPersistentEntriesAreRetriedAndSuccessfulUnknownInvalidatesPreviousLabel() {
        val now = 1_800_000_000_000L
        val cache = SecurityCategoryCache.decode("""{"1306":{"category":"ETF","fetched_at":$now},"8951":{"category":"013","fetched_at":0}}""")
        assertEquals(listOf("1306", "8951"), cache.dueCodes(listOf("1306", "8951"), now))
        val updated = SecurityCategoryCache().updated(mapOf("1306" to "014"), now)
            .updated(mapOf("1306" to null), now + 1)
        assertNull(updated.entries["1306"]?.category)
        assertTrue(updated.dueCodes(listOf("1306"), now + 2).isEmpty())
    }

    @Test fun onlyUnclassifiedJapaneseHoldingsReceiveProductLabels() {
        val unknown = "東証33業種未分類"
        assertEquals("REIT（不動産投資信託）", assetManagementSectorLabel(Category.JAPAN, unknown, "013"))
        assertEquals("ETF（上場投資信託）", assetManagementSectorLabel(Category.JAPAN, unknown, "014"))
        assertEquals("電気機器", assetManagementSectorLabel(Category.JAPAN, "電気機器", "014"))
        assertEquals(unknown, assetManagementSectorLabel(Category.JAPAN, unknown, "011"))
        assertEquals(unknown, assetManagementSectorLabel(Category.JAPAN, unknown, null))
        assertEquals(unknown, assetManagementSectorLabel(Category.US, unknown, "014"))
    }
}
