package com.example.jetnews.ui

import android.Manifest
import android.content.ComponentName
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import java.util.UUID

// --- BACKGROUND NOTIFICATION & LOCKSCREEN SERVICE ---
class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

// --- DATA MODELS ---
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val artUri: Uri,
    val isFavorite: Boolean = false
)

data class Playlist(val id: String, val name: String, val songs: List<Song>)

// --- LOCAL MEDIASTORE SCANNER WITH ALBUM ART ---
class MusicRepository(private val context: Context) {
    fun getLocalSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val artBase = Uri.parse("content://media/external/audio/albumart")
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        context.contentResolver.query(
            uri, proj, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { c ->
            val idC = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durC = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val albumId = c.getLong(albumIdC)
                list.add(
                    Song(
                        id = id,
                        title = c.getString(titleC) ?: "Unknown",
                        artist = c.getString(artistC) ?: "Unknown Artist",
                        album = c.getString(albumC) ?: "Unknown Album",
                        duration = c.getLong(durC),
                        uri = ContentUris.withAppendedId(uri, id),
                        artUri = ContentUris.withAppendedId(artBase, albumId)
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

@Composable
fun AlbumArtwork(artUri: Uri, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        AsyncImage(
            model = artUri,
            contentDescription = "Album Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
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
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(ctx) {
        val token = SessionToken(ctx, ComponentName(ctx, MusicPlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(future) }
    }

    var songs by remember { mutableStateOf(repo.getLocalSongs()) }
    var playlists by remember { mutableStateOf(listOf<Playlist>()) }
    var tab by remember { mutableIntStateOf(0) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    // Sync playback state & seek bar
    LaunchedEffect(controller, isPlaying) {
        while (true) {
            controller?.let {
                isPlaying = it.isPlaying
                position = it.currentPosition.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    fun play(song: Song, queue: List<Song> = songs) {
        val ctrl = controller ?: return
        currentSong = song
        val items = queue.map { s ->
            MediaItem.Builder()
                .setMediaId(s.id.toString())
                .setUri(s.uri)
                .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(s.title)
                    .setArtist(s.artist)
                    .setAlbumTitle(s.album)
                    .setArtworkUri(s.artUri)
                    .build()
            )
                .build()
        }
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        ctrl.setMediaItems(items, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
        isPlaying = true
    }

    fun skipNext() {
        val ctrl = controller ?: return
        if (ctrl.hasNextMediaItem()) {
            ctrl.seekToNextMediaItem()
            val idx = ctrl.currentMediaItemIndex
            if (idx in songs.indices) currentSong = songs[idx]
        }
    }
    fun skipPrev() {
        val ctrl = controller ?: return
        if (ctrl.hasPreviousMediaItem()) {
            ctrl.seekToPreviousMediaItem()
            val idx = ctrl.currentMediaItemIndex
            if (idx in songs.indices) currentSong = songs[idx]
        }
    }
    fun toggleFav(id: Long) {
        songs = songs.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it }
        if (currentSong?.id == id) currentSong = currentSong?.copy(isFavorite = !(currentSong?.isFavorite ?: false))
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
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    currentSong?.let { s ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { showFullPlayer = true },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                AlbumArtwork(s.artUri, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                                    Text(s.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = {
                                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                                        isPlaying = !isPlaying
                                }) {
                                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                                }
                                IconButton(onClick = { skipNext() }) {
                                    Icon(Icons.Default.SkipNext, "Next")
                                }
                            }
                        }
                    }
                }
                NavigationBar {
                    val labels = listOf("Songs", "Albums", "Favorites", "Playlists")
                    val icons = listOf(Icons.Default.MusicNote, Icons.Default.Album, Icons.Default.Favorite, Icons.AutoMirrored.Filled.QueueMusic)
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
                0 -> SongList(songs, { play(it, songs) }, ::toggleFav) { songToAdd = it }
                1 -> AlbumGrid(songs, ::play, ::toggleFav) { songToAdd = it }
                2 -> {
                    val favs = songs.filter { it.isFavorite }
                    SongList(favs, { play(it, favs) }, ::toggleFav) { songToAdd = it }
                }
                3 -> PlaylistList(playlists, ::play, ::toggleFav) { showNewPlaylist = true }
            }
        }
    }
    // FULL-SCREEN NOW PLAYING SHEET
    if (showFullPlayer && currentSong != null) {
        val song = currentSong!!
        ModalBottomSheet(
            onDismissRequest = { showFullPlayer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                AlbumArtwork(
                    artUri = song.artUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(32.dp))
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    IconButton(onClick = { toggleFav(song.id) }) {
                        Icon(
                            imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Column(Modifier.fillMaxWidth()) {
                    val total = song.duration.coerceAtLeast(1L)
                    Slider(
                        value = (position.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            val newPos = (fraction * total).toLong()
                            position = newPos
                            controller?.seekTo(newPos)
                        }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
                        Text(formatTime(total), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { skipPrev() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
                    }
                    FilledIconButton(
                        onClick = {
                            controller?.let { if (it.isPlaying) it.pause() else it.play() }
                            isPlaying = !isPlaying
                        },
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape
                    ) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { skipNext() }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
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
fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
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
                    AlbumArtwork(s.artUri, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)))
                },
                trailingContent = {
                    Row {
                        if (onAdd != null) IconButton(onClick = { onAdd(s) }) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, "Add") }
                        IconButton(onClick = { onFav(s.id) }) {
                            Icon(
                                if (s.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                "Fav",
                                tint = if (s.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    }
}
@Composable
fun AlbumGrid(songs: List<Song>, onPlay: (Song, List<Song>) -> Unit, onFav: (Long) -> Unit, onAdd: (Song) -> Unit) {
    val albums = songs.groupBy { it.album }
    var selected by remember { mutableStateOf<String?>(null) }
    if (selected != null) {
        val albumSongs = albums[selected] ?: emptyList()
        Column {
            TextButton(onClick = { selected = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("Back to Albums")
            }
            SongList(albumSongs, { onPlay(it, albumSongs) }, onFav, onAdd)
        }
    } else {
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums.keys.toList()) { name ->
                val firstSong = albums[name]?.firstOrNull()
                Card(Modifier.clickable { selected = name }, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        if (firstSong != null) {
                            AlbumArtwork(firstSong.artUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${albums[name]?.size ?: 0} songs", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
@Composable
fun PlaylistList(playlists: List<Playlist>, onPlay: (Song, List<Song>) -> Unit, onFav: (Long) -> Unit, onCreate: () -> Unit) {
    var selected by remember { mutableStateOf<Playlist?>(null) }
    if (selected != null) {
        val cur = playlists.find { it.id == selected?.id }
        val plSongs = cur?.songs ?: emptyList()
        Column {
            TextButton(onClick = { selected = null }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("Back to Playlists")
            }
            SongList(plSongs, { onPlay(it, plSongs) }, onFav)
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
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }
                )
            }
        }
    }
}
