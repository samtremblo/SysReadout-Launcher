package com.asnidev.sysreadout.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

/**
 * A shortcut the drawer can open. [pinned] ones were added to SysReadout (e.g.
 * Chrome's "add to home screen"); the others are an app's own shortcuts
 * ("new incognito tab"), listed only when the user switches that on.
 */
data class ShortcutEntry(
    val id: String,
    val pkg: String,
    val user: Long,
    val label: String,
    val pinned: Boolean,
    val isWork: Boolean,
) {
    /** Unique across apps and profiles, for list keys. */
    val key: String get() = "$pkg/$id#$user"
}

/**
 * Shortcuts through LauncherApps. Android only lets the default home app read
 * them, so everything here is empty while SysReadout isn't the home app.
 */
class ShortcutRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _shortcuts = MutableStateFlow<List<ShortcutEntry>>(emptyList())
    val shortcuts: StateFlow<List<ShortcutEntry>> = _shortcuts

    @Volatile private var includeAppShortcuts = false

    private val callback = object : LauncherApps.Callback() {
        override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = refresh()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    // Conflated like AppRepository's: one loader, newest request wins.
    private val refreshes = Channel<Unit>(Channel.CONFLATED)

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        scope.launch(Dispatchers.IO) {
            for (request in refreshes) {
                runCatching { _shortcuts.value = load() }.onFailure { Log.w(TAG, "couldn't list shortcuts", it) }
            }
        }
        refresh()
    }

    /** Whether apps' own shortcuts are listed too, not just pinned ones. */
    fun setIncludeAppShortcuts(on: Boolean) {
        if (includeAppShortcuts == on) return
        includeAppShortcuts = on
        refresh()
    }

    /** Also called on resume: becoming the default home app grants access without any callback. */
    fun refresh() {
        refreshes.trySend(Unit)
    }

    fun close() {
        refreshes.close()
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    val hasAccess: Boolean
        get() = runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    private fun load(): List<ShortcutEntry> {
        if (!hasAccess) return emptyList()
        val me = android.os.Process.myUserHandle()
        var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        if (includeAppShortcuts) {
            flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
        }
        val query = LauncherApps.ShortcutQuery().setQueryFlags(flags)
        val collator = Collator.getInstance()
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            // A locked or paused profile throws; the others still count.
            runCatching { launcherApps.getShortcuts(query, user) }.getOrNull().orEmpty()
                .filter { it.isEnabled && it.`package` != context.packageName }
                .mapNotNull { s ->
                    val label = (s.shortLabel ?: s.longLabel)?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ShortcutEntry(s.id, s.`package`, serial, label, s.isPinned, user != me)
                }
        }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun handle(user: Long): UserHandle? =
        runCatching { userManager.getUserForSerialNumber(user) }.getOrNull()

    fun launch(s: ShortcutEntry): Boolean {
        val user = handle(s.user) ?: return false
        return try {
            launcherApps.startShortcut(s.pkg, s.id, null, null, user)
            true
        } catch (e: RuntimeException) { // ActivityNotFoundException, IllegalStateException (locked user), SecurityException
            Log.w(TAG, "couldn't open shortcut ${s.key}", e)
            false
        }
    }

    /** Takes a pinned shortcut out of the drawer (the app may ask to pin it again later). */
    fun remove(s: ShortcutEntry) {
        val user = handle(s.user) ?: return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val keep = launcherApps.getShortcuts(
                    LauncherApps.ShortcutQuery().setPackage(s.pkg).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED),
                    user,
                ).orEmpty().map { it.id }.filter { it != s.id }
                launcherApps.pinShortcuts(s.pkg, keep, user)
            }.onFailure { Log.w(TAG, "couldn't remove shortcut ${s.key}", it) }
            refresh()
        }
    }

    private companion object {
        const val TAG = "ShortcutRepository"
    }
}
