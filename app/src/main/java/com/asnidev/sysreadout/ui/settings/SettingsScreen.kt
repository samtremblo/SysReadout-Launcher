package com.asnidev.sysreadout.ui.settings

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import com.asnidev.sysreadout.system.Torch
import com.asnidev.sysreadout.data.GestureKind
import com.asnidev.sysreadout.data.Gesture
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.data.AppKey
import com.asnidev.sysreadout.data.ContactAction
import com.asnidev.sysreadout.data.EntryStyle
import com.asnidev.sysreadout.data.HAlign
import com.asnidev.sysreadout.data.LockMode
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.data.VAlign
import com.asnidev.sysreadout.system.LockScreen
import com.asnidev.sysreadout.system.LockService
import com.asnidev.sysreadout.system.SystemActions
import com.asnidev.sysreadout.ui.AppPickerDialog
import com.asnidev.sysreadout.ui.Palette
import com.asnidev.sysreadout.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface Page {
    val title: String

    data object Root : Page { override val title = "" }
    data object Log : Page { override val title = "log" }
    data object Appearance : Page { override val title = "appearance" }
    data class Element(val element: StyleElement) : Page { override val title get() = element.title }
    data object About : Page { override val title = "about" }
    data object Crt : Page { override val title = "crt effects" }
    data object Shizuku : Page { override val title = "shizuku" }

    companion object {
        /** Top-level pages by name, for `--es page <name>`. */
        fun byName(name: String): Page? = listOf(Log, Appearance, About, Crt, Shizuku)
            .firstOrNull { it.title.substringBefore(' ') == name.lowercase() }
    }
}

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    var stack by remember { mutableStateOf(listOf<Page>(Page.Root)) }
    val page = stack.last()
    val open: (Page) -> Unit = { stack = stack + it }
    val back = { stack = stack.dropLast(1) }

    LaunchedEffect(vm.requestedPage) {
        vm.requestedPage?.let {
            stack = listOf(Page.Root, it)
            vm.requestedPage = null
        }
    }

    BackHandler(enabled = stack.size > 1, onBack = back)

    Column(Modifier.fillMaxSize().background(Palette.bg.copy(alpha = 0.94f)).systemBarsPadding()) {
        // Each page gets its own scroll position.
        key(page) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                Header(stack.drop(1).map { it.title }, onBack = if (stack.size > 1) back else null)
                when (page) {
                    Page.Root -> RootPage(vm, open)
                    Page.Log -> LogPage(vm, open)
                    Page.Appearance -> AppearancePage(vm, open)
                    is Page.Element -> ElementPage(vm, page.element)
                    Page.About -> AboutPage()
                    Page.Crt -> CrtPage(vm)
                    Page.Shizuku -> ShizukuPage(vm)
                }
            }
        }
    }
}

private enum class Picking { LEFT, RIGHT }

