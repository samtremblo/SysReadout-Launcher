package com.asnidev.sysreadout.data

/** What a home-screen swipe does. */
enum class GestureKind(val title: String) {
    NONE("nothing"),
    APP("open an app…"),
    DRAWER("app drawer"),
    NOTIFICATIONS("notifications"),
    QUICK_SETTINGS("quick settings"),
    LOCK("lock screen"),
    LAUNCHER_SETTINGS("sysreadout settings"),
    FLASHLIGHT("flashlight on/off"),
    CAMERA("camera"),
    DIALER("phone dialer"),
    ALARMS("alarms"),
    CALENDAR("calendar"),
}

/** A gesture's action; [app] is set only for [GestureKind.APP]. */
data class Gesture(val kind: GestureKind, val app: AppKey? = null) {

    /**
     * An app is saved as its bare AppKey, exactly as before 0.2.5, so older
     * versions still read it; the other actions as "action:NAME".
     */
    fun encode(): String? = when (kind) {
        GestureKind.NONE -> null
        GestureKind.APP -> app?.encode()
        else -> ACTION + kind.name
    }

    companion object {
        val NONE = Gesture(GestureKind.NONE)
        private const val ACTION = "action:"

        fun app(key: AppKey?) = if (key == null) NONE else Gesture(GestureKind.APP, key)

        fun decode(s: String?): Gesture {
            if (s.isNullOrEmpty()) return NONE
            if (s.startsWith(ACTION)) {
                val kind = runCatching { GestureKind.valueOf(s.removePrefix(ACTION)) }.getOrNull()
                return if (kind == null || kind == GestureKind.APP) NONE else Gesture(kind)
            }
            return app(AppKey.decode(s))
        }
    }
}
