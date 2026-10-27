package com.kurogane.dividend.data

/** Reuse parsed JSON until its persisted representation changes, including after reset/import. */
internal class ParsedPreferenceCache<T> {
    private var snapshot: Pair<String?, T>? = null

    @Synchronized
    fun read(raw: String?, parse: (String?) -> T): T {
        snapshot?.let { if (it.first == raw) return it.second }
        return parse(raw).also { snapshot = raw to it }
    }
}
