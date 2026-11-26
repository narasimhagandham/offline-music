/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://apache.org
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetnews.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.UUID

// --- DATA MODELS ---
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val isFavorite: Boolean = false
)

data class Playlist(
    val id: String,
    val name: String,
    val songs: List<Song>
)

// --- LOCAL MEDIASTORE SCANNER ---
class MusicRepository(private val context: Context) {
    fun getLocalSongs(): List<Song> {
        val songList = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(
            collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(collection, id)
                songList.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown Title",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        duration = cursor.getLong(durationCol),
                        uri = contentUri
                    )
                )
            }
        }
        return songList
    }
}

// --- MATERIAL YOU DYNAMIC THEME ---
@Composable
fun OfflineMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography(), content = content)
}

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OfflineMusicTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicAppScreen()
                }
            }
        }
    }
}

// --- FLUID UI & SCREENS ---
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicAppScreen() {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permissionState = rememberPermissionState(permission)

    if (!permissionState.status.isGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Audio Permission Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Allow access to scan and play offline music stored on your phone.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { permissionState.launchPermissionRequest() }) {
                Text("Grant Permission")
            }
        }
        return
    }

    val repository = remember { MusicRepository(context) }
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    var songs by remember { mutableStateOf(repository.getLocalSongs()) }
    var playlists by remember { mutableStateOf(listOf<Playlist>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

    fun playSong(song: Song) {
        currentSong = song
        exoPlayer.setMediaItem(MediaItem.fromUri(song.uri))
        exoPlayer.prepare()
        exoPlayer.play()
        isPlaying = true
    }

    fun toggleFavorite(songId: Long) {
        songs = songs.map { if (it.id == songId) it.copy(isFavorite = !it.isFavorite) else it }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Offline Music", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTab == 3) {
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Create Playlist")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    currentSong?.let { song ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
}
IconButton(onClick = {
if (isPlaying) exoPlayer.pause() else exoPlayer.play()
isPlaying = !isPlaying
}) {
Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Play/Pause")
}
}
}
}
}
NavigationBar {
val items = listOf("Songs", "Albums", "Favorites", "Playlists")
val icons = listOf(Icons.Default.MusicNote, Icons.Default.Album, Icons.Default.Favorite, Icons.Default.QueueMusic)
items.forEachIndexed { index, label ->
NavigationBarItem(
selected = selectedTab == index,
onClick = { selectedTab = index },
icon = { Icon(icons[index], contentDescription = label) },
label = { Text(label) }
)
}
}
}
}
) { padding ->
Crossfade(targetState = selectedTab, modifier = Modifier.padding(padding), label = "TabTransition") { tab ->
when (tab) {
0 -> SongList(songs, onPlay = ::playSong, onToggleFav = ::toggleFavorite, onAddToPlaylist = { songToAddToPlaylist = it })
1 -> AlbumGrid(songs, onPlay = ::playSong, onToggleFav = ::toggleFavorite, onAddToPlaylist = { songToAddToPlaylist = it })
2 -> SongList(songs.filter { it.isFavorite }, onPlay = ::playSong, onToggleFav = ::toggleFavorite, onAddToPlaylist = { songToAddToPlaylist = it })
3 -> PlaylistList(playlists, onPlay = ::playSong, onToggleFav = ::toggleFavorite, onCreateClick = { showCreatePlaylistDialog = true })
}
}
}
// Create Playlist Dialog
if (showCreatePlaylistDialog) {
var playlistName by remember { mutableStateOf("") }
AlertDialog(
onDismissRequest = { showCreatePlaylistDialog = false },
title = { Text("New Playlist") },
text = {
OutlinedTextField(
value = playlistName,
onValueChange = { playlistName = it },
label = { Text("Playlist Name") },
singleLine = true
)
},
confirmButton = {
TextButton(onClick = {
if (playlistName.isNotBlank()) {
playlists = playlists + Playlist(UUID.randomUUID().toString(), playlistName, emptyList())
showCreatePlaylistDialog = false
}
}) { Text("Create") }
},
dismissButton = {
TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
}
)
}
// Add Song to Playlist Dialog
songToAddToPlaylist?.let { song ->
AlertDialog(
onDismissRequest = { songToAddToPlaylist = null },
title = { Text("Add to Playlist") },
text = {
if (playlists.isEmpty()) {
Text("No playlists created yet. Go to the Playlists tab and tap + to create one.")
} else {
LazyColumn {
items(playlists) { playlist ->
ListItem(
modifier = Modifier.clickable {
playlists = playlists.map {
if (it.id == playlist.id && !it.songs.contains(song)) {
it.copy(songs = it.songs + song)
} else it
}
songToAddToPlaylist = null
},
headlineContent = { Text(playlist.name) }
)
}
}
}
},
confirmButton = {
TextButton(onClick = { songToAddToPlaylist = null }) { Text("Close") }
}
)
}
}
@Composable
fun SongList(
songs: List,
onPlay: (Song) -> Unit,
onToggleFav: (Long) -> Unit,
onAddToPlaylist: ((Song) -> Unit)? = null
) {
if (songs.isEmpty()) {
Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
Text("No songs found", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
return
}
LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
items(songs) { song ->
ListItem(
modifier = Modifier.clickable { onPlay(song) },
headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
supportingContent = { Text(song.artist, maxLines = 1) },
leadingContent = {
Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Audiotrack, contentDescription = null) }
}
},
trailingContent = {
Row {
if (onAddToPlaylist != null) {
IconButton(onClick = { onAddToPlaylist(song) }) {
Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to Playlist")
}
}
IconButton(onClick = { onToggleFav(song.id) }) {
Icon(
imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
contentDescription = "Favorite",
tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
)
}
}
}
)
}
}
}
@Composable
fun AlbumGrid(
songs: List,
onPlay: (Song) -> Unit,
onToggleFav: (Long) -> Unit,
onAddToPlaylist: (Song) -> Unit
) {
val albums = songs.groupBy { it.album }
var selectedAlbum by remember { mutableStateOf<String?>(null) }
if (selectedAlbum != null) {
Column {
TextButton(onClick = { selectedAlbum = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
Icon(Icons.Default.ArrowBack, contentDescription = null)
Spacer(modifier = Modifier.width(8.dp))
Text("Back to Albums")
}
SongList(albums[selectedAlbum] ?: emptyList(), onPlay, onToggleFav, onAddToPlaylist)
}
} else {
LazyVerticalGrid(
columns = GridCells.Fixed(2),
contentPadding = PaddingValues(16.dp),
horizontalArrangement = Arrangement.spacedBy(12.dp),
verticalArrangement = Arrangement.spacedBy(12.dp)
) {
items(albums.keys.toList()) { albumName ->
Card(
modifier = Modifier.clickable { selectedAlbum = albumName },
shape = RoundedCornerShape(20.dp),
colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
) {
Column(modifier = Modifier.padding(16.dp)) {
Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
Spacer(modifier = Modifier.height(8.dp))
Text(albumName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
Text("${albums[albumName]?.size ?: 0} songs", style = MaterialTheme.typography.bodySmall)
}
}
}
}
}
}
@Composable
fun PlaylistList(
playlists: List,
onPlay: (Song) -> Unit,
onToggleFav: (Long) -> Unit,
onCreateClick: () -> Unit
) {
var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
if (selectedPlaylist != null) {
val current = playlists.find { it.id == selectedPlaylist?.id }
Column {
TextButton(onClick = { selectedPlaylist = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
Icon(Icons.Default.ArrowBack, contentDescription = null)
Spacer(modifier = Modifier.width(8.dp))
Text("Back to Playlists")
}
SongList(current?.songs ?: emptyList(), onPlay, onToggleFav)
}
} else if (playlists.isEmpty()) {
Column(
modifier = Modifier.fillMaxSize(),
verticalArrangement = Arrangement.Center,
horizontalAlignment = Alignment.CenterHorizontally
) {
Text("No playlists yet")
Spacer(modifier = Modifier.height(8.dp))
Button(onClick = onCreateClick) { Text("Create Playlist") }
}
} else {
LazyColumn(contentPadding = PaddingValues(16.dp)) {
items(playlists) { playlist ->
ListItem(
modifier = Modifier.clickable { selectedPlaylist = playlist },
headlineContent = { Text(playlist.name, fontWeight = FontWeight.Medium) },
supportingContent = { Text("${playlist.songs.size} tracks") },
leadingContent = { Icon(Icons.Default.QueueMusic, contentDescription = null) }
)
}
}
}
}
