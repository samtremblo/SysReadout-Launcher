package com.asnidev.sysreadout.ui

import java.text.Normalizer

/**
 * A name prepared for matching once (per app or contact, not per keystroke):
 * accent-free lower case, split into words at spaces, punctuation and
 * camelCase ("WhatsApp" → whats, app), with its initials and a spaceless form.
 * [digits] is a phone number reduced to its digits, and [aliases] other names
 * (a contact's nicknames), matched like the label itself.
 */
class Searchable(val label: String, number: String? = null, aliases: List<String> = emptyList()) {
    val aliases: List<Searchable> = aliases.filter { it.isNotBlank() }.map { Searchable(it) }
    val full: String = Search.normalize(label)
    val words: List<String> = Search.words(label)
    val initials: String = words.joinToString("") { it.take(1) }
    val compact: String = words.joinToString("")
    val digits: String = number?.filter { it.isDigit() }.orEmpty()
}

/** Drawer search matching, shared by apps, shortcuts and contacts. */
object Search {

    /**
     * How well a name matched, best first. Everything from [SUBSEQUENCE] on is
     * fuzzy: listed only when nothing matched better, and never opened by itself.
     */
    enum class Tier {
        EXACT, // "maps" → Maps
        PREFIX, // "ins" → Instagram; also ignoring spaces: "whats app" → WhatsApp
        WORD_PREFIX, // "maps" → Google Maps
        INITIALS, // "gm" → Google Maps, "wa" → WhatsApp
        WORDS, // "goo ma" → Google Maps: every typed word starts a word of the name, in order
        NUMBER, // "5551" → a contact whose number contains it
        SUBSTRING, // "gram" → Instagram
        SUBSEQUENCE, // "ytb" → YouTube: letters in order, the first starting a word
        TYPO, // "instgram" → Instagram: one or two letters off
        ;

        val fuzzy: Boolean get() = this >= SUBSEQUENCE
    }

    /** What [find] matched: [fuzzy] when only fuzzy tiers matched anything. */
    data class Found<T>(val items: List<T>, val fuzzy: Boolean)

    private val marks = Regex("\\p{Mn}+")
    private val camel = Regex("(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})")
    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val spaces = Regex("\\s+")

    /** Lower case without accents, so "Zoé" matches "zoe". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase()

    /** Words of a name, normalized: "WhatsApp Business" → whats, app, business. */
    fun words(label: String): List<String> =
        label.split(separators).flatMap { it.split(camel) }.map(::normalize).filter { it.isNotEmpty() }

    /**
     * How [s] matches the query [q] (already [normalize]d), or null for no match.
     * Without [fuzzy], the costlier fuzzy tiers aren't tried.
     */
    fun match(s: Searchable, q: String, fuzzy: Boolean = true): Tier? {
        // The best of the name and its aliases (nicknames).
        val own = matchOne(s, q, fuzzy)
        if (s.aliases.isEmpty() || own == Tier.EXACT) return own
        return (listOf(own) + s.aliases.map { matchOne(it, q, fuzzy) }).filterNotNull().minOrNull()
    }

    /** The alias (nickname) that matches [q] better than the name itself, if any: worth showing. */
    fun matchedAlias(s: Searchable, q: String): String? {
        val own = matchOne(s, q, fuzzy = true)
        return s.aliases
            .mapNotNull { a -> matchOne(a, q, fuzzy = true)?.let { a to it } }
            .filter { own == null || it.second < own }
            .minByOrNull { it.second }?.first?.label
    }