@Composable
private fun RootPage(vm: LauncherViewModel, open: (Page) -> Unit) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    val apps by vm.apps.collectAsState()
    val monitor by vm.monitor.collectAsState()
    val shizuku by vm.shizuku.state.collectAsState()
    val safe by vm.safeMode.collectAsState()
    var picking by remember { mutableStateOf<Picking?>(null) }

    fun appName(key: AppKey?): String =
        key?.let { k -> apps.firstOrNull { it.key == k }?.let(vm::label) } ?: "none"

    fun describe(g: Gesture): String = when (g.kind) {
        GestureKind.NONE -> "none"
        GestureKind.APP -> appName(g.app)
        else -> g.kind.title
    }
    var choosing by remember { mutableStateOf<Picking?>(null) }

    if (safe) {
        Section("safe mode")
        Note(
            "sysreadout crashed twice right after starting, so the log, the system monitor, shizuku, " +
                "the dns monitor and your look are paused. the crash report is under about.",
        )
        Link("resume", "switch everything back on ›") { vm.leaveSafeMode() }
    }

    if (!SystemActions.isDefaultLauncher(context)) {
        Section("launcher")
        Link("set as default home app") { SystemActions.openHomeSettings(context) }
    }

    Section("screens")
    Link("appearance", "fonts · colours · presets ›") { open(Page.Appearance) }
    Link("log", "${prefs.logRows.size} rows ›") { open(Page.Log) }
    Link("shizuku", (if (monitor.shizuku) shizuku.label else "off · optional") + " ›") { open(Page.Shizuku) }

    Section("home")
    Toggle("clock", prefs.showClock) { v -> vm.update { it.copy(showClock = v) } }
    Toggle("date", prefs.showDate) { v -> vm.update { it.copy(showDate = v) } }
    Toggle("log background", prefs.showLog) { v -> vm.update { it.copy(showLog = v) } }
    Cycle("entry style", prefs.entryStyle, EntryStyle.entries) { v -> vm.update { it.copy(entryStyle = v) } }
    Cycle("horizontal align", prefs.hAlign, HAlign.entries) { v -> vm.update { it.copy(hAlign = v) } }
    Cycle("vertical position", prefs.vAlign, VAlign.entries) { v -> vm.update { it.copy(vAlign = v) } }

    LockScreenSection(vm)

    Section("drawer")
    Toggle("open keyboard", prefs.autoKeyboard) { v -> vm.update { it.copy(autoKeyboard = v) } }
    Toggle("launch single match", prefs.autoLaunch) { v -> vm.update { it.copy(autoLaunch = v) } }
    ContactSearchToggle(vm)
    Toggle("google search fallback", prefs.webSearch) { v -> vm.update { it.copy(webSearch = v) } }
    if (prefs.webSearch) {
        Note("when nothing matches, the drawer offers to search google for what you typed (tap it or press go). it opens in your browser; nothing is sent until you do.")
    }

    Section("gestures")
    Link("swipe left", describe(prefs.swipeLeft)) { choosing = Picking.LEFT }
    Link("swipe right", describe(prefs.swipeRight)) { choosing = Picking.RIGHT }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Toggle("double-tap to lock", prefs.doubleTapLock) { v ->
            vm.update { it.copy(doubleTapLock = v) }
            if (v && !LockService.isRunning) SystemActions.openAccessibilitySettings(context)
        }
    } else {
        Note("double-tap to lock needs Android 9 or newer.")
    }

    Section("hidden apps")
    val hidden = apps.filter { it.key in prefs.hidden }
    if (hidden.isEmpty()) Text("  (none)", style = Type.row, color = Palette.dim)
    hidden.forEach { app -> Link(vm.label(app), "[unhide]") { vm.unhide(app.key) } }

    BackupSection(vm)

    Section("about")
    Link("about & licenses") { open(Page.About) }

    picking?.let { side ->
        AppPickerDialog(
            apps = apps.filter { it.key !in prefs.hidden },
            label = vm::label,
            onDismiss = { picking = null },
        ) { app ->
            val g = Gesture.app(app?.key)
            vm.update { if (side == Picking.LEFT) it.copy(swipeLeft = g) else it.copy(swipeRight = g) }
            picking = null
        }
    }

    choosing?.let { side ->
        GesturePickerDialog(onDismiss = { choosing = null }) { kind ->
            choosing = null
            if (kind == GestureKind.APP) {
                picking = side
            } else {
                val g = Gesture(kind)
                vm.update { if (side == Picking.LEFT) it.copy(swipeLeft = g) else it.copy(swipeRight = g) }
                if (kind == GestureKind.LOCK && !LockService.isRunning) SystemActions.openAccessibilitySettings(context)
            }
        }
    }
}

