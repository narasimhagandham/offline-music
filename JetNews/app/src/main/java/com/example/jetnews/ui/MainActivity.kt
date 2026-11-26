package com.example.jetnews.ui

import android.Manifest
import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// --- BACKGROUND NOTIFICATION & LOCKSCREEN SERVICE ---
class MusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
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
    val dateAdded: Long = 0L,
    val isFavorite: Boolean = false
)

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

enum class SortOrder(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DURATION("Longest first"),
    RECENT("Recently added")
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
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
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
            while (c.moveToNext()) {
                val id = c.getLong(idC)
                val albumId = c.getLong(albumIdC)
                list.add(
                    Song(
                        id = id,
                        title = c.getString(titleC) ?: "Unknown",
                        artist = c.getString(artistC)?.takeUnless { it == "<unknown>" } ?: "Unknown Artist",
                        album = c.getString(albumC) ?: "Unknown Album",
                        duration = c.getLong(durC),
                        uri = ContentUris.withAppendedId(uri, id),
                        artUri = ContentUris.withAppendedId(artBase, albumId),
                        dateAdded = c.getLong(addedC)
                    )
                )
            }
        }
        return list
    }
}

// --- PERSISTENCE FOR FAVORITES & PLAYLISTS ---
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
            } else if (dark) {
                darkColorScheme(
                    primary = Color(0xFFB8A7FF),
                    primaryContainer = Color(0xFF3F3399),
                    secondaryContainer = Color(0xFF2E3A66),
                    background = Color(0xFF111118),
                    surface = Color(0xFF111118)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF5B45E0),
                    primaryContainer = Color(0xFFE4DFFF),
                    secondaryContainer = Color(0xFFDDE3FF),
                    background = Color(0xFFFBF8FF),
                    surface = Color(0xFFFBF8FF)
                )
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
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        AsyncImage(
            model = artUri,
            contentDescription = "Album Art",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MusicAppScreen() {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permState = rememberPermissionState(perm)

    LaunchedEffect(Unit) {
        if (!permState.status.isGranted) permState.launchPermissionRequest()
    }

    if (permState.status.isGranted) {
        MusicContent()
    } else {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.MusicNote, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Audio Permission Required", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Allow access to your audio files so the player can find the music on this device.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { permState.launchPermissionRequest() }) { Text("Grant Permission") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicContent() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { MusicStore(ctx) }
    val repo = remember { MusicRepository(ctx) }
    val snackbar = remember { SnackbarHostState() }

    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(ctx) {
        val token = SessionToken(ctx, ComponentName(ctx, MusicPlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({ controller = future.get() }, ContextCompat.getMainExecutor(ctx))
        onDispose { MediaController.releaseFuture(future) }
    }

    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        allSongs = withContext(Dispatchers.IO) { repo.getLocalSongs() }
        loading = false
    }

    var favorites by remember { mutableStateOf(store.loadFavorites()) }
    var playlists by remember { mutableStateOf(store.loadPlaylists()) }
    val songs = remember(allSongs, favorites) { allSongs.map { it.copy(isFavorite = it.id in favorites) } }
    val songsById = remember(songs) { songs.associateBy { it.id } }

    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(SortOrder.TITLE) }
    var sortMenu by remember { mutableStateOf(false) }

    var currentId by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var speed by remember { mutableFloatStateOf(1f) }
    var sleepEndAt by remember { mutableStateOf<Long?>(null) }

    var showFullPlayer by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    val currentSong = currentId?.let { songsById[it] }

    DisposableEffect(controller) {
        val c = controller
        if (c == null) {
            onDispose { }
        } else {
            currentId = c.currentMediaItem?.mediaId?.toLongOrNull()
            isPlaying = c.isPlaying
            shuffle = c.shuffleModeEnabled
            repeatMode = c.repeatMode
            speed = c.playbackParameters.speed
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentId = mediaItem?.mediaId?.toLongOrNull()
                    position = 0L
                }

                override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                    shuffle = enabled
                }

                override fun onRepeatModeChanged(mode: Int) {
                    repeatMode = mode
                }

                override fun onPlaybackParametersChanged(params: PlaybackParameters) {
                    speed = params.speed
                }
            }
            c.addListener(listener)
            onDispose { c.removeListener(listener) }
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let { position = it.currentPosition.coerceAtLeast(0L) }
            delay(500)
        }
    }

    LaunchedEffect(sleepEndAt) {
        val end = sleepEndAt ?: return@LaunchedEffect
        delay((end - System.currentTimeMillis()).coerceAtLeast(0L))
        controller?.pause()
        sleepEndAt = null
        snackbar.showSnackbar("Sleep timer finished")
    }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun play(song: Song, queue: List<Song>) {
        val ctrl = controller ?: return
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        ctrl.setMediaItems(queue.map { it.toMediaItem() }, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
        currentId = song.id
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        val queue = list.shuffled()
        play(queue.first(), queue)
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun playNext(song: Song) {
        val ctrl = controller ?: return
        if (ctrl.mediaItemCount == 0) {
            play(song, listOf(song))
        } else {
            ctrl.addMediaItem(ctrl.currentMediaItemIndex + 1, song.toMediaItem())
            toast("Playing next: ${song.title}")
        }
    }

    fun addToQueue(song: Song) {
        val ctrl = controller ?: return
        if (ctrl.mediaItemCount == 0) {
            play(song, listOf(song))
        } else {
            ctrl.addMediaItem(song.toMediaItem())
            toast("Added to queue: ${song.title}")
        }
    }

    fun toggleFav(song: Song) {
        favorites = if (song.id in favorites) favorites - song.id else favorites + song.id
        store.saveFavorites(favorites)
    }

    fun updatePlaylists(list: List<Playlist>) {
        playlists = list
        store.savePlaylists(list)
    }

    val actions = SongActions(
        onFavorite = { toggleFav(it) },
        onPlayNext = { playNext(it) },
        onAddToQueue = { addToQueue(it) },
        onAddToPlaylist = { songToAdd = it }
    )

    val visible = remember(songs, query, sort) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) songs else songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
        when (sort) {
            SortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION -> filtered.sortedByDescending { it.duration }
            SortOrder.RECENT -> filtered.sortedByDescending { it.dateAdded }
        }
    }

    BackHandler(enabled = searchOpen) {
        searchOpen = false
        query = ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Search songs, artists, albums") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    } else {
                        Column {
                            Text("Offline Music", fontWeight = FontWeight.Bold)
                            Text(
                                "${songs.size} songs",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searchOpen) query = ""
                        searchOpen = !searchOpen
                    }) {
                        Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    leadingIcon = {
                                        if (order == sort) Icon(Icons.Default.Check, null)
                                    },
                                    onClick = {
                                        sort = order
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                    if (tab == 4) {
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
                        MiniPlayer(
                            song = s,
                            isPlaying = isPlaying,
                            progress = (position.toFloat() / s.duration.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f),
                            onOpen = { showFullPlayer = true },
                            onToggle = { togglePlay() },
                            onNext = { controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem() }
                        )
                    }
                }
                NavigationBar {
                    val labels = listOf("Songs", "Albums", "Artists", "Favorites", "Playlists")
                    val icons = listOf(
                        Icons.Default.MusicNote,
                        Icons.Default.Album,
                        Icons.Default.Person,
                        Icons.Default.Favorite,
                        Icons.AutoMirrored.Filled.QueueMusic
                    )
                    labels.forEachIndexed { i, label ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(icons[i], label) },
                            label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (loading) {
                Text("Scanning your music…", Modifier.align(Alignment.Center))
            } else {
                Crossfade(targetState = tab, label = "Tab") { t ->
                    when (t) {
                        0 -> SongList(
                            songs = visible,
                            currentId = currentId,
                            isPlaying = isPlaying,
                            actions = actions,
                            onPlay = { play(it, visible) },
                            onShuffle = { shuffleAll(visible) },
                            emptyText = if (query.isBlank()) "No songs found" else "No results for \"$query\""
                        )

                        1 -> GroupGrid(
                            songs = visible,
                            keyOf = { it.album },
                            round = false,
                            currentId = currentId,
                            isPlaying = isPlaying,
                            actions = actions,
                            onPlay = ::play,
                            onShuffle = ::shuffleAll
                        )

                        2 -> GroupGrid(
                            songs = visible,
                            keyOf = { it.artist },
                            round = true,
                            currentId = currentId,
                            isPlaying = isPlaying,
                            actions = actions,
                            onPlay = ::play,
                            onShuffle = ::shuffleAll
                        )

                        3 -> {
                            val favs = visible.filter { it.isFavorite }
                            SongList(
                                songs = favs,
                                currentId = currentId,
                                isPlaying = isPlaying,
                                actions = actions,
                                onPlay = { play(it, favs) },
                                onShuffle = { shuffleAll(favs) },
                                emptyText = "Tap the heart on a song to add favorites"
                            )
                        }

                        else -> PlaylistList(
                            playlists = playlists,
                            songsById = songsById,
                            currentId = currentId,
                            isPlaying = isPlaying,
                            actions = actions,
                            onPlay = ::play,
                            onShuffle = ::shuffleAll,
                            onCreate = { showNewPlaylist = true },
                            onDelete = { id -> updatePlaylists(playlists.filterNot { it.id == id }) },
                            onRemoveSong = { pid, song ->
                                updatePlaylists(playlists.map {
                                    if (it.id == pid) it.copy(songIds = it.songIds - song.id) else it
                                })
                            }
                        )
                    }
                }
            }
        }
    }

    if (showFullPlayer && currentSong != null) {
        NowPlayingSheet(
            song = currentSong,
            isPlaying = isPlaying,
            position = position,
            shuffle = shuffle,
            repeatMode = repeatMode,
            speed = speed,
            sleepActive = sleepEndAt != null,
            onDismiss = { showFullPlayer = false },
            onToggle = { togglePlay() },
            onNext = { controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem() },
            onPrev = {
                controller?.let {
                    if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(0L)
                    else it.seekToPreviousMediaItem()
                }
            },
            onSeek = { newPos ->
                position = newPos
                controller?.seekTo(newPos)
            },
            onFavorite = { toggleFav(currentSong) },
            onShuffle = { controller?.shuffleModeEnabled = !shuffle },
            onRepeat = {
                controller?.repeatMode = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            },
            onSpeed = {
                val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                val next = steps.firstOrNull { it > speed + 0.01f } ?: steps.first()
                controller?.setPlaybackSpeed(next)
            },
            onSleep = { minutes ->
                sleepEndAt = if (minutes == 0) null else System.currentTimeMillis() + minutes * 60_000L
                toast(if (minutes == 0) "Sleep timer off" else "Music will stop in $minutes min")
            }
        )
    }

    if (showNewPlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPlaylist = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        updatePlaylists(playlists + Playlist(UUID.randomUUID().toString(), name.trim(), emptyList()))
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
                if (playlists.isEmpty()) {
                    Text("Create a playlist first in the Playlists tab.")
                } else {
                    LazyColumn {
                        items(playlists) { pl ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    if (song.id in pl.songIds) {
                                        toast("Already in ${pl.name}")
                                    } else {
                                        updatePlaylists(playlists.map {
                                            if (it.id == pl.id) it.copy(songIds = it.songIds + song.id) else it
                                        })
                                        toast("Added to ${pl.name}")
                                    }
                                    songToAdd = null
                                },
                                headlineContent = { Text(pl.name) },
                                supportingContent = { Text("${pl.songIds.size} tracks") }
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { songToAdd = null }) { Text("Close") } }
        )
    }
}

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { onOpen() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArtwork(song.artUri, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        song.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.SkipNext, "Next")
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    song: Song,
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
    onSleep: (Int) -> Unit
) {
    var sleepMenu by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val total = song.duration.coerceAtLeast(1L)
    val fraction = dragFraction ?: (position.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AlbumArtwork(
                artUri = song.artUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(32.dp))
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = fraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    dragFraction?.let { onSeek((it * total).toLong()) }
                    dragFraction = null
                }
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime((fraction * total).toLong()), style = MaterialTheme.typography.labelMedium)
                Text(formatTime(total), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(
                        Icons.Default.Shuffle,
                        "Shuffle",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(onClick = onToggle, modifier = Modifier.size(76.dp), shape = CircleShape) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "Play/Pause",
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = speed != 1f,
                    onClick = onSpeed,
                    label = { Text("${speed}x") },
                    leadingIcon = { Icon(Icons.Default.Speed, null, Modifier.size(18.dp)) }
                )
                Box {
                    FilterChip(
                        selected = sleepActive,
                        onClick = { sleepMenu = true },
                        label = { Text(if (sleepActive) "Sleep timer on" else "Sleep timer") },
                        leadingIcon = { Icon(Icons.Default.Timer, null, Modifier.size(18.dp)) }
                    )
                    DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                        listOf(0 to "Off", 15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour").forEach { (min, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSleep(min)
                                    sleepMenu = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}

@Composable
fun ShuffleBar(count: Int, onPlayAll: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$count songs",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(onClick = onPlayAll) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Play")
        }
        OutlinedButton(onClick = onShuffle) {
            Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Shuffle")
        }
    }
}

