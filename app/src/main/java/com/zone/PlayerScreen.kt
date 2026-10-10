package com.zone

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import com.zone.ui.theme.ZoneTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.graphics.graphicsLayer



enum class LoopMode { OFF, ONE, ALL }

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun PlayerScreen(modifier: Modifier = Modifier, engine: MusicEngine) {
    var loopState by remember { mutableStateOf(LoopMode.OFF) }
    var artworkColor by remember { mutableStateOf(Color.Unspecified) }


    LaunchedEffect(engine.isPlaying) {
        while (engine.isPlaying) {
            engine.updatePosition()
            delay(500)
        }
    }

    LaunchedEffect(engine.artwork) {
        val art = engine.artwork
        if (art != null) {
            val bitmap = art.asAndroidBitmap()
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).generate()
            }
            artworkColor = Color(palette.getVibrantColor(Color(0xFF3F51B5).toArgb()))
        }
    }

    PlayerContent(
        title = engine.title,
        artist = engine.artist,
        artwork = engine.artwork,
        artworkColor = artworkColor,
        isPlaying = engine.isPlaying,
        position = engine.position,
        duration = engine.duration,
        isShuffle = engine.isShuffle,
        loopMode = loopState,
        queueOrder = engine.queueOrder,
        queue = engine.queue,
        onQueueItemClick = { engine.playAt(it) },
        nextArtwork = engine.nextArtwork,
        previousArtwork = engine.previousArtwork,
        hasNext = engine.hasNext,
        hasPrevious = engine.hasPrevious,
        mediaIndex = engine.mediaIndex,
        onPlayPause = { if (engine.isPlaying) engine.pause() else engine.play() },
        onPrevious = { engine.previous() },               // restarts song if > 3s played
        onPreviousTrack = { engine.skipToPrevious() },    // always goes to the previous track
        onNext = { engine.next() },
        onSeek = { engine.seekTo(it) },
        onShuffleClick = { engine.setShuffleEnabled(!engine.isShuffle) },
        onLoopClick = {
            loopState = when (loopState) {
                LoopMode.OFF -> LoopMode.ONE
                LoopMode.ONE -> LoopMode.ALL
                LoopMode.ALL -> LoopMode.OFF
            }
            engine.setRepeatMode(
                when (loopState) {
                    LoopMode.OFF -> Player.REPEAT_MODE_OFF
                    LoopMode.ONE -> Player.REPEAT_MODE_ONE
                    LoopMode.ALL -> Player.REPEAT_MODE_ALL
                }
            )
        },
        modifier = modifier
    )
}

