package com.zone


import android.content.Intent
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.zone.ui.theme.ZoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
fun HomeScreen(engine: MusicEngine, onPillClick: () -> Unit) {

    val context = LocalContext.current
    val settings = remember { AppSettings(context) }
    val cache = remember { SongCache(context) }
    val sources by settings.folderUris.collectAsState(initial = null)
    var songs by remember { mutableStateOf(emptyList<Song>()) }

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

    // Runs whenever the set of sources changes
    LaunchedEffect(sources) {
        val currentSources = sources ?: return@LaunchedEffect
        val cacheList = withContext(Dispatchers.IO) {
            cache.load()
        }

        val alreadyScanned = cacheList.map { it.source }.toSet()
        val newSources = currentSources - alreadyScanned

        val newSongs = withContext(Dispatchers.IO) {
            val scanned = newSources.flatMap { SongScanner().scanFolder(context, it.toUri()) }

            val tagged = scanned.map { SongScanner().readTags(context, it) }
            cache.save(cacheList.filter { it.source in currentSources } + tagged)
            tagged
        }

        songs = (cacheList.filter { it.source in currentSources } + newSongs)
            .distinctBy { it.uri }
    }

    Box(Modifier.fillMaxSize()
        .navigationBarsPadding()){
        HomeScreenUI(
            hasFolder = sources?.isNotEmpty() == true,
            onAddFolder = { launcher.launch(null) },
            songs = songs,
            onSongClick = { index ->
                val song = songs[index]
                engine.load(song.uri)
                engine.play()
                onPillClick()
            })
        NowPlayingPill(
            title = engine.title,
            artist = engine.artist,
            isPlaying = engine.isPlaying,
            albumArt=engine.artwork,
            onPlayPause = { if (engine.isPlaying) engine.pause() else engine.play() },
            onClick = onPillClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)



        )

    }

}

@Composable
fun HomeScreenUI(
    hasFolder: Boolean,
    onAddFolder: () -> Unit,
    songs: List<Song>?,
    onSongClick: (Int) -> Unit,
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
        Spacer(modifier = Modifier.size(25.dp))


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
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp, start = 10.dp, end = 10.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(color = MaterialTheme.colorScheme.surface)
                    ,
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                itemsIndexed(songs ?: emptyList()) { index, song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(85.dp)
                            .padding(5.dp)
                            .border(width = 3.dp, color = MaterialTheme.colorScheme.outline)
                            .clickable { onSongClick(index) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .size(55.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color = MaterialTheme.colorScheme.surfaceVariant)
                                .aspectRatio(1f)
                        ) {

                        }
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
    }
}

@Composable
fun NowPlayingPill(
    title: String,
    artist: String,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    albumArt: ImageBitmap?
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .offset(5.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
        ){
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
            albumArt = null
        )
    }
}


@Composable
@Preview(showBackground = true)
fun HomeScreenPreview() {
    ZoneTheme {
        HomeScreenUI(hasFolder = true, onAddFolder = {}, songs = null, onSongClick = {})
    }
}