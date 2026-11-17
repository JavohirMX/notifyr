package com.javohirmx.notifyr.domain.rules

import java.util.concurrent.ConcurrentHashMap

/**
 * Matches keywords as whole words instead of raw substrings.
 *
 * The previous `content.contains(keyword)` checks produced obvious false positives:
 * "now" matched "know", "sale" matched "wholesale", "help" matched "helpful", and so on.
 * A short notification could therefore be silently downgraded or marked urgent for the
 * wrong reason.
 *
 * Matching rules:
 * - The keyword must start and end on a word boundary (letters/digits count as word chars).
 * - A trailing "s"/"es" is tolerated so "sale" still matches "sales" and "offer" matches
 *   "offers" without matching "wholesale"/"offered elsewhere" style substrings.
 * - Spaces inside a keyword are preserved, so "call me" and "action required" match phrases.
 * - Regex keywords are still handled by the caller (they opt in explicitly).
 */
internal object KeywordMatcher {

    // Classification runs on every posted notification, so avoid recompiling the same
    // short patterns over and over. User-defined keywords are included, but the cache is
    // bounded so a pathological rule set cannot grow it without limit.
    private const val MAX_CACHED_PATTERNS = 256
    private val patternCache = ConcurrentHashMap<String, Regex>()

    fun matches(content: String, keyword: String): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty() || content.isEmpty()) return false

        // Punctuation-only keywords (for example "?") are not word-like; the callers that
        // use them only care about presence, and word-boundary lookbehind would reject them.
        if (trimmed.none { it.isLetterOrDigit() }) {
            return content.contains(trimmed, ignoreCase = true)
        }

        return patternFor(trimmed).containsMatchIn(content)
    }

    private fun patternFor(keyword: String): Regex {
        patternCache[keyword]?.let { return it }
        if (patternCache.size >= MAX_CACHED_PATTERNS) {
            patternCache.clear()
        }
        return buildPattern(keyword).also { patternCache[keyword] = it }
    }

    private fun buildPattern(keyword: String): Regex {
        val escaped = Regex.escape(keyword)
        // Allow a simple plural on the final token so "sale" matches "sales" and
        // "offer" matches "offers". The lookarounds still prevent substring matches.
        val plural = if (keyword.last().isLetterOrDigit()) "(?:s|es)?" else ""
        return Regex(
            "(?<![\\p{L}\\p{N}])$escaped$plural(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
    }
}
