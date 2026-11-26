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

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val isFavorite: Boolean = false
)

data class Playlist(val id: String, val name: String, val songs: List<Song>)

class MusicRepository(private val context: Context) {
    fun getLocalSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )
        context.contentResolver.query(
            uri, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                list.add(
                    Song(
                        id,
                        c.getString(titleC) ?: "Unknown",
                        c.getString(artistC) ?: "Unknown Artist",
                        c.getString(albumC) ?: "Unknown Album",
                        ContentUris.withAppendedId(uri, id)
                    )
                )
            }
        }
        return list
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val dark = isSystemInDarkTheme()
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                if (dark) darkColorScheme() else lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MusicAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MusicAppScreen() {
    val ctx = LocalContext.current
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permState = rememberPermissionState(perm)

    if (!permState.status.isGranted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Audio Permission Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { permState.launchPermissionRequest() }) { Text("Grant Permission") }
        }
        return
    }

    val repo = remember { MusicRepository(ctx) }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    var songs by remember { mutableStateOf(repo.getLocalSongs()) }
    var playlists by remember { mutableStateOf(listOf<Playlist>()) }
    var tab by remember { mutableIntStateOf(0) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    fun play(song: Song) {
        currentSong = song
        player.setMediaItem(MediaItem.fromUri(song.uri))
        player.prepare()
        player.play()
        isPlaying = true
    }

    fun toggleFav(id: Long) {
        songs = songs.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Offline Music", fontWeight = FontWeight.Bold) },
                actions = {
                    if (tab == 3) {
                        IconButton(onClick = { showNewPlaylist = true }) {
                            Icon(Icons.Default.Add, "New Playlist")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column {
                AnimatedVisibility(visible = currentSong != null) {
                    currentSong?.let { s ->
                        Card(
                            Modifier.fillMaxWidth().padding(12.dp, 6.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MusicNote, null, Modifier.size(32.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                    if (isPlaying) player.pause() else player.play()
                                    isPlaying = !isPlaying
                                }) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                                }
                            }
                        }
                    }
                }
                NavigationBar {
                    val labels = listOf("Songs", "Albums", "Favorites", "Playlists")
                    val icons = listOf(Icons.Default.MusicNote, Icons.Default.Album, Icons.Default.Favorite, Icons.Default.QueueMusic)
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icons[i], label) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Crossfade(targetState = tab, modifier = Modifier.padding(pad), label = "Tab") { t ->
            when (t) {
                0 -> SongList(songs, ::play, ::toggleFav) { songToAdd = it }
                1 -> AlbumGrid(songs, ::play, ::toggleFav) { songToAdd = it }
                2 -> SongList(songs.filter { it.isFavorite }, ::play, ::toggleFav) { songToAdd = it }
                3 -> PlaylistList(playlists, ::play, ::toggleFav) { showNewPlaylist = true }
            }
        }
    }

    if (showNewPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
onDismissRequest = { showNewPlaylist = false },
title = { Text("New Playlist") },
text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
confirmButton = {
TextButton(onClick = {
if (name.isNotBlank()) {
playlists = playlists + Playlist(UUID.randomUUID().toString(), name, emptyList())
showNewPlaylist = false
}
}) { Text("Create") }
},
dismissButton = { TextButton(onClick = { showNewPlaylist = false }) { Text("Cancel") } }
)
}
songToAdd?.let { song ->
AlertDialog(
onDismissRequest = { songToAdd = null },
title = { Text("Add to Playlist") },
text = {
if (playlists.isEmpty()) Text("Create a playlist first in the Playlists tab.")
else LazyColumn {
items(playlists) { pl ->
ListItem(
modifier = Modifier.clickable {
playlists = playlists.map { if (it.id == pl.id && !it.songs.contains(song)) it.copy(songs = it.songs + song) else it }
songToAdd = null
},
headlineContent = { Text(pl.name) }
)
}
}
},
confirmButton = { TextButton(onClick = { songToAdd = null }) { Text("Close") } }
)
}
}
@Composable
fun SongList(songs: List<Song>, onPlay: (Song) -> Unit, onFav: (Long) -> Unit, onAdd: ((Song) -> Unit)? = null) {
if (songs.isEmpty()) {
Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No songs found") }
return
}
LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
items(songs) { s ->
ListItem(
modifier = Modifier.clickable { onPlay(s) },
headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
supportingContent = { Text(s.artist, maxLines = 1) },
leadingContent = {
Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(48.dp)) {
Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Audiotrack, null) }
}
},
trailingContent = {
Row {
if (onAdd != null) IconButton(onClick = { onAdd(s) }) { Icon(Icons.Default.PlaylistAdd, "Add") }
IconButton(onClick = { onFav(s.id) }) {
Icon(if (s.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Fav",
tint = if (s.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}
}
}
)
}
}
}
@Composable
fun AlbumGrid(songs: List<Song>, onPlay: (Song) -> Unit, onFav: (Long) -> Unit, onAdd: (Song) -> Unit) {
val albums = songs.groupBy { it.album }
var selected by remember { mutableStateOf<String?>(null) }
if (selected != null) {
Column {
TextButton(onClick = { selected = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("Back to Albums")
}
SongList(albums[selected] ?: emptyList(), onPlay, onFav, onAdd)
}
} else {
LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
items(albums.keys.toList()) { name ->
Card(Modifier.clickable { selected = name }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
Column(Modifier.padding(16.dp)) {
Icon(Icons.Default.Album, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
Spacer(Modifier.height(8.dp))
Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
Text("${albums[name]?.size ?: 0} songs", style = MaterialTheme.typography.bodySmall)
}
}
}
}
}
}
@Composable
fun PlaylistList(playlists: List<Playlist>, onPlay: (Song) -> Unit, onFav: (Long) -> Unit, onCreate: () -> Unit) {
var selected by remember { mutableStateOf<Playlist?>(null) }
if (selected != null) {
val cur = playlists.find { it.id == selected?.id }
Column {
TextButton(onClick = { selected = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
Icon(Icons.Default.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("Back to Playlists")
}
SongList(cur?.songs ?: emptyList(), onPlay, onFav)
}
} else if (playlists.isEmpty()) {
Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
Text("No playlists yet"); Spacer(Modifier.height(8.dp)); Button(onClick = onCreate) { Text("Create Playlist") }
}
} else {
LazyColumn(contentPadding = PaddingValues(16.dp)) {
items(playlists) { pl ->
ListItem(
modifier = Modifier.clickable { selected = pl },
headlineContent = { Text(pl.name, fontWeight = FontWeight.Medium) },
supportingContent = { Text("${pl.songs.size} tracks") },
leadingContent = { Icon(Icons.Default.QueueMusic, null) }
)
}
}
}
}
