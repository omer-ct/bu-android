package com.codefixr.beummati.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.UUID

/** Bookmarks + notes persisted as JSON in SharedPreferences. */
object SavedStore {
    private const val PREFS = "beummati.saved"
    private const val KEY_BOOKMARKS = "bookmarks.v1"
    private const val KEY_NOTES = "notes.v1"

    private lateinit var prefs: SharedPreferences

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _bookmarks.value = decode<List<Bookmark>>(KEY_BOOKMARKS).orEmpty()
        _notes.value = decode<List<Note>>(KEY_NOTES).orEmpty()
    }

    // region Bookmarks

    fun isBookmarked(id: String): Boolean = _bookmarks.value.any { it.id == id }

    fun toggleBookmark(bookmark: Bookmark) {
        if (isBookmarked(bookmark.id)) removeBookmark(bookmark.id) else addBookmark(bookmark)
    }

    fun addBookmark(bookmark: Bookmark) {
        _bookmarks.value = listOf(bookmark) + _bookmarks.value.filterNot { it.id == bookmark.id }
        persistBookmarks()
    }

    fun removeBookmark(id: String) {
        _bookmarks.value = _bookmarks.value.filterNot { it.id == id }
        persistBookmarks()
    }

    // endregion

    // region Notes

    fun addNote(title: String, body: String, ref: String = "", route: String? = null): Note {
        val now = System.currentTimeMillis()
        val note = Note(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { "Note" },
            body = body,
            ref = ref,
            route = route,
            createdAt = now,
            updatedAt = now
        )
        _notes.value = listOf(note) + _notes.value
        persistNotes()
        return note
    }

    fun updateNote(id: String, title: String, body: String) {
        _notes.value = _notes.value.map {
            if (it.id == id) it.copy(title = title.ifBlank { "Note" }, body = body, updatedAt = System.currentTimeMillis()) else it
        }
        persistNotes()
    }

    fun deleteNote(id: String) {
        _notes.value = _notes.value.filterNot { it.id == id }
        persistNotes()
    }

    // endregion

    private fun persistBookmarks() {
        prefs.edit().putString(KEY_BOOKMARKS, Catalogs.json.encodeToString(_bookmarks.value)).apply()
    }

    private fun persistNotes() {
        prefs.edit().putString(KEY_NOTES, Catalogs.json.encodeToString(_notes.value)).apply()
    }

    private inline fun <reified T> decode(key: String): T? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching { Catalogs.json.decodeFromString<T>(raw) }.getOrNull()
    }
}
