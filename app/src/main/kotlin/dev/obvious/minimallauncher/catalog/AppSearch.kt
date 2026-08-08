package dev.obvious.minimallauncher.catalog


import java.text.Normalizer
import java.util.Locale

object AppSearch {
    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()

    fun score(label: String, rawQuery: String): Int {
        val name = normalize(label)
        val query = normalize(rawQuery)
        if (query.isEmpty()) return 0
        if (name == query) return 100_000
        if (name.startsWith(query)) return 80_000 - name.length

        val words = name.split(WORD_BREAKS)
        val wordIndex = words.indexOfFirst { it.startsWith(query) }
        if (wordIndex >= 0) return 70_000 - wordIndex * 100 - name.length

        val contiguous = name.indexOf(query)
        if (contiguous >= 0) return 60_000 - contiguous * 100 - name.length

        var queryIndex = 0
        var previousMatch = -1
        var gaps = 0
        name.forEachIndexed { index, character ->
            if (queryIndex < query.length && character == query[queryIndex]) {
                if (previousMatch >= 0) gaps += index - previousMatch - 1
                previousMatch = index
                queryIndex += 1
            }
        }
        return if (queryIndex == query.length) 40_000 - gaps * 100 - name.length else -1
    }

    fun rank(apps: List<AppEntry>, rawQuery: String): List<AppEntry> {
        val query = normalize(rawQuery)
        val alphabetical = compareBy<AppEntry>({ normalize(it.label) }, { it.stableId })
        if (query.isEmpty()) return apps.sortedWith(alphabetical)
        return apps.map { it to score(it.label, query) }
            .filter { it.second >= 0 }
            .sortedWith(compareByDescending<Pair<AppEntry, Int>> { it.second }.thenBy(alphabetical) { it.first })
            .map { it.first }
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val WORD_BREAKS = Regex("[^\\p{L}\\p{N}]+")
}
