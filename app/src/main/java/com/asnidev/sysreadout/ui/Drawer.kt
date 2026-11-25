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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.Screen
import com.asnidev.sysreadout.apps.AppEntry
import com.asnidev.sysreadout.apps.ContactEntry
import com.asnidev.sysreadout.data.ContactAction
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

    val q = Search.normalize(query.trim())
    val results = remember(q, apps, prefs.hidden, prefs.renames) {
        Search.filter(apps.filter { it.key !in prefs.hidden }, q, vm::label)
    }
    // Contacts only show for a search, never as a full list under the apps.
    val people = remember(q, contacts, prefs.searchContacts) {
        if (q.isEmpty() || !prefs.searchContacts) emptyList()
        else Search.filterNormalized(contacts, q) { it.match }.take(MAX_CONTACTS)
    }
    // Still reading contacts: don't open an app that a contact might also match.
    val waitingForContacts = prefs.searchContacts && !contactsLoaded && vm.contacts.hasPermission()

    val open: (AppEntry) -> Unit = { app ->
        if (!vm.launch(app.key)) Toast.makeText(context, "can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }
    val trimmed = query.trim()
    val offerWeb = prefs.webSearch && q.isNotEmpty() && results.isEmpty() && people.isEmpty()
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
    // Only an app opens by itself, and only when it's the one match of any kind.
    LaunchedEffect(q, results.size, people.size, waitingForContacts) {
        if (prefs.autoLaunch && q.isNotEmpty() && results.size == 1 && people.isEmpty() && !waitingForContacts) {
            open(results.first())
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
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = styled.drawer,
            cursorBrush = SolidColor(styled.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                results.firstOrNull()?.let(open) ?: people.firstOrNull()?.let(openContact) ?: if (offerWeb) searchWeb() else Unit
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
            items(results, key = { it.key.encode() }) { app -> DrawerRow(vm, app, styled, open) }
            if (people.isNotEmpty()) {
                item(key = "contacts-header") {
                    Text(
                        styled.text(StyleElement.DRAWER, "# contacts"),
                        style = styled.drawer,
                        color = styled.drawer.color.copy(alpha = 0.45f),
                        maxLines = 1,
                        modifier = Modifier.padding(top = if (results.isEmpty()) 0.dp else 12.dp, bottom = 2.dp),
                    )
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
        }
    }
}
