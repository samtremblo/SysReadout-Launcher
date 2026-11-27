package com.asnidev.sysreadout.apps

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.asnidev.sysreadout.ui.Searchable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** One contact as the drawer shows it. [number] is its primary (or first) phone number, if any. */
data class ContactEntry(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val number: String?,
    val nicknames: List<String> = emptyList(),
) {
    /** [name], [number] and [nicknames] prepared once for matching, so typing doesn't redo it for every contact. */
    val search: Searchable = Searchable(name, number, nicknames)
}

/**
 * The phone's contacts, for drawer search. Read only while the user has
 * "search contacts" on and has granted the contacts permission; kept in memory
 * only (never saved, never sent anywhere) and dropped when switched off.
 */
class ContactRepository(private val context: Context, private val scope: CoroutineScope) {

    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contacts: StateFlow<List<ContactEntry>> = _contacts

    /** True once the list reflects the phone's contacts (false while off or still reading). */
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    @Volatile private var enabled = false
    private var loading: Job? = null
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload(debounce = true)
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Starts or stops reading contacts. Calling it again while on re-checks the permission. */
    fun setEnabled(on: Boolean) {
        enabled = on
        if (on && hasPermission()) {
            if (!observing) {
                runCatching {
                    context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
                    observing = true
                }
            }
            if (!_loaded.value) reload(debounce = false)
        } else {
            stopObserving()
            loading?.cancel()
            _contacts.value = emptyList()
            _loaded.value = false
        }
    }

    private fun reload(debounce: Boolean) {
        loading?.cancel()
        loading = scope.launch(Dispatchers.IO) {
            if (debounce) delay(1_000) // sync adapters change contacts in bursts
            if (!enabled || !hasPermission()) return@launch
            val result = try {
                query()
            } catch (e: RuntimeException) { // SecurityException if revoked mid-read, provider errors
                Log.w(TAG, "couldn't read contacts", e)
                emptyList()
            }
            // The query can't be interrupted: if search was switched off meanwhile, drop what it read.
            withContext(Dispatchers.Main) {
                if (isActive && enabled) {
                    _contacts.value = result
                    _loaded.value = true
                }
            }
        }
    }

    private fun query(): List<ContactEntry> {
        val resolver = context.contentResolver
        val numbers = HashMap<Long, String>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null,
            // Primary numbers first, so the first one seen per contact is the one to keep.
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, " +
                "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                numbers.putIfAbsent(id, number)
            }
        }

        // Nicknames ("Bobby", "Mom") live in the data table, one row each.
        val nicknames = HashMap<Long, MutableList<String>>()
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.CommonDataKinds.Nickname.NAME),
            "${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val nick = c.getString(1)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val list = nicknames.getOrPut(c.getLong(0)) { mutableListOf() }
                if (list.none { it.equals(nick, ignoreCase = true) }) list += nick
            }
        }

        val out = ArrayList<ContactEntry>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val key = c.getString(1) ?: continue
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                out += ContactEntry(id, key, name, numbers[id], nicknames[id].orEmpty().filterNot { it.equals(name, ignoreCase = true) })
            }
        }
        val collator = Collator.getInstance()
        return out.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun open(contact: ContactEntry): Boolean =
        start(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)))

    /** Opens the dialer with the number filled in; SysReadout never places calls itself. */
    fun dial(contact: ContactEntry): Boolean =
        contact.number?.let { start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null))) } ?: false

    fun message(contact: ContactEntry): Boolean =
        contact.number?.let { start(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", it, null))) } ?: false

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun stopObserving() {
        if (observing) runCatching { context.contentResolver.unregisterContentObserver(observer) }
        observing = false
    }

    fun close() {
        enabled = false
        stopObserving()
    }

    private companion object {
        const val TAG = "ContactRepository"
    }
}