@Composable
fun SongList(
    songs: List<Song>,
    currentId: Long?,
    isPlaying: Boolean,
    actions: SongActions,
    onPlay: (Song) -> Unit,
    onShuffle: (() -> Unit)? = null,
    onRemove: ((Song) -> Unit)? = null,
    emptyText: String = "No songs found"
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (onShuffle != null) {
            item { ShuffleBar(songs.size, onPlayAll = { onPlay(songs.first()) }, onShuffle = onShuffle) }
        }
        items(songs, key = { it.id }) { s ->
            SongRow(s, s.id == currentId, isPlaying, actions, onPlay, onRemove)
        }
    }
}

@Composable
fun SongRow(
    s: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    actions: SongActions,
    onPlay: (Song) -> Unit,
    onRemove: ((Song) -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onPlay(s) },
        colors = ListItemDefaults.colors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent
        ),
        headlineContent = {
            Text(
                s.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text("${s.artist} • ${formatTime(s.duration)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                AlbumArtwork(s.artUri, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)))
                if (isCurrent && isPlaying) {
                    Box(
                        Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.GraphicEq, null, tint = Color.White) }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { actions.onFavorite(s) }) {
                    Icon(
                        if (s.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        "Favorite",
                        tint = if (s.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Play next") },
                            leadingIcon = { Icon(Icons.Default.SkipNext, null) },
                            onClick = { menu = false; actions.onPlayNext(s) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to queue") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            onClick = { menu = false; actions.onAddToQueue(s) }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                            onClick = { menu = false; actions.onAddToPlaylist(s) }
                        )
                        if (onRemove != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from playlist") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; onRemove(s) }
                            )
                        }
                    }
                }
            }
        }
    )
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
    val groups = remember(songs) { songs.groupBy(keyOf) }
    var selected by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selected != null) { selected = null }

    val groupSongs = selected?.let { groups[it] }
    if (selected != null && groupSongs != null) {
        Column {
            TextButton(onClick = { selected = null }, modifier = Modifier.padding(horizontal = 12.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Spacer(Modifier.width(8.dp))
                Text(selected ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SongList(
                songs = groupSongs,
                currentId = currentId,
                isPlaying = isPlaying,
                actions = actions,
                onPlay = { onPlay(it, groupSongs) },
                onShuffle = { onShuffle(groupSongs) }
            )
        }
    } else {
        if (groups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        val shape: Shape = if (round) CircleShape else RoundedCornerShape(16.dp)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(groups.keys.toList()) { name ->
                val list = groups[name].orEmpty()
                Card(
                    Modifier.clickable { selected = name },
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        list.firstOrNull()?.let {
                            AlbumArtwork(it.artUri, Modifier.fillMaxWidth().aspectRatio(1f).clip(shape))
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${list.size} songs", style = MaterialTheme.typography.bodySmall)
                    }
                }
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
    BackHandler(enabled = selectedId != null) { selectedId = null }

    val selected = playlists.find { it.id == selectedId }
    if (selected != null) {
        val plSongs = selected.songIds.mapNotNull { songsById[it] }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedId = null }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(8.dp))
                    Text(selected.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SongList(
                songs = plSongs,
                currentId = currentId,
                isPlaying = isPlaying,
                actions = actions,
                onPlay = { onPlay(it, plSongs) },
                onShuffle = { onShuffle(plSongs) },
                onRemove = { onRemoveSong(selected.id, it) },
                emptyText = "This playlist is empty. Use ⋮ on a song to add it."
            )
        }
    } else if (playlists.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                null,
                Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text("No playlists yet")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCreate) { Text("Create Playlist") }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(playlists, key = { it.id }) { pl ->
                ListItem(
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { selectedId = pl.id },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(pl.name, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text("${pl.songIds.size} tracks") },
                    leadingContent = {
                        val firstSong = pl.songIds.firstNotNullOfOrNull { songsById[it] }
                        if (firstSong != null) {
                            AlbumArtwork(firstSong.artUri, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)))
                        } else {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(50.dp))
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { onDelete(pl.id) }) { Icon(Icons.Default.Delete, "Delete playlist") }
                    }
                )
            }
        }
    }
}
