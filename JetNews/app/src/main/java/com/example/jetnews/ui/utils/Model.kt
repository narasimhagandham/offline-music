package com.example.jetnews.ui

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

// --- DATA MODELS ---
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val artUri: Uri,
    val folder: String = "",
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false
)

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

data class QueueItem(val mediaId: String, val title: String, val artist: String, val art: Uri?)

data class SavedQueue(val ids: List<Long>, val index: Int, val position: Long)

enum class SortOrder(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Longest first"),
    RECENT("Recently added")
}

enum class SongFilter(val label: String) {
    ALL("All"),
    RECENT("Recently played"),
    MOST("Most played")
}

class SongActions(
    val onFavorite: (Song) -> Unit,
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onAddToPlaylist: (Song) -> Unit
)

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri)
            .build()
    )
    .build()

// --- LOCAL MEDIASTORE SCANNER WITH ALBUM ART ---
class MusicRepository(private val context: Context) {
    fun getLocalSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val artBase = Uri.parse("content://media/external/audio/albumart")
        val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            folderColumn
        )
        context.contentResolver.query(
            uri, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val folderC = c.getColumnIndexOrThrow(folderColumn)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val albumId = c.getLong(albumIdC)
                val rawFolder = c.getString(folderC).orEmpty()
                val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    rawFolder.trimEnd('/')
                } else {
                    rawFolder.substringBeforeLast('/', "")
                }
                list.add(
                    Song(
                        id = id,
                        title = c.getString(titleC) ?: "Unknown",
                        artist = c.getString(artistC)?.takeUnless { it == "<unknown>" } ?: "Unknown Artist",
                        album = c.getString(albumC) ?: "Unknown Album",
                        duration = c.getLong(durC),
                        uri = ContentUris.withAppendedId(uri, id),
                        artUri = ContentUris.withAppendedId(artBase, albumId),
                        folder = folder.ifEmpty { "Unknown folder" },
                        dateAdded = c.getLong(addedC)
                    )
                )
            }
        }
        return list
    }
}

// --- PERSISTENCE: favorites, playlists, play history, last queue ---
class MusicStore(context: Context) {
    private val prefs = context.getSharedPreferences("music_prefs", Context.MODE_PRIVATE)

    fun loadFavorites(): Set<Long> =
        (prefs.getStringSet("favorites", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    fun saveFavorites(ids: Set<Long>) {
        prefs.edit().putStringSet("favorites", ids.map { it.toString() }.toSet()).apply()
    }

    fun loadPlaylists(): List<Playlist> {
        val raw = prefs.getString("playlists", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("songs")
                Playlist(o.getString("id"), o.getString("name"), (0 until ids.length()).map { ids.getLong(it) })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePlaylists(list: List<Playlist>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("songs", JSONArray(p.songIds)))
        }
        prefs.edit().putString("playlists", arr.toString()).apply()
    }

    fun loadPlayCounts(): Map<Long, Int> {
        val raw = prefs.getString("play_counts", null) ?: return emptyMap()
        return try {
            val o = JSONObject(raw)
            o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to o.getInt(k) } }.toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun savePlayCounts(counts: Map<Long, Int>) {
        val o = JSONObject()
        counts.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs.edit().putString("play_counts", o.toString()).apply()
    }

    fun loadRecents(): List<Long> =
        prefs.getString("recents", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun saveRecents(ids: List<Long>) {
        prefs.edit().putString("recents", ids.joinToString(",")).apply()
    }

    fun saveQueue(ids: List<Long>, index: Int, position: Long) {
        prefs.edit()
            .putString("queue_ids", ids.joinToString(","))
            .putInt("queue_index", index)
            .putLong("queue_position", position)
            .apply()
    }

    fun loadQueue(): SavedQueue? {
        val ids = prefs.getString("queue_ids", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return null
        return SavedQueue(ids, prefs.getInt("queue_index", 0), prefs.getLong("queue_position", 0L))
    }
}
