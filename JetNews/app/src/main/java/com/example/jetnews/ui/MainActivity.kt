package com.example.jetnews.ui

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class AppScreen { MAIN, SETTINGS, EQUALIZER, QUEUE }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val settingsStore = remember { SettingsStore(ctx) }
            var settings by remember { mutableStateOf(settingsStore.load()) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK, ThemeMode.AMOLED -> true
            }
            MaterialTheme(colorScheme = buildColorScheme(ctx, settings, dark)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MusicAppScreen(settings) {
                        settings = it
                        settingsStore.save(it)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MusicAppScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val permState = rememberPermissionState(perm)

    LaunchedEffect(Unit) {
        if (!permState.status.isGranted) permState.launchPermissionRequest()
    }

    // Android 13+ hides the playback notification unless this is granted
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notifState = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(permState.status.isGranted) {
            if (permState.status.isGranted && !notifState.status.isGranted) notifState.launchPermissionRequest()
        }
    }

    if (permState.status.isGranted) {
        MusicContent(settings, onSettingsChange)
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
fun MusicContent(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
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
    var playCounts by remember { mutableStateOf(store.loadPlayCounts()) }
    var recents by remember { mutableStateOf(store.loadRecents()) }

    val songs = remember(allSongs, favorites, settings.minDurationSec, settings.hiddenFolders) {
        allSongs
            .filter { it.duration >= settings.minDurationSec * 1000L && it.folder !in settings.hiddenFolders }
            .map { it.copy(isFavorite = it.id in favorites) }
    }
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val folders = remember(allSongs) {
        allSongs.groupingBy { it.folder }.eachCount().toList().sortedBy { it.first.lowercase() }
    }

    var screen by remember { mutableStateOf(AppScreen.MAIN) }
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(settings.defaultSort) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var songFilter by remember { mutableStateOf(SongFilter.ALL) }
    LaunchedEffect(settings.defaultSort) { sort = settings.defaultSort }

    var currentId by remember { mutableStateOf<Long?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var speed by remember { mutableFloatStateOf(1f) }
    var sleepEndAt by remember { mutableStateOf<Long?>(null) }
    var queueVersion by remember { mutableIntStateOf(0) }
    var resumed by remember { mutableStateOf(true) }

    var showFullPlayer by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var songToAdd by remember { mutableStateOf<Song?>(null) }

    val currentSong = currentId?.let { songsById[it] }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    fun saveState() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val ids = (0 until c.mediaItemCount).mapNotNull { c.getMediaItemAt(it).mediaId.toLongOrNull() }
        store.saveQueue(ids, c.currentMediaItemIndex, c.currentPosition.coerceAtLeast(0L))
    }

    fun recordPlay(id: Long) {
        playCounts = playCounts + (id to ((playCounts[id] ?: 0) + 1))
        recents = (listOf(id) + recents.filter { it != id }).take(50)
        store.savePlayCounts(playCounts)
        store.saveRecents(recents)
    }

    // Track whether the app is on screen so we can stop updating the UI in the background
    DisposableEffect(ctx) {
        val owner = ctx as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumed = true
            if (event == Lifecycle.Event.ON_PAUSE) resumed = false
            if (event == Lifecycle.Event.ON_STOP) saveState()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

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
                    if (!playing) saveState()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val id = mediaItem?.mediaId?.toLongOrNull()
                    currentId = id
                    position = 0L
                    if (id != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) recordPlay(id)
                    saveState()
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    queueVersion++
                    saveState()
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

    // Restore the previous queue (paused) once both the player and the library are ready
    var restored by remember { mutableStateOf(false) }
    LaunchedEffect(controller, songsById) {
        val c = controller ?: return@LaunchedEffect
        if (restored || songsById.isEmpty()) return@LaunchedEffect
        restored = true
        if (settings.resumeOnLaunch && c.mediaItemCount == 0) {
            val saved = store.loadQueue() ?: return@LaunchedEffect
            val items = saved.ids.mapNotNull { songsById[it]?.toMediaItem() }
            if (items.isNotEmpty()) {
                c.setMediaItems(items, saved.index.coerceIn(0, items.size - 1), saved.position)
                c.prepare()
            }
        }
    }

    // Poll only while audible and on screen; otherwise the CPU is free to sleep
    LaunchedEffect(controller, isPlaying, resumed, showFullPlayer, settings.lowPower) {
        controller?.let { position = it.currentPosition.coerceAtLeast(0L) }
        val interval = when {
            settings.lowPower -> if (showFullPlayer) 1000L else 2000L
            showFullPlayer -> 250L
            else -> 1000L
        }
        while (isPlaying && resumed) {
            delay(interval)
            controller?.let { position = it.currentPosition.coerceAtLeast(0L) }
        }
    }

    LaunchedEffect(sleepEndAt) {
        val end = sleepEndAt ?: return@LaunchedEffect
        delay((end - System.currentTimeMillis()).coerceAtLeast(0L))
        controller?.pause()
        sleepEndAt = null
        snackbar.showSnackbar("Sleep timer finished")
    }

    fun play(song: Song, queue: List<Song>) {
        val ctrl = controller ?: return
        val startIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        ctrl.setMediaItems(queue.map { it.toMediaItem() }, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
        currentId = song.id
        recordPlay(song.id)
    }

    fun shuffleAll(list: List<Song>) {
        if (list.isEmpty()) return
        val queue = list.shuffled()
        play(queue.first(), queue)
    }

    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() {
        controller?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
    }

    fun skipPrev() {
        controller?.let {
            if (it.currentPosition > 3000 || !it.hasPreviousMediaItem()) it.seekTo(0L) else it.seekToPreviousMediaItem()
        }
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

    val library = remember(songs, query, sort) {
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

    val songsTabList = remember(library, songFilter, recents, playCounts, songsById, query) {
        val q = query.trim()
        fun matches(s: Song) = q.isEmpty() || s.title.contains(q, true) || s.artist.contains(q, true) || s.album.contains(q, true)
        when (songFilter) {
            SongFilter.ALL -> library
            SongFilter.RECENT -> recents.mapNotNull { songsById[it] }.filter(::matches)
            SongFilter.MOST -> songs.filter { (playCounts[it.id] ?: 0) > 0 && matches(it) }
                .sortedByDescending { playCounts[it.id] ?: 0 }
        }
    }

    BackHandler(enabled = screen != AppScreen.MAIN) { screen = AppScreen.MAIN }
    BackHandler(enabled = screen == AppScreen.MAIN && searchOpen) {
        searchOpen = false
        query = ""
    }

    when (screen) {
        AppScreen.SETTINGS -> SettingsScreen(
            settings = settings,
            folders = folders,
            onChange = onSettingsChange,
            onBack = { screen = AppScreen.MAIN }
        )

        AppScreen.EQUALIZER -> EqualizerScreen(onBack = { screen = AppScreen.MAIN })

        AppScreen.QUEUE -> {
            val items = remember(queueVersion, controller) {
                controller?.let { c ->
                    (0 until c.mediaItemCount).map { i ->
                        val m = c.getMediaItemAt(i)
                        QueueItem(
                            m.mediaId,
                            m.mediaMetadata.title?.toString().orEmpty(),
                            m.mediaMetadata.artist?.toString().orEmpty(),
                            m.mediaMetadata.artworkUri
                        )
                    }
                } ?: emptyList()
            }
            val index = remember(currentId, queueVersion, controller) { controller?.currentMediaItemIndex ?: -1 }
            QueueScreen(
                items = items,
                currentIndex = index,
                onPlayAt = { i ->
                    controller?.let {
                        it.seekTo(i, 0L)
                        it.play()
                    }
                },
                onRemove = { controller?.removeMediaItem(it) },
                onMove = { from, to -> controller?.moveMediaItem(from, to) },
                onClear = { controller?.clearMediaItems() },
                onBack = { screen = AppScreen.MAIN }
            )
        }

        AppScreen.MAIN -> Scaffold(
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
                                        leadingIcon = { if (order == sort) Icon(Icons.Default.Check, null) },
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
                        Box {
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Equalizer") },
                                    leadingIcon = { Icon(Icons.Default.Equalizer, null) },
                                    onClick = { moreMenu = false; screen = AppScreen.EQUALIZER }
                                )
                                DropdownMenuItem(
                                    text = { Text("Queue") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                    onClick = { moreMenu = false; screen = AppScreen.QUEUE }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { moreMenu = false; screen = AppScreen.SETTINGS }
                                )
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
                                onNext = { skipNext() },
                                onPrev = { skipPrev() }
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
                    val tabContent: @Composable (Int) -> Unit = { t ->
                        when (t) {
                            0 -> SongList(
                                songs = songsTabList,
                                currentId = currentId,
                                isPlaying = isPlaying,
                                actions = actions,
                                onPlay = { play(it, songsTabList) },
                                onShuffle = { shuffleAll(songsTabList) },
                                emptyText = when {
                                    query.isNotBlank() -> "No results for \"$query\""
                                    songFilter != SongFilter.ALL -> "Nothing played yet"
                                    else -> "No songs found"
                                },
                                header = {
                                    Row(
                                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SongFilter.entries.forEach { f ->
                                            FilterChip(
                                                selected = songFilter == f,
                                                onClick = { songFilter = f },
                                                label = { Text(f.label) }
                                            )
                                        }
                                    }
                                }
                            )

                            1 -> GroupGrid(
                                songs = library,
                                keyOf = { it.album },
                                round = false,
                                currentId = currentId,
                                isPlaying = isPlaying,
                                actions = actions,
                                onPlay = ::play,
                                onShuffle = ::shuffleAll
                            )

                            2 -> GroupGrid(
                                songs = library,
                                keyOf = { it.artist },
                                round = true,
                                currentId = currentId,
                                isPlaying = isPlaying,
                                actions = actions,
                                onPlay = ::play,
                                onShuffle = ::shuffleAll
                            )

                            3 -> {
                                val favs = library.filter { it.isFavorite }
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
                    if (settings.reduceAnimations || settings.lowPower) {
                        tabContent(tab)
                    } else {
                        Crossfade(targetState = tab, label = "Tab") { t -> tabContent(t) }
                    }
                }
            }
        }
    }

    if (showFullPlayer && currentSong != null) {
        NowPlayingSheet(
            song = currentSong,
            settings = settings,
            isPlaying = isPlaying,
            position = position,
            shuffle = shuffle,
            repeatMode = repeatMode,
            speed = speed,
            sleepActive = sleepEndAt != null,
            onDismiss = { showFullPlayer = false },
            onToggle = { togglePlay() },
            onNext = { skipNext() },
            onPrev = { skipPrev() },
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
            },
            onQueue = {
                showFullPlayer = false
                screen = AppScreen.QUEUE
            },
            onEqualizer = {
                showFullPlayer = false
                screen = AppScreen.EQUALIZER
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
