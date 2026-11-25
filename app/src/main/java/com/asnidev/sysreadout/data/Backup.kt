package com.asnidev.sysreadout.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * A backup file: every saved setting (home, drawer, log, theme, presets,
 * monitor) plus imported fonts, as one JSON document the user saves wherever
 * they like. Nothing else is in it: no contacts, no log data, no crash report.
 *
 * Settings are stored by DataStore key with their type, so keys added in
 * later versions round-trip without this file knowing about them.
 */
data class Backup(
    val settings: Map<String, Any>,
    val fonts: Map<String, ByteArray> = emptyMap(),
    val appVersion: String = "",
) {
    fun toJson(encode: (ByteArray) -> String = ::base64): String {
        val s = JSONObject()
        settings.toSortedMap().forEach { (key, value) ->
            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "bool").put("v", value)
                is Int -> JSONObject().put("t", "int").put("v", value)
                is Long -> JSONObject().put("t", "long").put("v", value)
                is Float -> JSONObject().put("t", "float").put("v", value.toDouble())
                is String -> JSONObject().put("t", "string").put("v", value)
                is Set<*> -> JSONObject().put("t", "strings").put("v", JSONArray(value.map { it.toString() }.sorted()))
                else -> null
            }
            entry?.let { s.put(key, it) }
        }
        val f = JSONObject()
        fonts.toSortedMap().forEach { (name, bytes) -> f.put(name, encode(bytes)) }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("app", appVersion)
            .put("settings", s)
            .put("fonts", f)
            .toString(2)
    }

    /** Thrown for a file that isn't a SysReadout backup, or one from a newer, incompatible version. */
    class InvalidBackup(message: String) : Exception(message)

    companion object {
        const val FORMAT = "sysreadout-backup"
        const val VERSION = 1

        /** Imported fonts bigger than this are left out of backups (and refused on restore). */
        const val MAX_FONT_BYTES = 8 * 1024 * 1024

        private fun base64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
        private fun unbase64(s: String): ByteArray = Base64.decode(s, Base64.DEFAULT)

        /** A safe file name for a restored font, or null if nothing usable is left. */
        fun fontName(name: String): String? {
            val clean = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.')
            return clean.takeIf {
                it.isNotEmpty() && (it.endsWith(".ttf", true) || it.endsWith(".otf", true))
            }
        }

        fun fromJson(text: String, decode: (String) -> ByteArray = ::unbase64): Backup {
            val root = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw InvalidBackup("not a sysreadout backup")
            }
            if (root.optString("format") != FORMAT) throw InvalidBackup("not a sysreadout backup")
            if (root.optInt("version", 0) > VERSION) throw InvalidBackup("made by a newer sysreadout; update first")

            val settings = LinkedHashMap<String, Any>()
            val s = root.optJSONObject("settings") ?: JSONObject()
            s.keys().forEach { key ->
                val e = s.optJSONObject(key) ?: return@forEach
                val value: Any? = when (e.optString("t")) {
                    "bool" -> e.opt("v") as? Boolean
                    "int" -> (e.opt("v") as? Number)?.toInt()
                    "long" -> (e.opt("v") as? Number)?.toLong()
                    "float" -> (e.opt("v") as? Number)?.toFloat()
                    "string" -> e.opt("v") as? String
                    "strings" -> e.optJSONArray("v")?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
                    else -> null // a type from a later version: skip that one setting
                }
                if (value != null) settings[key] = value
            }

            val fonts = LinkedHashMap<String, ByteArray>()
            val f = root.optJSONObject("fonts") ?: JSONObject()
            f.keys().forEach { name ->
                val safe = fontName(name) ?: return@forEach
                val bytes = runCatching { decode(f.getString(name)) }.getOrNull() ?: return@forEach
                if (bytes.isNotEmpty() && bytes.size <= MAX_FONT_BYTES) fonts[safe] = bytes
            }
            return Backup(settings, fonts, root.optString("app"))
        }
    }

    // ByteArray has identity equals; compare contents so round-trip tests mean something.
    override fun equals(other: Any?): Boolean =
        other is Backup && settings == other.settings && appVersion == other.appVersion &&
            fonts.keys == other.fonts.keys && fonts.all { (k, v) -> other.fonts[k]?.contentEquals(v) == true }

    override fun hashCode(): Int = settings.hashCode() * 31 + fonts.keys.hashCode()
}