    private fun matchOne(s: Searchable, q: String, fuzzy: Boolean): Tier? {
        val query = q.trim().replace(spaces, " ")
        if (query.isEmpty()) return null
        val tokens = query.split(' ')
        val joined = tokens.joinToString("")
        return when {
            s.full == query || s.compact == joined -> Tier.EXACT
            s.full.startsWith(query) || s.compact.startsWith(joined) -> Tier.PREFIX
            tokens.size == 1 && s.words.drop(1).any { it.startsWith(query) } -> Tier.WORD_PREFIX
            tokens.size == 1 && query.length >= 2 && s.initials.startsWith(query) -> Tier.INITIALS
            tokens.size > 1 && wordsInOrder(s.words, tokens) -> Tier.WORDS
            isNumberQuery(query) && s.digits.contains(query.filter { it.isDigit() }) -> Tier.NUMBER
            s.full.contains(query) -> Tier.SUBSTRING
            !fuzzy -> null
            joined.length >= 3 && subsequence(s, joined) -> Tier.SUBSEQUENCE
            joined.length >= 4 && closeEnough(s, joined) -> Tier.TYPO
            else -> null
        }
    }

    /** Each token starts a word, the words in the same order (others may sit between). */
    private fun wordsInOrder(words: List<String>, tokens: List<String>): Boolean {
        var w = 0
        for (t in tokens) {
            while (w < words.size && !words[w].startsWith(t)) w++
            if (w == words.size) return false
            w++
        }
        return true
    }

    private fun isNumberQuery(q: String) = q.count { it.isDigit() } >= 3 && q.all { it.isDigit() || it in "+-() " }

    /** [q]'s letters appear in order in the name, and the first one starts a word. */
    private fun subsequence(s: Searchable, q: String): Boolean {
        if (s.words.none { it.startsWith(q[0]) }) return false
        val start = s.words.indexOfFirst { it.startsWith(q[0]) }
        val text = s.words.drop(start).joinToString("")
        var i = 0
        for (c in text) if (i < q.length && c == q[i]) i++
        return i == q.length
    }

    /** Within 1 edit (2 from 8 letters) of the name's start, or of a word's start. */
    private fun closeEnough(s: Searchable, q: String): Boolean {
        val max = if (q.length >= 8) 2 else 1
        val candidates = listOf(s.compact) + s.words
        return candidates.any { c ->
            // Compare with the same length of the name, and one longer or shorter (a missed or extra letter).
            (q.length - 1..q.length + 1).any { n -> n in 1..c.length && distance(q, c.take(n)) <= max }
        }
    }

    /** Edit distance counting a swap of two neighbours as one edit ("teh" → "the"). */
    internal fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }

    /**
     * The items of [all] matching [q], best first: by tier, then [boost]
     * (launch history), then shorter names; ties keep their order. Fuzzy
     * matches are listed only when nothing matched better.
     */
    fun <T> find(all: List<T>, q: String, boost: ((T) -> Double)? = null, key: (T) -> Searchable): Found<T> {
        if (q.isBlank()) return Found(all, fuzzy = false)
        // Fuzzy tiers only when nothing matched properly: cheaper, and no noise next to good results.
        val strong = all.mapNotNull { item -> match(key(item), q, fuzzy = false)?.let { item to it } }
        val kept = strong.ifEmpty { all.mapNotNull { item -> match(key(item), q)?.let { item to it } } }
        val items = kept
            .sortedWith(
                compareBy<Pair<T, Tier>> { it.second }
                    .thenByDescending { boost?.invoke(it.first) ?: 0.0 }
                    .thenBy { key(it.first).label.length },
            )
            .map { it.first }
        return Found(items, fuzzy = strong.isEmpty() && kept.isNotEmpty())
    }

    /** [find] for plain labels (prepared on each call; fine for tests and short lists). */
    fun <T> filter(all: List<T>, q: String, boost: ((T) -> Double)? = null, label: (T) -> String): List<T> =
        find(all, q, boost) { Searchable(label(it)) }.items

    /**
     * The rest of [label] after what was [typed], when the label starts with it
     * (ignoring case and accents): "ins" + Instagram → "tagram". Null otherwise.
     */
    fun completion(label: String, typed: String): String? {
        if (typed.isEmpty() || label.length < typed.length) return null
        return if (normalize(label.take(typed.length)) == normalize(typed)) label.drop(typed.length) else null
    }
}
