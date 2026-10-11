package com.zone

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    var artworkColor by remember { mutableStateOf<Color?>(null) }


    // Rescan control
    var refreshTick by remember { mutableIntStateOf(0) }
    var lastScan by remember { mutableLongStateOf(0L) }
    // At most 2 tag-reading threads so the scan doesn't hog the CPU
    val tagDispatcher = remember { Dispatchers.IO.limitedParallelism(2) }





    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
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

        // 2. Cheap scan: file names only, no tags
        val scanner = SongScanner()
        val cachedByUri = cached.associateBy { it.uri }
        val scanned = withContext(Dispatchers.IO) {
            currentSources
                .flatMap { scanner.scanFolder(context, it.toUri()) }
                .distinctBy { it.uri }
        }

        // 3. Apply deletions right away (songs that no longer exist drop out)
        var current = scanned.mapNotNull { cachedByUri[it.uri] }
        val sorted = sortSongs(current)
        if (sorted != songs) songs = sorted
        if (current.size != cached.size) {
            withContext(Dispatchers.IO) { cache.save(current) }
        }

        // 4. Read tags for NEW files in small batches, showing each batch as it finishes
        val fresh = scanned.filter { it.uri !in cachedByUri }
        for (batch in fresh.chunked(25)) {
            val tagged = withContext(tagDispatcher) {
                batch.map {
                    ensureActive()
                    scanner.readTags(context, it)
                }
            }
            current = current + tagged
            songs = sortSongs(current)
            withContext(Dispatchers.IO) { cache.save(current) }
        }

        lastScan = System.currentTimeMillis()
    }

    //Extract artwork vibrant color for the ui
    LaunchedEffect(engine.artwork) {
        val art = engine.artwork
        if (art != null) {
            val bitmap = (art as ImageBitmap).asAndroidBitmap()
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).generate()
            }
            artworkColor = Color(palette.getDarkVibrantColor(Color(0xFF003A59).toArgb()))
        }
    }

    //save the last song played and other stuff
    LaunchedEffect(engine.title, engine.mediaIndex, engine.isShuffle, engine.repeatState) {
        val queue = engine.queue
        val current = queue.getOrNull(engine.mediaIndex)?.uri ?: return@LaunchedEffect
        settings.saveSession(queue.map { it.uri }, current, engine.isShuffle, engine.repeatState)
    }

    var restored by remember { mutableStateOf(false) }

    //restore last player state if any
    LaunchedEffect(songs) {
        if (restored || songs.isEmpty() || engine.queue.isNotEmpty()) return@LaunchedEffect

        val savedCurrent = settings.lastPlayedUri.first() ?: return@LaunchedEffect
        val byUri = songs.associateBy { it.uri }
        val queue = settings.queueUris.first().mapNotNull { byUri[it] }   // skips deleted files
        val index = queue.indexOfFirst { it.uri == savedCurrent }
        if (index < 0) return@LaunchedEffect

        restored = true
        engine.loadPlaylist(queue, index, autoPlay = false)
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
            songs = songs,
            onSongClick = { list, index ->
                engine.loadPlaylist(list, index)
                engine.play()
                onPillClick()
            })
        NowPlayingPill(
            title = engine.title,
            artist = engine.artist,
            isPlaying = engine.isPlaying,
            albumArt = engine.artwork,
            albumArtColor = artworkColor,
            onPlayPause = { if (engine.isPlaying) engine.pause() else engine.play() },
            onClick = onPillClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

// The layout of the home screen: the title, the search button, the three tabs
// (Songs, Playlists, Folders) and whichever tab is open. It only shows things,
// the work happens in HomeScreen above.
@Composable
fun HomeScreenUI(
    hasFolder: Boolean,
    onAddFolder: () -> Unit,
    folderSources: Set<String>,
    onRemoveFolder: (String) -> Unit,
    songs: List<Song>?,
    onSongClick: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
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

            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    tint = MaterialTheme.colorScheme.onBackground,
                    contentDescription = "Search",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(8.dp))


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
        } else {
            var tab by rememberSaveable { mutableIntStateOf(0) }
            val tabs = listOf("Songs", "Playlists", "Folders")

            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,   // let your background show through
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
                0 -> songUI(songs, { i -> onSongClick(songs ?: emptyList(), i) }, Modifier.weight(1f))
                1 -> playlistUI(Modifier.weight(1f))
                2 -> folderUI(
                    songs,
                    folderSources,
                    onSongClick,
                    onAddFolder,
                    onRemoveFolder,
                    Modifier.weight(1f)
                )
            }
        }
    }
}

// The Songs tab: a scrolling list of songs. Each row shows the cover, title
// and artist. Tapping a row plays that song.
@Composable
fun songUI(
    songs: List<Song>?,
    onSongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 72.dp)
            .clip(RoundedCornerShape(topEnd = 25.dp, topStart = 25.dp))
            .background(color = MaterialTheme.colorScheme.surface),
        contentPadding = PaddingValues(horizontal = 10.dp)
    ) {
        itemsIndexed(
            items = songs ?: emptyList(),
            key = { _, song -> song.uri.toString() }   // stable keys = cheaper updates
        ) { index, song ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp)
                    .padding(5.dp)
                    .border(width = 3.dp, color = MaterialTheme.colorScheme.outline)
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

                IconButton(onClick = {}) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "play",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

// The Playlists tab. Empty for now, to be built later.
@Composable
fun playlistUI(modifier: Modifier = Modifier) {
    // yours to fill in
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
                        .border(3.dp, MaterialTheme.colorScheme.outline)
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
                        .border(3.dp, MaterialTheme.colorScheme.outline)
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
                modifier = Modifier.weight(1f)
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
    albumArtColor: Color?
) {

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(color = albumArtColor ?: MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
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
            onAddFolder = {},
            folderSources = emptySet(),
            onRemoveFolder = {},
            songs = null,
            onSongClick = { _, _ -> }
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
            } catch (e: Exception) {
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
private fun folderName(source: String): String {
    val last = source.toUri().lastPathSegment ?: return "Folder"
    return last.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }
}