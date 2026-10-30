package com.zone

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.palette.graphics.Palette
import com.zone.ui.theme.ZoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap

// ---------- Sorting ----------

// Decides how song titles are compared, so the list is in proper A-Z order
private val collator = Collator.getInstance().apply {
    strength = Collator.PRIMARY   // ignore case and accents when comparing
}

// Puts a list of songs in A-Z order by title
private fun sortSongs(list: List<Song>): List<Song> =
    list.sortedWith { a, b -> collator.compare(a.title, b.title) }

// How the library is ordered on the Songs and Folders tabs
enum class SortMode(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    RECENT("Recently added")   // really "recently modified": folders don't expose a date-added
}

private fun sortFor(list: List<Song>, mode: SortMode): List<Song> = when (mode) {
    SortMode.TITLE -> list   // the library is already kept A-Z by title
    SortMode.ARTIST -> list.sortedWith { a, b ->
        val byArtist = collator.compare(a.artist, b.artist)
        if (byArtist != 0) byArtist else collator.compare(a.title, b.title)
    }
    SortMode.RECENT -> list.sortedByDescending { it.lastModified }
}

// "A".."Z", or "#" for digits, symbols and non-Latin scripts (used by the fast-scroll strip)
fun sectionLetter(text: String): String {
    val c = foldText(text).trim().firstOrNull()?.uppercaseChar()
    return if (c != null && c in 'A'..'Z') c.toString() else "#"
}

// One look for every row in the lists: rounded, with a thin soft outline
private val rowShape = RoundedCornerShape(14.dp)

@Composable
private fun Modifier.rowStyle(): Modifier = this
    .clip(rowShape)
    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), rowShape)

// ---------- Screen ----------

