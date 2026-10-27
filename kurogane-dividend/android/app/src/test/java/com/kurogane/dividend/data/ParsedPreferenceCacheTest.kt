package com.kurogane.dividend.data

import org.junit.Assert.*
import org.junit.Test

class ParsedPreferenceCacheTest {
    @Test fun unchangedPreferencesReuseParsingButWritesAndResetInvalidateIt() {
        val cache = ParsedPreferenceCache<List<String>>()
        var parses = 0
        val parse: (String?) -> List<String> = { parses++; it?.split(',').orEmpty() }
        val first = cache.read("7203,2914", parse)
        assertSame(first, cache.read("7203,2914", parse))
        assertEquals(1, parses)
        assertEquals(listOf("7203"), cache.read("7203", parse))
        assertEquals(emptyList<String>(), cache.read(null, parse))
        assertEquals(3, parses)
    }
}
