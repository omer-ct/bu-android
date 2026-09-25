package com.codefixr.beummati.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    /** `HifzSnapshot` JSON as [HifzStore] writes it — progress, reviews, goal and streak. */
    val hifzSnapshot: String = "",
    val bookmarks: List<Bookmark> = emptyList(),
    val notes: List<Note> = emptyList()
)

data class BackupSummary(
    val bookmarks: Int,
    val notes: Int,
    val hifzRestored: Boolean
)

/** The reader's own data — hifz progress, bookmarks and notes — as one JSON file. */
object BackupStore {
    private const val TAG = "BackupStore"
    private val FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** Writes today's backup to `cacheDir/backups` and returns the file. */
    fun export(context: Context): File {
        val app = context.applicationContext
        HifzStore.init(app)
        SavedStore.init(app)
        val payload = BackupPayload(
            hifzSnapshot = runCatching { HifzStore.exportSnapshotJson() }.getOrDefault(""),
            bookmarks = SavedStore.bookmarks.value,
            notes = SavedStore.notes.value
        )
        val dir = File(app.cacheDir, "backups").apply { mkdirs() }
        val file = File(dir, "beummati-backup-${LocalDate.now().format(FILE_DATE)}.json")
        file.writeText(Catalogs.json.encodeToString(payload))
        return file
    }

    /** Exports, then offers the file to any app that takes a document. */
    fun exportAndShare(context: Context): File? {
        val file = runCatching { export(context) }
            .onFailure { Log.w(TAG, "Backup export failed", it) }
            .getOrNull() ?: return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.shares", file)
        }.onFailure { Log.w(TAG, "Couldn’t expose ${file.name}", it) }.getOrNull() ?: return null
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val launched = runCatching { context.startActivity(Intent.createChooser(send, "Save backup")) }
            .onFailure { Log.w(TAG, "No app took the backup", it) }
            .isSuccess
        return file.takeIf { launched }
    }

    /** Replaces the current bookmarks, notes and hifz progress. Null when [json] isn't a backup. */
    fun import(context: Context, json: String): BackupSummary? {
        val app = context.applicationContext
        HifzStore.init(app)
        SavedStore.init(app)
        val payload = runCatching { Catalogs.json.decodeFromString<BackupPayload>(json) }
            .onFailure { Log.w(TAG, "Unreadable backup", it) }
            .getOrNull() ?: return null
        val hifzRestored = payload.hifzSnapshot.isNotBlank() &&
            runCatching { HifzStore.importSnapshotJson(payload.hifzSnapshot) }
                .onFailure { Log.w(TAG, "Hifz snapshot in the backup was unreadable", it) }
                .isSuccess
        SavedStore.replaceAll(payload.bookmarks, payload.notes)
        return BackupSummary(
            bookmarks = payload.bookmarks.size,
            notes = payload.notes.size,
            hifzRestored = hifzRestored
        )
    }

    /** Reads a backup the reader picked with `ACTION_GET_CONTENT`. */
    fun readText(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.onFailure { Log.w(TAG, "Couldn’t read $uri", it) }.getOrNull()
}