/** The actions a swipe can do on this phone. */
@Composable
private fun GesturePickerDialog(onDismiss: () -> Unit, onPick: (GestureKind) -> Unit) {
    val context = LocalContext.current
    val kinds = remember {
        GestureKind.entries.filter {
            when (it) {
                GestureKind.LOCK -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                GestureKind.FLASHLIGHT -> Torch.available(context)
                else -> true
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancel", style = Type.row) } },
        title = { Text("swipe does", style = Type.row) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(kinds) { kind ->
                    Text(
                        kind.title,
                        style = Type.row,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(kind) }.padding(vertical = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun BackupSection(vm: LauncherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf<Uri?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = vm.saveBackup(uri)
                Toast.makeText(context, if (ok) "backup saved" else "couldn't write the backup", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }

    Section("backup")
    Link("save backup", "to a file ›") {
        save.launchOr(context, "no app to save a file with", "sysreadout-backup-${LocalDate.now()}.json")
    }
    // Some file pickers label .json as text or as nothing at all.
    Link("restore backup", "from a file ›") {
        pick.launchOr(context, "no app to pick a file with", arrayOf("application/json", "text/plain", "application/octet-stream"))
    }
    Note("home, drawer, log, look, presets, monitor settings and imported fonts. no contacts, log data or wallpaper images are in it.")

    restoring?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("restore backup?", style = Type.row) },
            text = { Text("this replaces all your current settings and pinned apps.", style = Type.small) },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch {
                        val problem = vm.restoreBackup(uri)
                        Toast.makeText(context, problem ?: "backup restored", Toast.LENGTH_LONG).show()
                    }
                }) { Text("restore", style = Type.row) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text("cancel", style = Type.row) } },
        )
    }
}

@Composable
private fun ContactSearchToggle(vm: LauncherViewModel) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    // Not observable by itself: re-read here and after each permission answer.
    var permitted by remember { mutableStateOf(vm.contacts.hasPermission()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permitted = vm.contacts.hasPermission()
        if (granted) {
            vm.update { it.copy(searchContacts = true) }
            vm.refreshContacts()
        } else {
            Toast.makeText(context, "without the contacts permission there's nothing to search", Toast.LENGTH_SHORT).show()
        }
    }
    Toggle("search contacts", prefs.searchContacts) { v ->
        when {
            !v -> vm.update { it.copy(searchContacts = false) }
            permitted -> vm.update { it.copy(searchContacts = true) }
            else -> ask.launchOr(context, "this phone has no permission screen", Manifest.permission.READ_CONTACTS)
        }
    }
    if (prefs.searchContacts) {
        Cycle(
            "tapping a contact",
            prefs.contactAction,
            ContactAction.entries,
            show = {
                when (it) {
                    ContactAction.OPEN -> "opens it"
                    ContactAction.CALL -> "calls"
                    ContactAction.TEXT -> "texts"
                }
            },
        ) { v -> vm.update { it.copy(contactAction = v) } }
        if (permitted) {
            Note("contacts show under the apps while you type; long-press one for the other actions. calling opens the dialer with the number filled in. a contact without a number always opens. contacts stay on this phone and are never saved by sysreadout.")
        } else {
            Note("the contacts permission was taken away, so no contacts show.")
            Link("allow again") { ask.launchOr(context, "this phone has no permission screen", Manifest.permission.READ_CONTACTS) }
        }
    }
}

@Composable
private fun LockScreenSection(vm: LauncherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { LockScreen.setImage(context, uri) }
                Toast.makeText(context, if (ok) "lock screen set" else "couldn't use that image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Section("lock screen")
    Cycle(
        "wallpaper",
        prefs.lockMode,
        LockMode.entries,
        show = {
            when (it) {
                LockMode.OFF -> "leave alone"
                LockMode.IMAGE -> "your image"
                LockMode.SNAPSHOT -> "log snapshot"
            }
        },
    ) { v ->
        val was = prefs.lockMode
        vm.update { it.copy(lockMode = v) }
        if (v == LockMode.SNAPSHOT) vm.updateLockSnapshot()
        // Leaving image or snapshot: take SysReadout's picture off the lock screen again.
        if (v == LockMode.OFF && was != LockMode.OFF) scope.launch(Dispatchers.IO) { LockScreen.clear(context) }
    }
    when (prefs.lockMode) {
        LockMode.OFF -> Note("SysReadout doesn't touch your lock-screen wallpaper. switching here from image or snapshot gives the lock screen your home wallpaper back.")
        LockMode.IMAGE -> Link("choose image") { picker.launchOr(context, "no app to pick an image with", arrayOf("image/*")) }
        LockMode.SNAPSHOT -> {
            Link("update now") {
                vm.updateLockSnapshot()
                Toast.makeText(context, "lock screen updated", Toast.LENGTH_SHORT).show()
            }
            Note("the log, drawn with your theme and crt effects, redrawn each time the screen turns off (at most once a minute). android draws its clock and notifications on top.")
        }
    }
}
