package com.bankai.anime

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

data class AnimeRecord(
    val id: String,
    val title: String,
    val posterPath: String? = null,
    val posterUrl: String? = null,
    val status: String = "Plan to watch",
    val weekly: Boolean = false,
    val seasons: List<SeasonRecord> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class SeasonRecord(
    val id: String,
    val name: String,
    val episodes: List<EpisodeRecord> = emptyList()
)

data class EpisodeRecord(
    val number: Int,
    val watched: Boolean = false,
    val videoPath: String? = null
)

object LibraryStore {
    private const val PREFS = "bankai_library_v1"
    private const val KEY_LIBRARY = "anime_records"
    private val gson = Gson()

    fun load(context: Context): List<AnimeRecord> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LIBRARY, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<AnimeRecord>>() {}.type
            gson.fromJson<List<AnimeRecord>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, records: List<AnimeRecord>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIBRARY, gson.toJson(records))
            .apply()
    }
}

fun copyUriToPrivateStorage(context: Context, uri: Uri, folderName: String): String? {
    return runCatching {
        val directory = File(context.filesDir, folderName).apply { mkdirs() }
        val mime = context.contentResolver.getType(uri)
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?: if (folderName == "videos") "mp4" else "jpg"
        val destination = File(directory, UUID.randomUUID().toString() + "." + extension)
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to read selected file")
        input.use { source -> destination.outputStream().use { output -> source.copyTo(output) } }
        destination.absolutePath
    }.getOrNull()
}

fun deletePrivateFile(path: String?) {
    if (!path.isNullOrBlank()) runCatching { File(path).delete() }
}
