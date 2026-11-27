package com.example.jetnews.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil.compose.AsyncImage

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
fun ArtThumb(art: Uri?, sizeDp: Int, round: Boolean = false) {
    val shape = if (round) CircleShape else RoundedCornerShape(8.dp)
    Box(
        Modifier.size(sizeDp.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        AsyncImage(
            model = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}

@Composable
private fun SongRow(
    song: Song,
    current: Boolean,
    isPlaying: Boolean,
    actions: SongActions,
    onClick: () -> Unit,
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = if (current) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = { ArtThumb(song.artUri, 48) },
        headlineContent = {
            Text(song.title, maxLines = 1, fontWeight = if (current) FontWeight.Bold else null)
        },
        supportingContent = { Text("${song.artist} • ${formatTime(song.duration)}", maxLines = 1) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current && isPlaying) Icon(Icons.Default.Equalizer, "Playing", tint = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { actions.onFavorite(song) }) {
                    Icon(
                        if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favorite",
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            onClick = { menu = false; actions.onPlayNext(song) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            onClick = { menu = false; actions.onAddToQueue(song) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            onClick = { menu = false; actions.onAddToPlaylist(song) }
                        )
                        extraMenu?.invoke { menu = false }
                    }
                }
            }
        }
    )
}

@Composable
fun SongList(
    songs: List<Song>,
    currentId: Long?,
    isPlaying: Boolean,
    actions: SongActions,
    onPlay: (Song) -> Unit,
    onShuffle: () -> Unit,
    emptyText: String,
    header: (@Composable () -> Unit)? = null,
    extraMenu: ((Song) -> (@Composable (dismiss: () -> Unit) -> Unit))? = null
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (header != null) {
            item { Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { header() } }
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    emptyText,
                    Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                OutlinedButton(
                    onClick = onShuffle,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Shuffle, null)
                    Text("  Shuffle ${songs.size} songs")
                }
            }
            items(songs, key = { it.id }) { song ->
                SongRow(
                    song = song,
                    current = song.id == currentId,
                    isPlaying = isPlaying,
                    actions = actions,
                    onClick = { onPlay(song) },
                    extraMenu = extraMenu?.invoke(song)
                )
            }
        }
    }
}

@Composable
fun GroupGrid(
    songs: List<Song>,
    keyOf: (Song) -> String,
    round: Boolean,
    currentId: Long?,
    isPlaying: Boolean,
    actions: SongActions,
    onPlay: (Song, List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit
) {
    val groups = remember(songs) { songs.groupBy(keyOf).toList().sortedBy { it.first.lowercase() } }
    var selected by remember { mutableStateOf<String?>(null) }
    val selectedSongs = selected?.let { key -> groups.firstOrNull { it.first == key }?.second }

    if (selectedSongs == null) {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(groups, key = { it.first }) { (name, list) ->
                Column(
                    Modifier.clickable { selected = name },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(if (round) CircleShape else RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        AsyncImage(
                            model = list.first().artUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name, maxLines = 1, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(
                        "${list.size} songs",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        BackHandler { selected = null }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(selected.orEmpty(), Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.weight(1f)) {
                SongList(
                    songs = selectedSongs,
                    currentId = currentId,
                    isPlaying = isPlaying,
                    actions = actions,
                    onPlay = { onPlay(it, selectedSongs) },
                    onShuffle = { onShuffle(selectedSongs) },
                    emptyText = "No songs"
                )
            }
        }
    }
}

@Composable
fun PlaylistList(
    playlists: List<Playlist>,
    songsById: Map<Long, Song>,
    currentId: Long?,
    isPlaying: Boolean,
    actions: SongActions,
    onPlay: (Song, List<Song>) -> Unit,
    onShuffle: (List<Song>) -> Unit,
    onCreate: () -> Unit,
    onDelete: (String) -> Unit,
    onRemoveSong: (String, Song) -> Unit
) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val playlist = playlists.firstOrNull { it.id == selectedId }

    if (playlist == null) {
        if (playlists.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onCreate) {
                    Icon(Icons.Default.Add, null)
                    Text("  New playlist")
                }
            }
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(playlists, key = { it.id }) { pl ->
                ListItem(
                    modifier = Modifier.clickable { selectedId = pl.id },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    headlineContent = { Text(pl.name, maxLines = 1) },
                    supportingContent = { Text("${pl.songIds.count { it in songsById }} songs") },
                    trailingContent = {
                        IconButton(onClick = { onDelete(pl.id) }) { Icon(Icons.Default.Delete, "Delete playlist") }
                    }
                )
            }
        }
    } else {
        BackHandler { selectedId = null }
        val list = playlist.songIds.mapNotNull { songsById[it] }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedId = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(playlist.name, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
            }
            Box(Modifier.weight(1f)) {
                SongList(
                    songs = list,
                    currentId = currentId,
                    isPlaying = isPlaying,
                    actions = actions,
                    onPlay = { onPlay(it, list) },
                    onShuffle = { onShuffle(list) },
                    emptyText = "This playlist is empty",
                    extraMenu = { song ->
                        { dismiss ->
                            DropdownMenuItem(
                                text = { Text("Remove from playlist") },
                                onClick = { dismiss(); onRemoveSong(playlist.id, song) }
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).clickable(onClick = onOpen)
    ) {
        Column {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtThumb(song.artUri, 44)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(song.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                    Text(
                        song.artist,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, "Previous") }
                IconButton(onClick = onToggle) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play")
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    song: Song,
    settings: AppSettings,
    isPlaying: Boolean,
    position: Long,
    shuffle: Boolean,
    repeatMode: Int,
    speed: Float,
    sleepActive: Boolean,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavorite: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: (Int) -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit
) {
    var sleepMenu by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = song.duration.coerceAtLeast(1L)
    val shownPos = dragging ?: position.toFloat()
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier.fillMaxSize().navigationBarsPadding()
                .padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.weight(1f).aspectRatio(1f)
                    .clip(RoundedCornerShape(if (settings.lowPower) 8.dp else 40.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(72.dp), tint = muted)
                AsyncImage(
                    model = song.artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(song.artist, maxLines = 1, color = muted)
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        "Favorite",
                        tint = if (song.isFavorite) accent else muted
                    )
                }
            }
            Slider(
                value = shownPos.coerceIn(0f, duration.toFloat()),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSeek(it.toLong()) }
                    dragging = null
                },
                valueRange = 0f..duration.toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(shownPos.toLong()), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) accent else muted)
                }
                IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                FilledIconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(width = 96.dp, height = 72.dp),
                    shape = if (isPlaying) RoundedCornerShape(24.dp) else CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "Pause" else "Play",
                        Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (repeatMode == Player.REPEAT_MODE_OFF) muted else accent
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextSpeed(speed, onSpeed)
                Box {
                    IconButton(onClick = { sleepMenu = true }) {
                        Icon(Icons.Default.Bedtime, "Sleep timer", tint = if (sleepActive) accent else muted)
                    }
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 45 to "45 minutes", 60 to "1 hour")
                            .forEach { (min, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = { sleepMenu = false; onSleep(min) }
                                )
                            }
                    }
                }
                IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue", tint = muted) }
                IconButton(onClick = onEqualizer) { Icon(Icons.Default.Equalizer, "Equalizer", tint = muted) }
            }
        }
    }
}

@Composable
private fun TextSpeed(speed: Float, onClick: () -> Unit) {
    Row(Modifier.clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Speed, "Playback speed", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(" ${"%.2f".format(speed).trimEnd('0').trimEnd('.')}x", style = MaterialTheme.typography.labelLarge)
    }
}