// The main home screen. It finds your songs, keeps the list up to date,
// remembers what was playing last time, and shows the library with the
// small "now playing" bar at the bottom.
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun HomeScreen(engine: MusicEngine, onPillClick: () -> Unit) {

    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val cache = remember { SongCache(context) }
    val sources by settings.folderUris.collectAsState(initial = null)
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    val sortIndex by settings.sortMode.collectAsState(initial = 0)
    val sortMode = SortMode.values().getOrElse(sortIndex) { SortMode.TITLE }
    val shownSongs = remember(songs, sortMode) { sortFor(songs, sortMode) }
    var scanning by remember { mutableStateOf(false) }
    var artworkColor by remember { mutableStateOf<Color?>(null) }

    // Rescan control
    var refreshTick by remember { mutableIntStateOf(0) }
    var lastScan by remember { mutableLongStateOf(0L) }
    // At most 2 tag-reading threads so the scan doesn't hog the CPU
    val tagDispatcher = remember { Dispatchers.IO.limitedParallelism(2) }

    // Playlists (one shared instance from ZoneApp)
    val store = remember { (context.applicationContext as ZoneApp).playlists }
    val playlists by store.playlists.collectAsState()

    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            scope.launch { settings.addFolder(uri) }
        }
    }

    // Rescan when the app comes back to the foreground (throttled to once per 30s)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (System.currentTimeMillis() - lastScan > 30_000) refreshTick++
    }

    // Runs when the set of sources changes, or when a refresh is requested
    LaunchedEffect(sources, refreshTick) {
        val currentSources = sources ?: return@LaunchedEffect

        // 1. Show the cached list instantly (sorted)
        val cached = withContext(Dispatchers.IO) { cache.load() }
            .filter { it.source in currentSources }
            .distinctBy { it.uri }
        if (songs.isEmpty()) songs = sortSongs(cached)
        scanning = songs.isEmpty()   // first run: show the progress line while we look

        // 2. Cheap scan: file names only, no tags
        val scanner = SongScanner()
        val cachedByUri = cached.associateBy { it.uri }
        val scanned = withContext(Dispatchers.IO) {
            currentSources
                .flatMap { scanner.scanFolder(context, it.toUri()) }
                .distinctBy { it.uri }
        }

        // 3. Sort the scan into unchanged, new, and changed-since-last-time files.
        //    Changed files keep their old tags on screen until the new tags are read.
        val fresh = mutableListOf<Song>()
        val kept = mutableListOf<Song>()
        for (s in scanned) {
            val old = cachedByUri[s.uri]
            when {
                old == null -> fresh.add(s)
                // 0 = an old cache without dates: trust it once and just record the date
                old.lastModified == 0L || old.lastModified == s.lastModified ->
                    kept.add(old.copy(lastModified = s.lastModified))
                else -> {
                    kept.add(old)
                    fresh.add(s)
                }
            }
        }

        // Apply deletions right away (songs that no longer exist drop out)
        var current: List<Song> = kept
        val sorted = sortSongs(current)
        if (sorted != songs) songs = sorted
        if (current.toSet() != cached.toSet()) {
            withContext(Dispatchers.IO) { cache.save(current) }
        }

        // 4. Read tags for new and changed files in small batches, showing each batch as it finishes
        scanning = fresh.isNotEmpty()
        for (batch in fresh.chunked(25)) {
            val tagged = withContext(tagDispatcher) {
                batch.map {
                    ensureActive()
                    scanner.readTags(context, it)
                }
            }
            val taggedUris = tagged.map { it.uri }.toSet()
            current = current.filter { it.uri !in taggedUris } + tagged
            songs = sortSongs(current)
            withContext(Dispatchers.IO) { cache.save(current) }
        }

        scanning = false
        lastScan = System.currentTimeMillis()
    }

    // Extract artwork vibrant color for the ui
    LaunchedEffect(engine.artwork) {
        val art = engine.artwork
        if (art != null) {
            val bitmap = art.asAndroidBitmap()
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).generate()
            }
            artworkColor = Color(palette.getDarkVibrantColor(Color(0xFF003A59).toArgb()))
        }
    }

    // Save the last song played and other stuff
    LaunchedEffect(engine.title, engine.mediaIndex, engine.isShuffle, engine.repeatState, engine.queue) {
        val queue = engine.queue
        val current = queue.getOrNull(engine.mediaIndex)?.uri ?: return@LaunchedEffect
        settings.saveSession(queue.map { it.uri }, current, engine.isShuffle, engine.repeatState)
        settings.saveLastPosition(engine.currentPositionMs())   // 0 on a new song, real value on a queue edit
    }

    // Keeps the pill's progress line moving when the full player isn't open
    LaunchedEffect(engine.isPlaying) {
        while (engine.isPlaying) {
            engine.updatePosition()
            delay(500)
        }
    }

    // Remember where we are: every 5 seconds while playing, and once when playback stops
    LaunchedEffect(engine.isPlaying) {
        if (engine.queue.isEmpty()) return@LaunchedEffect
        if (engine.isPlaying) {
            while (true) {
                delay(5_000)
                settings.saveLastPosition(engine.currentPositionMs())
            }
        } else {
            settings.saveLastPosition(engine.currentPositionMs())
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (engine.queue.isNotEmpty()) {
            scope.launch { settings.saveLastPosition(engine.currentPositionMs()) }
        }
    }

    var restored by remember { mutableStateOf(false) }

    // Restore last player state if any
    LaunchedEffect(songs) {
        if (restored || songs.isEmpty() || engine.queue.isNotEmpty()) return@LaunchedEffect

        val savedCurrent = settings.lastPlayedUri.first() ?: return@LaunchedEffect
        val byUri = songs.associateBy { it.uri }
        val queue = settings.queueUris.first().mapNotNull { byUri[it] }   // skips deleted files
        val index = queue.indexOfFirst { it.uri == savedCurrent }
        if (index < 0) return@LaunchedEffect
        val savedPosition = settings.lastPosition.first() ?: 0L

        restored = true
        engine.loadPlaylist(queue, index, autoPlay = false, startPosition = savedPosition)
        if (settings.shuffle.first() == true) engine.setShuffleEnabled(true)
        settings.repeatMode.first()?.let { engine.setRepeatMode(it) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        HomeScreenUI(
            hasFolder = sources?.isNotEmpty() == true,
            sortMode = sortMode,
            onSortChange = { mode -> scope.launch { settings.saveSortMode(mode.ordinal) } },
            scanning = scanning,
            onAddFolder = { launcher.launch(null) },
            folderSources = sources ?: emptySet(),
            onRemoveFolder = { source ->
                scope.launch {
                    settings.removeFolder(source.toUri())
                    try {
                        context.contentResolver.releasePersistableUriPermission(
                            source.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                }
            },
            songs = shownSongs,
            onSongClick = { list, index ->
                engine.loadPlaylist(list, index)
                engine.play()
                onPillClick()
            },
            playlists = playlists,
            songsOf = { id -> store.songsOf(id, songs) },
            onPlaylistSongClick = { list, index ->
                engine.loadPlaylist(list, index)
                onPillClick()
            },
            onCreatePlaylist = { name -> store.createPlaylist(name) },
            onDeletePlaylist = { id -> store.deletePlaylist(id) },
            onToggleSong = { playlistId, uri -> store.toggleSong(playlistId, uri) },
            onPlayAll = { list ->
                if (list.isNotEmpty()) {
                    engine.setShuffleEnabled(false)
                    engine.loadPlaylist(list, 0)
                }
            },
            onShuffleAll = { list ->
                if (list.isNotEmpty()) {
                    engine.setShuffleEnabled(true)
                    engine.loadPlaylist(list, list.indices.random())
                }
            },
            onPlayNext = { song ->
                engine.playNext(song)
                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = { song ->
                engine.addToQueue(song)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            },
            onDeleteSong = { song ->
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        try {
                            DocumentsContract.deleteDocument(context.contentResolver, song.uri)
                        } catch (_: Exception) {
                            false
                        }
                    }
                    if (deleted) {
                        engine.songDeleted(song.uri)            // out of the queue
                        store.removeSongEverywhere(song.uri)    // out of every playlist
                        songs = songs.filter { it.uri != song.uri }
                        val remaining = songs
                        withContext(Dispatchers.IO) { cache.save(remaining) }
                    } else {
                        Toast.makeText(
                            context,
                            "Couldn't delete. Re-add the folder to allow deleting.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )

        NowPlayingPill(
            title = engine.title,
            artist = engine.artist,
            isPlaying = engine.isPlaying,
            albumArt = engine.artwork,
            albumArtColor = artworkColor,
            onPlayPause = { if (engine.isPlaying) engine.pause() else engine.play() },
            progress = {
                if (engine.duration > 0L) {
                    (engine.position.toFloat() / engine.duration).coerceIn(0f, 1f)
                } else 0f
            },
            onClick = onPillClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

// Replace your existing HomeScreenUI() and songUI() functions with these versions.
// Keep the rest of HomeScreen.kt unchanged. The existing imports already include
// SortByAlpha, DropdownMenu, DropdownMenuItem, Box, Row, Spacer and related Compose APIs.

@Composable
fun HomeScreenUI(
    hasFolder: Boolean,
    sortMode: SortMode,
    onSortChange: (SortMode) -> Unit,
    scanning: Boolean,
    onAddFolder: () -> Unit,
    folderSources: Set<String>,
    onRemoveFolder: (String) -> Unit,
    songs: List<Song>?,
    onSongClick: (List<Song>, Int) -> Unit,
    playlists: List<Playlist>,
    songsOf: (String) -> List<Song>,
    onPlaylistSongClick: (List<Song>, Int) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onToggleSong: (String, Uri) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    // Hoisted out of the branches so closing search keeps the tab you were on
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = searching) { searching = false; query = "" }

    Column(
        modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        if (searching) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onClose = { searching = false; query = "" }
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Zone Music",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 20.sp,
                    modifier = Modifier.offset(x = 20.dp)
                )

                // The sort button has moved to the Songs list toolbar.
                if (hasFolder) {
                    IconButton(onClick = { searching = true }) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            tint = MaterialTheme.colorScheme.onBackground,
                            contentDescription = "Search",
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }

        if (scanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }
        Spacer(modifier = Modifier.size(6.dp))

        if (!hasFolder) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Click to add folder",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onAddFolder() }
                )
            }
        } else if (searching) {
            SearchResults(
                query = query,
                songs = songs,
                onSongClick = onSongClick,
                playlists = playlists,
                onToggleSong = onToggleSong,
                onDeleteSong = onDeleteSong,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
                modifier = Modifier.weight(1f)
            )
        } else {
            val tabs = listOf("Songs", "Playlists", "Artists", "Albums", "Folders")

            // The fast-scroll letters follow the sort: none when sorting by date
            val sectionOf: ((Song) -> String)? = when (sortMode) {
                SortMode.TITLE -> ({ song: Song -> sectionLetter(song.title) })
                SortMode.ARTIST -> ({ song: Song -> sectionLetter(song.artist) })
                SortMode.RECENT -> null
            }

            ScrollableTabRow(
                selectedTabIndex = tab,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label) },
                        selectedContentColor = Color.White,
                        unselectedContentColor = Color.Gray
                    )
                }
            }

            when (tab) {
                0 -> songUI(
                    songs = songs,
                    sectionOf = sectionOf,
                    onSongClick = { i -> onSongClick(songs ?: emptyList(), i) },
                    modifier = Modifier.weight(1f),
                    playlists = playlists,
                    onToggleSong = onToggleSong,
                    onDeleteSong = onDeleteSong,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onPlayAll = { onPlayAll(songs ?: emptyList()) },
                    onShuffleAll = { onShuffleAll(songs ?: emptyList()) },
                    sortMode = sortMode,
                    onSortChange = onSortChange,
                    showSort = true
                )

                1 -> playlistUI(
                    playlists = playlists,
                    allSongs = songs ?: emptyList(),
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    songsOf = songsOf,
                    onSongClick = onPlaylistSongClick,
                    onCreatePlaylist = onCreatePlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    onToggleSong = onToggleSong,
                    onDeleteSong = onDeleteSong,
                    onPlayAll = onPlayAll,
                    onShuffleAll = onShuffleAll,
                    modifier = Modifier.weight(1f)
                )

                2 -> groupUI(
                    songs = songs,
                    groupOf = { it.artist },
                    subtitleOf = { list -> "${list.size} songs" },
                    showArt = false,
                    onSongClick = onSongClick,
                    playlists = playlists,
                    onToggleSong = onToggleSong,
                    onDeleteSong = onDeleteSong,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onPlayAll = onPlayAll,
                    onShuffleAll = onShuffleAll,
                    modifier = Modifier.weight(1f)
                )

                3 -> groupUI(
                    songs = songs,
                    groupOf = { it.album },
                    subtitleOf = { list -> "${list.first().artist} · ${list.size} songs" },
                    showArt = true,
                    onSongClick = onSongClick,
                    playlists = playlists,
                    onToggleSong = onToggleSong,
                    onDeleteSong = onDeleteSong,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onPlayAll = onPlayAll,
                    onShuffleAll = onShuffleAll,
                    modifier = Modifier.weight(1f)
                )

                4 -> folderUI(
                    songs = songs,
                    folderSources = folderSources,
                    onSongClick = onSongClick,
                    onAddFolder = onAddFolder,
                    onRemoveFolder = onRemoveFolder,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    playlists = playlists,
                    onToggleSong = onToggleSong,
                    onDeleteSong = onDeleteSong,
                    onPlayAll = onPlayAll,
                    onShuffleAll = onShuffleAll,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// The Songs tab: a scrolling list of songs. Each row shows the cover, title
// and artist. The sort button is now on the left of Play All and Shuffle.
@Composable
fun songUI(
    songs: List<Song>?,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
    onToggleSong: (String, Uri) -> Unit = { _, _ -> },
    onDeleteSong: (Song) -> Unit = {},
    onPlayNext: (Song) -> Unit = {},
    onAddToQueue: (Song) -> Unit = {},
    onPlayAll: (() -> Unit)? = null,
    onShuffleAll: (() -> Unit)? = null,
    sectionOf: ((Song) -> String)? = null,
    sortMode: SortMode = SortMode.TITLE,
    onSortChange: (SortMode) -> Unit = {},
    showSort: Boolean = false
) {
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var expandedUri by remember { mutableStateOf<Uri?>(null) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }

    // Fast scroll: the first list position of each letter
    val listState = rememberLazyListState()
    val indexScope = rememberCoroutineScope()
    val firstIndexOf = remember(songs, sectionOf) {
        val map = LinkedHashMap<String, Int>()
        if (sectionOf != null && songs != null && songs.size >= 40) {
            songs.forEachIndexed { i, song -> map.putIfAbsent(sectionOf(song), i) }
        }
        map
    }
    val showIndex = firstIndexOf.size > 1

    // Asks "are you sure?" before the file is deleted
    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Delete song?") },
            text = { Text("\"${song.title}\" will be permanently deleted from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSong(song)
                    songToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("Cancel") }
            }
        )
    }

    menuSong?.let { song ->
        AlertDialog(
            onDismissRequest = { menuSong = null },
            title = { Text("Add to playlist") },
            text = {
                LazyColumn {
                    items(playlists, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleSong(p.id, song.uri) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = song.uri in p.uris,
                                onCheckedChange = { onToggleSong(p.id, song.uri) }
                            )
                            Text(
                                text = p.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuSong = null }) { Text("Done") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 72.dp)
            .clip(RoundedCornerShape(topEnd = 25.dp, topStart = 25.dp))
            .background(color = MaterialTheme.colorScheme.surface)
    ) {
        // Sort on the far left; Play All and Shuffle stay on the right.
        if (onPlayAll != null && onShuffleAll != null && !songs.isNullOrEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSort) {
                    Box {
                        var sortMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.SortByAlpha,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = "Sort songs",
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenu,
                            onDismissRequest = { sortMenu = false }
                        ) {
                            SortMode.values().forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (mode == sortMode) "${mode.label}  ✓" else mode.label)
                                    },
                                    onClick = {
                                        sortMenu = false
                                        onSortChange(mode)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlayAll) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play all",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    IconButton(onClick = onShuffleAll) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    end = if (showIndex) 30.dp else 10.dp
                )
            ) {
                itemsIndexed(
                    items = songs ?: emptyList(),
                    key = { _, song -> song.uri.toString() }
                ) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                            .padding(5.dp)
                            .rowStyle()
                            .clickable { onSongClick(index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SongArtwork(
                            uri = song.uri,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .size(55.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 20.dp)
                        ) {
                            Text(
                                text = song.title,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box {
                            IconButton(onClick = { expandedUri = song.uri }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                            DropdownMenu(
                                expanded = expandedUri == song.uri,
                                onDismissRequest = { expandedUri = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Play next") },
                                    onClick = {
                                        expandedUri = null
                                        onPlayNext(song)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to queue") },
                                    onClick = {
                                        expandedUri = null
                                        onAddToQueue(song)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add to playlist") },
                                    onClick = {
                                        expandedUri = null
                                        menuSong = song
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete song") },
                                    onClick = {
                                        expandedUri = null
                                        songToDelete = song
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showIndex) {
                LetterIndex(
                    letters = firstIndexOf.keys.toList(),
                    onSelect = { i ->
                        val target = firstIndexOf.values.elementAt(i)
                        indexScope.launch { listState.scrollToItem(target) }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.85f)
                        .width(24.dp)
                )
            }
        }
    }
}


// The thin A-Z strip on the right of a long list. Tap or drag on it to jump.
@Composable
fun LetterIndex(
    letters: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    var active by remember { mutableStateOf(false) }

    fun pick(y: Float) {
        val i = ((y / heightPx) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        onSelect(i)
    }

    Column(
        modifier
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (active) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                else Color.Transparent
            )
            .pointerInput(letters) {
                detectTapGestures { offset -> pick(offset.y) }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> active = true; pick(offset.y) },
                    onDragEnd = { active = false },
                    onDragCancel = { active = false }
                ) { change, _ -> pick(change.position.y) }
            },
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// The Artists and Albums tabs. Level 1 lists the groups; tapping one shows its songs.
@Composable
fun groupUI(
    songs: List<Song>?,
    groupOf: (Song) -> String,
    subtitleOf: (List<Song>) -> String,
    showArt: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    playlists: List<Playlist>,
    onToggleSong: (String, Uri) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(songs) {
        (songs ?: emptyList())
            .groupBy(groupOf)
            .toList()
            .sortedWith { a, b -> collator.compare(a.first, b.first) }
    }

    BackHandler(enabled = selected != null) { selected = null }

    val current = selected
    val currentSongs = groups.firstOrNull { it.first == current }?.second

    if (current == null || currentSongs == null) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 72.dp)
                .clip(RoundedCornerShape(topEnd = 25.dp, topStart = 25.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            items(groups, key = { it.first }) { (name, list) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                        .padding(5.dp)
                        .rowStyle()
                        .clickable { selected = name },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showArt) {
                        SongArtwork(
                            uri = list.first().uri,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .size(55.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 15.dp).size(40.dp)
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text(
                            text = name,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitleOf(list),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    } else {
        Column(modifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = current,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            songUI(
                songs = currentSongs,
                onSongClick = { i -> onSongClick(currentSongs, i) },
                modifier = Modifier.weight(1f),
                playlists = playlists,
                onToggleSong = onToggleSong,
                onDeleteSong = onDeleteSong,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPlayAll = { onPlayAll(currentSongs) },
                onShuffleAll = { onShuffleAll(currentSongs) }
            )
        }
    }
}

// The Playlists tab. Level 1 is the list of playlists with a "New playlist"
// row. Tapping a playlist opens level 2, which shows its songs.
@Composable
fun playlistUI(
    playlists: List<Playlist>,
    allSongs: List<Song>,
    songsOf: (String) -> List<Song>,
    onSongClick: (List<Song>, Int) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onToggleSong: (String, Uri) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Playlist?>(null) }

    BackHandler(enabled = selectedId != null) { selectedId = null }

    // Dialog: type a name for a new playlist
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { newName = ""; showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) onCreatePlaylist(newName.trim())
                    newName = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { newName = ""; showCreate = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: "are you sure?" before deleting a playlist
    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("\"${p.name}\" will be deleted. Your songs are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(p.id)
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) { Text("Cancel") }
            }
        )
    }

    val current = playlists.firstOrNull { it.id == selectedId }

    if (current == null) {
        // Level 1: new playlist row, then the list of playlists
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 72.dp)
                .clip(RoundedCornerShape(topEnd = 25.dp, topStart = 25.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .padding(5.dp)
                        .rowStyle()
                        .clickable { showCreate = true },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 15.dp).size(30.dp)
                    )
                    Text(
                        text = "New playlist",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }
            items(playlists, key = { it.id }) { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                        .padding(5.dp)
                        .rowStyle()
                        .clickable { selectedId = p.id },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text(
                            text = p.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${p.uris.size} songs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    // Liked Songs can't be deleted
                    if (p.id != LIKED_ID) {
                        IconButton(onClick = { toDelete = p }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete playlist",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Level 2: the songs inside the chosen playlist
        val playlistSongs = songsOf(current.id)

        if (showPicker) {
            // Song picker: every song, with + to add or - to remove
            BackHandler { showPicker = false }
            Column(modifier) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showPicker = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Add songs to ${current.name}",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(bottom = 72.dp)
                        .clip(RoundedCornerShape(topEnd = 25.dp, topStart = 25.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    items(allSongs, key = { it.uri.toString() }) { song ->
                        val inPlaylist = song.uri in current.uris
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(85.dp)
                                .padding(5.dp)
                                .rowStyle(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SongArtwork(
                                uri = song.uri,
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .size(55.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 20.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onToggleSong(current.id, song.uri) }) {
                                Icon(
                                    imageVector = if (inPlaylist) Icons.Filled.Remove else Icons.Filled.Add,
                                    contentDescription = if (inPlaylist) "Remove from playlist" else "Add to playlist",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Column(modifier) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedId = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = current.name,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    // + button, top right: opens the song picker
                    IconButton(onClick = { showPicker = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add songs",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                songUI(
                    songs = playlistSongs,
                    onSongClick = { i -> onSongClick(playlistSongs, i) },
                    modifier = Modifier.weight(1f),
                    playlists = playlists,
                    onToggleSong = onToggleSong,
                    onDeleteSong = onDeleteSong,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onPlayAll = { onPlayAll(playlistSongs) },
                    onShuffleAll = { onShuffleAll(playlistSongs) }
                )
            }
        }
    }
}

// The Folders tab. It shows the folders you added, with a button to add more
// and a bin icon to remove one. Tapping a folder opens its songs, and the back
// arrow takes you back to the folder list.
@Composable
fun folderUI(
    songs: List<Song>?,
    folderSources: Set<String>,
    onSongClick: (List<Song>, Int) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    playlists: List<Playlist>,
    onToggleSong: (String, Uri) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var toRemove by remember { mutableStateOf<String?>(null) }

    val allSongs = songs ?: emptyList()
    val counts = remember(songs) { allSongs.groupingBy { it.source }.eachCount() }
    val folders = remember(folderSources) {
        folderSources.sortedBy { folderName(it).lowercase() }
    }

    BackHandler(enabled = selected != null) { selected = null }

    // Asks "are you sure?" before a folder is removed
    toRemove?.let { source ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Remove folder?") },
            text = {
                Text("\"${folderName(source)}\" and its songs will be removed from your library. Your files are not deleted.")
            },
            confirmButton = {
                TextButton(onClick = { onRemoveFolder(source); toRemove = null }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text("Cancel") }
            }
        )
    }

    val current = selected
    if (current == null) {
        // Level 1: add button, then the list of folders
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 72.dp)
                .clip(RoundedCornerShape(topEnd = 25.dp, topStart = 25.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .padding(5.dp)
                        .rowStyle()
                        .clickable { onAddFolder() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 15.dp).size(30.dp)
                    )
                    Text(
                        text = "Add folder",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }
            items(folders, key = { it }) { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.dp)
                        .padding(5.dp)
                        .rowStyle()
                        .clickable { selected = source },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 15.dp).size(40.dp)
                    )
                    Column(Modifier.weight(1f).padding(start = 20.dp)) {
                        Text(
                            text = folderName(source),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${counts[source] ?: 0} songs",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = { toRemove = source }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Remove folder",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    } else {
        // Level 2: the songs inside the chosen folder
        val folderSongs = allSongs.filter { it.source == current }
        Column(modifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = folderName(current),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp
                )
            }
            songUI(
                songs = folderSongs,
                onSongClick = { i -> onSongClick(folderSongs, i) },
                modifier = Modifier.weight(1f),
                playlists = playlists,
                onToggleSong = onToggleSong,
                onDeleteSong = onDeleteSong,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onPlayAll = { onPlayAll(folderSongs) },
                onShuffleAll = { onShuffleAll(folderSongs) }
            )
        }
    }
}

// The small bar at the bottom showing the current song, with a play/pause
// button. Tapping it opens the full player.
@Composable
fun NowPlayingPill(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    albumArt: ImageBitmap?,
    albumArtColor: Color?,
    progress: () -> Float = { 0f }   // 0..1, read while drawing so it doesn't recompose the list
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(color = albumArtColor ?: MaterialTheme.colorScheme.surfaceVariant)
            .drawWithContent {
                drawContent()
                val h = 2.dp.toPx()
                drawRect(
                    color = Color.White.copy(alpha = 0.85f),
                    topLeft = Offset(0f, size.height - h),
                    size = Size(size.width * progress().coerceIn(0f, 1f), h)
                )
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(55.dp)
                .offset(5.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (albumArt != null) {
                Image(
                    bitmap = albumArt,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .offset(x = 22.dp)
        ) {
            Text(
                text = title.ifEmpty { "Nothing playing" },
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onPlayPause) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(35.dp)
            )
        }
    }
}

// Preview of the bottom bar, only for viewing in Android Studio
@Preview(showBackground = true)
@Composable
fun NowPlayingPillPreview() {
    ZoneTheme {
        NowPlayingPill(
            title = "Tell Your World",
            artist = "livetune",
            isPlaying = true,
            onPlayPause = {},
            onClick = {},
            albumArt = null,
            albumArtColor = Color.Gray
        )
    }
}

// Preview of the home screen, only for viewing in Android Studio
@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    ZoneTheme {
        HomeScreenUI(
            hasFolder = true,
            sortMode = SortMode.TITLE,
            onSortChange = {},
            scanning = false,
            onAddFolder = {},
            folderSources = emptySet(),
            onRemoveFolder = {},
            songs = null,
            onSongClick = { _, _ -> },
            playlists = emptyList(),
            songsOf = { emptyList() },
            onPlaylistSongClick = { _, _ -> },
            onCreatePlaylist = {},
            onDeletePlaylist = {},
            onToggleSong = { _, _ -> },
            onDeleteSong = {},
            onPlayNext = {},
            onAddToQueue = {},
            onPlayAll = {},
            onShuffleAll = {}
        )
    }
}

// ---------- Song artwork (thumbnails with memory + disk cache) ----------

// Finds the cover picture inside a song file and keeps a small copy of it,
// so covers show up quickly and the list scrolls smoothly.
@OptIn(ExperimentalCoroutinesApi::class)
object SongArt {
    private const val TARGET_PX = 192   // thumbnail size; the row image is 55dp

    private val memory = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()   // 1/16 of the heap, in KB
    ) {
        override fun sizeOf(key: String, value: ImageBitmap) =
            value.asAndroidBitmap().byteCount / 1024
    }
    private val missing = ConcurrentHashMap.newKeySet<String>()   // songs known to have no art
    private val dispatcher = Dispatchers.IO.limitedParallelism(2)

    // Quick check: is this cover already ready to show?
    fun peek(uri: Uri): ImageBitmap? = memory.get(uri.toString())

    // Gets a song's cover. Uses a saved copy if there is one, otherwise
    // reads it from the song file and saves a small version for next time.
    suspend fun load(context: Context, uri: Uri): ImageBitmap? {
        val key = uri.toString()
        memory.get(key)?.let { return it }
        if (key in missing) return null

        return withContext(dispatcher) {
            val dir = File(context.cacheDir, "art").apply { mkdirs() }
            val name = sha1(key)
            val jpg = File(dir, "$name.jpg")
            val none = File(dir, "$name.none")

            // 1. Disk cache
            if (jpg.exists()) {
                BitmapFactory.decodeFile(jpg.path)?.asImageBitmap()?.let {
                    memory.put(key, it)
                    return@withContext it
                }
            }
            if (none.exists()) {
                missing.add(key)
                return@withContext null
            }

            ensureActive()   // row scrolled away? stop before the expensive part

            // 2. Read the embedded picture from the file
            val retriever = MediaMetadataRetriever()
            val bytes = try {
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture
            } catch (_: Exception) {
                null
            } finally {
                retriever.release()
            }

            if (bytes == null) {
                none.createNewFile()
                missing.add(key)
                return@withContext null
            }

            // 3. Decode downsampled, so a 3000px cover doesn't cost 36MB
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_PX &&
                bounds.outHeight / (sample * 2) >= TARGET_PX
            ) sample *= 2

            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            )
            if (bitmap == null) {
                missing.add(key)
                return@withContext null
            }

            // 4. Save the thumbnail for next launch
            try {
                jpg.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            } catch (_: Exception) { }

            val image = bitmap.asImageBitmap()
            memory.put(key, image)
            image
        }
    }

    // Turns a song's address into a short, safe file name for the saved cover
    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

// Shows one song's cover as a small rounded square. It stays grey until
// the picture has loaded.
@Composable
fun SongArtwork(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val art by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = SongArt.peek(uri) ?: SongArt.load(context, uri)
    }

    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        art?.let {
            Image(
                bitmap = it,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// Turns a folder's long address into a short name that is easy to read
fun folderName(source: String): String {
    val last = source.toUri().lastPathSegment ?: return "Folder"
    return last.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }
}