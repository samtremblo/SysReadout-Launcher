package com.asnidev.sysreadout.data

import org.json.JSONObject
import kotlin.math.pow

/**
 * How often and how recently something was opened from SysReadout, as one
 * decaying score: each launch adds 1, and the total halves every [HALF_LIFE_MS].
 * An app opened daily stays near the top; one opened a lot last month fades.
 * Only apps and shortcuts are recorded (never contacts), on this phone only.
 */
data class Usage(val score: Double, val last: Long) {
    fun weightAt(now: Long): Double = score * decay(now - last)

    companion object {
        const val HALF_LIFE_MS = 7L * 24 * 60 * 60 * 1000
        private fun decay(elapsed: Long): Double = 0.5.pow(elapsed.coerceAtLeast(0).toDouble() / HALF_LIFE_MS)
    }
}

object LaunchHistory {
    /** Entries kept; the faintest go first. */
    const val MAX_ENTRIES = 300

    fun bump(history: Map<String, Usage>, key: String, now: Long): Map<String, Usage> {
        val next = history.toMutableMap()
        next[key] = Usage((history[key]?.weightAt(now) ?: 0.0) + 1.0, now)
        if (next.size > MAX_ENTRIES) {
            next.entries.sortedBy { it.value.weightAt(now) }.take(next.size - MAX_ENTRIES).forEach { next.remove(it.key) }
        }
        return next
    }

    fun appKey(key: AppKey) = "app:" + key.encode()
    fun shortcutKey(key: String) = "shortcut:$key"

    fun toJson(history: Map<String, Usage>): String = JSONObject().apply {
        history.forEach { (k, u) -> put(k, JSONObject().put("s", u.score).put("t", u.last)) }
    }.toString()

    fun fromJson(s: String?): Map<String, Usage> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().mapNotNull { k ->
            val e = o.optJSONObject(k) ?: return@mapNotNull null
            val score = e.optDouble("s", Double.NaN).takeIf { it.isFinite() && it > 0 } ?: return@mapNotNull null
            k to Usage(score, e.optLong("t", 0))
        }.toMap()
    }.getOrDefault(emptyMap())
}