/**
 * The drawing-only part. No engine in here, so it can be previewed
 * with fake values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerContent(
    title: String,
    artist: String,
    artwork: ImageBitmap?,
    artworkColor: Color,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isShuffle: Boolean,
    loopMode: LoopMode,
    queueOrder: List<Int> = emptyList(),
    queue: List<Song> = emptyList(),
    onQueueItemClick: (Int) -> Unit = {},
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleClick: () -> Unit,
    onLoopClick: () -> Unit,
    modifier: Modifier = Modifier,
    nextArtwork: ImageBitmap? = null,
    previousArtwork: ImageBitmap? = null,
    hasNext: Boolean = true,
    hasPrevious: Boolean = true,
    mediaIndex: Int = 0
) {
    val maxPosition = duration.coerceAtLeast(1L)
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }

    // ---- Album art carousel state ----
    val artOffsetX = remember { Animatable(0f) }
    val artScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val artWidthPx = with(density) { 300.dp.toPx() }
    val step = artWidthPx + with(density) { 70.dp.toPx() }   // art width + gap
    var pendingReset by remember { mutableStateOf(false) }


    // Once the engine reports the new track, put the strip back at 0.
    // The centre card now holds the new art, so there is no visible change.
    LaunchedEffect(mediaIndex) {
        if (pendingReset) {
            artOffsetX.snapTo(0f)
            pendingReset = false
        }
    }
    // Fallback in case the index never changes (e.g. a one-song playlist)
    LaunchedEffect(pendingReset) {
        if (pendingReset) {
            delay(500)
            artOffsetX.snapTo(0f)
            pendingReset = false
        }
    }

    fun swipeNext() {
        if (!hasNext || artOffsetX.isRunning || pendingReset) return
        artScope.launch {
            artOffsetX.animateTo(-step, tween(200))   // next art slides into the centre
            pendingReset = true
            onNext()
        }
    }

    fun swipePrevious() {
        if (!hasPrevious || artOffsetX.isRunning || pendingReset) return
        artScope.launch {
            artOffsetX.animateTo(step, tween(200))
            pendingReset = true
            onPreviousTrack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {}   // swallow touches so the home screen behind doesn't react
    ) {

        Crossfade(targetState = artwork, animationSpec = tween(800)) { art ->
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp)
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Text(
                modifier = Modifier.padding(top = 12.dp, bottom = 30.dp),
                text = "Now Playing",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                fontSize = 18.sp
            )

            // Fixed box: catches the horizontal drag. The cards inside move.
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            if (pendingReset) return@rememberDraggableState
                            artScope.launch {
                                val min = if (hasNext) -step else 0f
                                val max = if (hasPrevious) step else 0f
                                artOffsetX.snapTo((artOffsetX.value + delta).coerceIn(min, max))
                            }
                        },
                        onDragStopped = { velocity ->
                            val threshold = artWidthPx * 0.3f
                            when {
                                artOffsetX.value < -threshold || velocity < -1000f -> swipeNext()
                                artOffsetX.value > threshold || velocity > 1000f -> swipePrevious()
                                else -> artScope.launch {
                                    artOffsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                    )
                                }
                            }
                        }
                    )
            ) {
                if (hasPrevious) {
                    ArtCard(
                        previousArtwork,
                        Modifier.offset { IntOffset((artOffsetX.value - step).roundToInt(), 0) }
                    )
                }
                ArtCard(
                    artwork,
                    Modifier.offset { IntOffset(artOffsetX.value.roundToInt(), 0) }
                )
                if (hasNext) {
                    ArtCard(
                        nextArtwork,
                        Modifier.offset { IntOffset((artOffsetX.value + step).roundToInt(), 0) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = title.ifEmpty { "Song Title" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Normal,
                color = Color.White,
                fontSize = 30.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2000
                    )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = artist.ifEmpty { "Unknown Artist" },
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Light,
                color = Color.White,
                fontSize = 18.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f)
            )

            Spacer(modifier = Modifier.height(30.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(position),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    fontSize = 15.sp
                )
                Text(
                    text = formatTime(maxPosition),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    fontSize = 15.sp
                )
            }

            Slider(
                value = if (isDragging) dragPosition else position.toFloat(),
                onValueChange = { dragPosition = it },
                onValueChangeFinished = { onSeek(dragPosition.toLong()) },
                valueRange = 0f..maxPosition.toFloat(),
                interactionSource = interactionSource,
                track = { sliderState ->
                    val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                    val fraction = if (range > 0f) {
                        ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                    } else 0f

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))                       // rounds BOTH ends of the whole bar
                            .background(Color.White.copy(alpha = 0.25f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))                   // rounds the end of the filled part
                                .background(artworkColor)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(0.9f),
                thumb = {
                    if (isDragging) {
                        Box(
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(color = artworkColor)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(0.dp)
                                .background(color = artworkColor)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(50.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PressableIconButton(
                    onClick = {
                        // Restart the song if > 3s in (no art animation), otherwise animate to previous
                        if (position > 3000L || !hasPrevious) onPrevious() else swipePrevious()
                    },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(72.dp)
                    )
                }

                PressableIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(72.dp)
                    )
                }

                PressableIconButton(
                    onClick = { swipeNext() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Track",
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PressableIconButton(
                    onClick = onShuffleClick,
                    modifier = Modifier.size(35.dp)
                ) {
                    if (isShuffle) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle on",
                            tint = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(35.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = "Shuffle off",
                            tint = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(35.dp)
                        )
                    }
                }

                PressableIconButton(
                    onClick = { showQueue = true },
                    modifier = Modifier.size(35.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.List,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(35.dp)
                    )
                }

                PressableIconButton(
                    onClick = onLoopClick,
                    modifier = Modifier.size(35.dp)
                ) {
                    when (loopMode) {
                        LoopMode.OFF -> Icon(
                            imageVector = Icons.Filled.Repeat,
                            contentDescription = "No repeat",
                            tint = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(35.dp)
                        )

                        LoopMode.ONE -> Icon(
                            imageVector = Icons.Filled.RepeatOne,
                            contentDescription = "Repeat one",
                            tint = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(35.dp)
                        )

                        LoopMode.ALL -> Icon(
                            imageVector = Icons.Filled.AllInclusive,
                            contentDescription = "Repeat all",
                            tint = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(35.dp)
                        )
                    }
                }
            }
        }
        if (showQueue) {
            ModalBottomSheet(
                onDismissRequest = { showQueue = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                QueueList(
                    queue = queue,
                    order = queueOrder.ifEmpty { queue.indices.toList() },
                    currentIndex = mediaIndex,
                    isShuffle = isShuffle,
                    onItemClick = onQueueItemClick
                )
            }
        }
    }
}

@Composable
private fun ArtCard(bitmap: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(300.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PlayerContentPreview() {
    ZoneTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            PlayerContent(
                title = "Song Title",
                artist = "Artist",
                artwork = null,
                artworkColor = Color.White,
                isPlaying = false,
                position = 30_000L,
                duration = 215_000L,
                isShuffle = true,
                loopMode = LoopMode.OFF,
                onPlayPause = {},
                onPrevious = {},
                onPreviousTrack = {},
                onNext = {},
                onSeek = {},
                onShuffleClick = {},
                onLoopClick = {}
            )
        }
    }
}
@Composable
private fun QueueList(
    queue: List<Song>,
    order: List<Int>,          // original indices in play order
    currentIndex: Int,         // original index of the playing song
    isShuffle: Boolean,
    onItemClick: (Int) -> Unit // receives the original index
) {
    val currentPos = order.indexOf(currentIndex).coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentPos - 1).coerceAtLeast(0)
    )

    // Follow the playing song when the track changes
    LaunchedEffect(currentPos) {
        listState.animateScrollToItem((currentPos - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (isShuffle) "Queue · ${order.size} songs · Shuffled"
            else "Queue · ${order.size} songs",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            items(
                items = order,
                key = { originalIndex -> queue.getOrNull(originalIndex)?.uri.toString() }
            ) { originalIndex ->
                val song = queue.getOrNull(originalIndex) ?: return@items
                val isCurrent = originalIndex == currentIndex

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable { onItemClick(originalIndex) }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SongArtwork(
                        uri = song.uri,
                        modifier = Modifier.size(48.dp)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = song.title,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
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
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Now playing",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PressableIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.8f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = (1f - (1f - scale) * 1.5f).coerceIn(0.5f, 1f)   // dims slightly while pressed
        }
    ) {
        content()
    }
}