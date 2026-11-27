package com.asnidev.sysreadout.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.Screen
import com.asnidev.sysreadout.apps.AppEntry
import com.asnidev.sysreadout.apps.ContactEntry
import com.asnidev.sysreadout.apps.ShortcutEntry
import com.asnidev.sysreadout.data.ContactAction
import com.asnidev.sysreadout.data.LaunchHistory
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.system.SystemActions

@Composable
fun Drawer(vm: LauncherViewModel) {
    val context = LocalContext.current
    val styled = LocalStyled.current
    val prefs by vm.prefs.collectAsState()
    val apps by vm.apps.collectAsState()
    val contacts by vm.contacts.contacts.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    val contactsLoaded by vm.contacts.loaded.collectAsState()
    val shortcuts by vm.shortcuts.shortcuts.collectAsState()

    val q = Search.normalize(query.trim())
    val history by vm.history.collectAsState()
    // Read once per query, not per comparison; null when history sorting is off.
    val now = remember(q) { System.currentTimeMillis() }
    val weight: ((String) -> Double)? =
        if (prefs.sortByHistory) { key -> history[key]?.weightAt(now) ?: 0.0 } else null
    // Names prepared for matching once per list change, not on every keystroke.
    val appNames = remember(apps, prefs.renames) { apps.associate { it.key to Searchable(vm.label(it)) } }
    val linkNames = remember(shortcuts) { shortcuts.associate { it.key to Searchable(it.label) } }
    val foundApps = remember(q, apps, appNames, prefs.hidden, history, prefs.sortByHistory) {
        Search.find(
            apps.filter { it.key !in prefs.hidden }, q,
            weight?.let { w -> { app: AppEntry -> w(LaunchHistory.appKey(app.key)) } },
        ) { appNames[it.key] ?: Searchable(vm.label(it)) }
    }
    // Pinned shortcuts are listed like apps; apps' own shortcuts only when searching.
    val foundLinks = remember(q, shortcuts, linkNames, history, prefs.sortByHistory) {
        Search.find(
            if (q.isEmpty()) shortcuts.filter { it.pinned } else shortcuts, q,
            weight?.let { w -> { s: ShortcutEntry -> w(LaunchHistory.shortcutKey(s.key)) } },
        ) { linkNames[it.key] ?: Searchable(it.label) }
    }
    // Before typing: the most used apps and shortcuts on top, then the full A–Z list.
    val frequent: List<Any> = remember(q, apps, shortcuts, prefs.hidden, history, prefs.sortByHistory) {
        if (q.isNotEmpty() || weight == null) return@remember emptyList()
        val candidates = apps.filter { it.key !in prefs.hidden }.map { it to weight(LaunchHistory.appKey(it.key)) } +
            shortcuts.filter { it.pinned }.map { it to weight(LaunchHistory.shortcutKey(it.key)) }
        candidates.filter { it.second >= MIN_FREQUENT }.sortedByDescending { it.second }.take(MAX_FREQUENT).map { it.first }
    }
    // Contacts only show for a search, never as a full list under the apps.
    val foundPeople = remember(q, contacts, prefs.searchContacts, prefs.hiddenContacts) {
        if (q.isEmpty() || !prefs.searchContacts) Search.Found(emptyList(), fuzzy = false)
        else Search.find(contacts.filter { it.lookupKey !in prefs.hiddenContacts }, q) { it.search }
    }
    // A proper match anywhere (app, shortcut or contact) hides the fuzzy guesses everywhere.
    val found = listOf(foundApps, foundLinks, foundPeople)
    val anyStrong = found.any { it.items.isNotEmpty() && !it.fuzzy }
    fun <T> shown(f: Search.Found<T>): List<T> = if (f.fuzzy && anyStrong) emptyList() else f.items
    val results = shown(foundApps)
    val links = shown(foundLinks)
    val people = shown(foundPeople).take(MAX_CONTACTS)
    // Only proper matches open by themselves; a guess waits for a tap or Go.
    val onlyGuesses = !anyStrong
    // Still reading contacts: don't open an app that a contact might also match.
    val waitingForContacts = prefs.searchContacts && !contactsLoaded && vm.contacts.hasPermission()

    val open: (AppEntry) -> Unit = { app ->
        if (!vm.launch(app.key)) Toast.makeText(context, "can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
    val trimmed = query.trim()
    // Also next to fuzzy guesses: the user may have meant something not on the phone.
    val offerWeb = prefs.webSearch && q.isNotEmpty() && onlyGuesses
    val openLink: (ShortcutEntry) -> Unit = { s ->
        if (!vm.launchShortcut(s)) Toast.makeText(context, "can't open ${s.label}", Toast.LENGTH_SHORT).show()
    }
    val searchWeb = {
        if (SystemActions.googleSearch(context, trimmed)) vm.screen = Screen.HOME
        else Toast.makeText(context, "no browser to search with", Toast.LENGTH_SHORT).show()
    }
    val openContact: (ContactEntry) -> Unit = { c ->
        if (!vm.openContact(c)) Toast.makeText(context, "no app for that", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        vm.refreshContacts() // picks up a permission granted or revoked in Android settings
        // Throws if the field isn't attached yet; the keyboard is a convenience, not worth a crash.
        if (prefs.autoKeyboard) runCatching { focus.requestFocus() }
    }
    // The one match of any kind opens by itself: an app, a shortcut, or a contact
    // (with the tap action from settings). Not while contacts are still being read.
    LaunchedEffect(q, results.size, links.size, people.size, waitingForContacts, onlyGuesses) {
        if (prefs.autoLaunch && q.isNotEmpty() && !waitingForContacts && !onlyGuesses &&
            results.size + links.size + people.size == 1
        ) {
            results.firstOrNull()?.let(open) ?: links.firstOrNull()?.let(openLink) ?: people.firstOrNull()?.let(openContact)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(styled.background.copy(alpha = 0.86f))
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // What Go would open, previewed after the cursor: the rest of its name, or "→ name".
        val topLabel = results.firstOrNull()?.let(vm::label)
            ?: links.firstOrNull()?.label
            ?: people.firstOrNull()?.name
        val ghost = when {
            topLabel == null || query.isBlank() -> ""
            else -> Search.completion(topLabel, query) ?: "  → ${styled.text(StyleElement.DRAWER, topLabel)}"
        }
        val ghostColor = styled.drawer.color.copy(alpha = 0.4f)
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = styled.drawer,
            visualTransformation = remember(ghost, ghostColor) { GhostCompletion(ghost, ghostColor) },
            cursorBrush = SolidColor(styled.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                results.firstOrNull()?.let(open)
                    ?: links.firstOrNull()?.let(openLink)
                    ?: people.firstOrNull()?.let(openContact)
                    ?: if (offerWeb) searchWeb() else Unit
            }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(bottom = 12.dp),
            decorationBox = { inner ->
                Row {
                    Text("> ", style = styled.drawer, color = styled.accent)
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                styled.text(StyleElement.DRAWER, "search"),
                                style = styled.drawer,
                                color = styled.drawer.color.copy(alpha = 0.45f),
                            )
                        }
                        inner()
                    }
                }
            },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (frequent.isNotEmpty()) {
                item(key = "frequent-header") { SectionLabel("# frequent", styled, top = false) }
                items(frequent, key = { "frequent:" + if (it is AppEntry) it.key.encode() else (it as ShortcutEntry).key }) { item ->
                    when (item) {
                        is AppEntry -> DrawerRow(vm, item, styled, open)
                        is ShortcutEntry -> ShortcutRow(vm, item, styled, openLink)
                    }
                }
                item(key = "all-header") { SectionLabel("# all", styled, top = true) }
            }
            items(results, key = { it.key.encode() }) { app -> DrawerRow(vm, app, styled, open) }
            if (links.isNotEmpty()) {
                item(key = "shortcuts-header") { SectionLabel("# shortcuts", styled, top = results.isNotEmpty()) }
                items(links, key = { "shortcut:${it.key}" }) { s -> ShortcutRow(vm, s, styled, openLink) }
            }
            if (people.isNotEmpty()) {
                item(key = "contacts-header") {
                    SectionLabel("# contacts", styled, top = results.isNotEmpty() || links.isNotEmpty())
                }
                items(people, key = { "contact:${it.id}" }) { c -> ContactRow(vm, c, styled, openContact) }
            }
            if (offerWeb) {
                item(key = "web-search") {
                    Text(
                        styled.text(StyleElement.DRAWER, "search google for \"$trimmed\" ›"),
                        style = styled.drawer,
                        color = styled.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = searchWeb).padding(vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(vm: LauncherViewModel, app: AppEntry, styled: Styled, open: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val pinned = app.key in prefs.pinned

    Box {
        Text(
            styled.text(StyleElement.DRAWER, vm.label(app)) + if (app.isWork) " [w]" else "",
            style = styled.drawer,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { open(app) }, onLongClick = { menu = true })
                .padding(vertical = 7.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (pinned) {
                MenuItem("remove from home") { menu = false; vm.unpin(app.key) }
            } else {
                MenuItem("pin to home") {
                    menu = false
                    if (!vm.pin(app.key)) Toast.makeText(context, "no vertical space left on home", Toast.LENGTH_SHORT).show()
                }
            }
            MenuItem("rename") { menu = false; renaming = true }
            MenuItem("hide") { menu = false; vm.hide(app.key) }
            MenuItem("app info") { menu = false; vm.repo.openInfo(app.key) }
            MenuItem("uninstall") { menu = false; vm.repo.uninstall(app.key) }
        }
    }
    if (renaming) {
        RenameDialog(app.label, vm.label(app), onDismiss = { renaming = false }) {
            vm.rename(app.key, it)
            renaming = false
        }
    }
}

private const val MAX_CONTACTS = 20

/** The "# frequent" section: at most this many, each opened about once in the last week or more. */
private const val MAX_FREQUENT = 6
private const val MIN_FREQUENT = 0.5

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(vm: LauncherViewModel, contact: ContactEntry, styled: Styled, open: (ContactEntry) -> Unit) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val run = { action: ContactAction ->
        menu = false
        if (!vm.openContact(contact, action)) Toast.makeText(context, "no app for that", Toast.LENGTH_SHORT).show()
    }

    Box {
        Text(
            styled.text(StyleElement.DRAWER, contact.name),
            style = styled.drawer,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { open(contact) }, onLongClick = { menu = true })
                .padding(vertical = 7.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuItem("open contact") { run(ContactAction.OPEN) }
            if (contact.number != null) {
                MenuItem("call") { run(ContactAction.CALL) }
                MenuItem("text") { run(ContactAction.TEXT) }
            }
            MenuItem("hide from search") {
                menu = false
                vm.hideContact(contact)
                Toast.makeText(context, "hidden · settings › drawer to bring it back", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, styled: Styled, top: Boolean) =
    Text(
        styled.text(StyleElement.DRAWER, text),
        style = styled.drawer,
        color = styled.drawer.color.copy(alpha = 0.45f),
        maxLines = 1,
        modifier = Modifier.padding(top = if (top) 12.dp else 0.dp, bottom = 2.dp),
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutRow(vm: LauncherViewModel, s: ShortcutEntry, styled: Styled, open: (ShortcutEntry) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Text(
            styled.text(StyleElement.DRAWER, s.label) + if (s.isWork) " [w]" else "",
            style = styled.drawer,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { open(s) }, onLongClick = { menu = s.pinned })
                .padding(vertical = 7.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuItem("remove shortcut") { menu = false; vm.shortcuts.remove(s) }
        }
    }
}

/**
 * Shows [ghost] dimmed after the typed text without making it part of it:
 * the cursor and selection stay within what was typed.
 */
private class GhostCompletion(private val ghost: String, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (ghost.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val shown = buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(color = color)) { append(ghost) }
        }
        val typed = text.length
        return TransformedText(
            shown,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset
                override fun transformedToOriginal(offset: Int) = offset.coerceAtMost(typed)
            },
        )
    }
}
