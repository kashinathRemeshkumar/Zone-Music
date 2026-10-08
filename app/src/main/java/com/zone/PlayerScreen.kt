package com.zone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.zone.ui.theme.ZoneTheme
import kotlinx.coroutines.delay

enum class LoopMode { OFF, ONE, ALL }

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * The connected screen. It knows about the engine, keeps the position loop
 * running, and passes plain values and functions down to PlayerContent.
 */
@Composable
fun PlayerScreen(modifier: Modifier = Modifier, engine: MusicEngine) {
    var loopState by remember { mutableStateOf(LoopMode.OFF) }

    LaunchedEffect(engine.isPlaying) {
        while (engine.isPlaying) {
            engine.updatePosition()
            delay(500)
        }
    }

    PlayerContent(
        title = engine.title,
        artist = engine.artist,
        artwork = engine.artwork,
        isPlaying = engine.isPlaying,
        position = engine.position,
        duration = engine.duration,
        isShuffle = engine.isShuffle,
        loopMode = loopState,
        onPlayPause = { if (engine.isPlaying) engine.pause() else engine.play() },
        onPrevious = { engine.previous() },
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
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    isShuffle: Boolean,
    loopMode: LoopMode,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onShuffleClick: () -> Unit,
    onLoopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxPosition = duration.coerceAtLeast(1L)
    val interactionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    var dragPosition by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {

        Text(
            modifier = Modifier.padding(top = 20.dp, bottom = 30.dp),
            text = "Now Playing",
            color = Color.LightGray,
            textAlign = TextAlign.Center,
            fontSize = 18.sp
        )

        Box(
            modifier = Modifier
                .size(350.dp)
                .fillMaxWidth(1f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = "Album art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Text(
            text = title.ifEmpty { "Song Title" },
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Normal,
            color = Color.White,
            fontSize = 30.sp,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.ifEmpty { "Unknown Artist" },
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Light,
            color = Color.White,
            fontSize = 18.sp,
            maxLines = 1
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
                SliderDefaults.Track(
                    sliderState = sliderState,
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = {}
                )
            },
            modifier = Modifier.fillMaxWidth(0.9f),
            thumb = {
                if (isDragging) {
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                } else {
                    Box(modifier = Modifier.size(0.dp))
                }
            }
        )

        Spacer(modifier = Modifier.height(50.dp))

        Row(
            modifier = Modifier.fillMaxWidth(0.9f),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPrevious,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(72.dp)
                )
            }

            IconButton(
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

            IconButton(
                onClick = onNext,
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
            IconButton(
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

            IconButton(
                onClick = { /* Queue will be added later */ },
                modifier = Modifier.size(35.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.List,
                    contentDescription = "Queue",
                    tint = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(35.dp)
                )
            }

            IconButton(
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
                isPlaying = false,
                position = 30_000L,
                duration = 215_000L,
                isShuffle = true,
                loopMode = LoopMode.OFF,
                onPlayPause = {},
                onPrevious = {},
                onNext = {},
                onSeek = {},
                onShuffleClick = {},
                onLoopClick = {}
            )
        }
    }
}