package com.asnidev.sysreadout.apps

import android.app.Activity
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.Toast

/**
 * Android sends "add to home screen" requests (e.g. a web page from Chrome) to
 * the default home app. The asking app already showed its own confirmation,
 * so the shortcut is accepted straight away and appears in the drawer.
 * Widget requests are declined: SysReadout has no widgets.
 */
class PinShortcutActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = runCatching {
            getSystemService(LauncherApps::class.java).getPinItemRequest(intent)
        }.getOrNull()
        val label = request?.shortcutInfo?.shortLabel
        val ok = request != null && request.isValid &&
            request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT &&
            runCatching { request.accept() }.getOrDefault(false)
        Toast.makeText(
            this,
            if (ok) "\"$label\" added to the drawer" else "sysreadout can only add shortcuts, not widgets",
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }
}
