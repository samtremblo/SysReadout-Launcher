package com.asnidev.sysreadout.ui

import java.text.Normalizer

/** Drawer search matching, shared by apps and contacts. */
object Search {

    private val marks = Regex("\\p{Mn}+")

    /** Lower case without accents, so "Zoé" matches "zoe". */
    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase()

    /**
     * Prefix matches first (0), then word-initial matches (1, "gm" → Google Maps),
     * then substring (2); null when [label] doesn't match. [q] must already be normalized.
     */
    fun rank(label: String, q: String): Int? = rankNormalized(normalize(label), q)

    /** [rank] for a label that is already [normalize]d (e.g. once per contact, not per keystroke). */
    fun rankNormalized(l: String, q: String): Int? {
        return when {
            l.startsWith(q) -> 0
            l.split(' ', '-', '.').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.startsWith(q) -> 1
            l.contains(q) -> 2
            else -> null
        }
    }

    /**
     * The rest of [label] after what was [typed], when the label starts with it
     * (ignoring case and accents): "ins" + Instagram → "tagram". Null otherwise.
     */
    fun completion(label: String, typed: String): String? {
        if (typed.isEmpty() || label.length < typed.length) return null
        return if (normalize(label.take(typed.length)) == normalize(typed)) label.drop(typed.length) else null
    }

    /** The items of [all] that match [q], best first; ties keep their order. */
    fun <T> filter(all: List<T>, q: String, boost: ((T) -> Double)? = null, label: (T) -> String): List<T> =
        filterNormalized(all, q, boost) { normalize(label(it)) }

    /**
     * [filter] where [key] already returns a normalized label. Within one match
     * rank, a higher [boost] (launch history) comes first; ties keep their order.
     */
    fun <T> filterNormalized(all: List<T>, q: String, boost: ((T) -> Double)? = null, key: (T) -> String): List<T> =
        if (q.isEmpty()) all else all
            .mapNotNull { item -> rankNormalized(key(item), q)?.let { item to it } }
            .sortedWith(compareBy<Pair<T, Int>> { it.second }.thenByDescending { boost?.invoke(it.first) ?: 0.0 })
            .map { it.first }
}
