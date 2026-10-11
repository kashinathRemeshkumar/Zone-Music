package com.zone

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class MusicEngine(context: Context) {

    // Order matters: appContext must be declared before anything that uses it
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var neighborJob: Job? = null
    private var songs: List<Song> = emptyList()

    //for mediaservice
    val sessionPlayer: Player get() = player

    private val player = ExoPlayer.Builder(appContext)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    // ---- State exposed to the UI ----
    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var artwork by mutableStateOf<ImageBitmap?>(null)
        private set
    var duration by mutableStateOf(0L)
        private set
    var position by mutableStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isShuffle by mutableStateOf(false)
        private set
    var repeatState by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set

    var nextArtwork by mutableStateOf<ImageBitmap?>(null)
        private set
    var previousArtwork by mutableStateOf<ImageBitmap?>(null)
        private set
    var hasNext by mutableStateOf(false)
        private set
    var hasPrevious by mutableStateOf(false)
        private set
    var mediaIndex by mutableIntStateOf(0)
        private set
    var queue by mutableStateOf<List<Song>>(emptyList())
        private set
    var queueOrder by mutableStateOf<List<Int>>(emptyList())
        private set

    init {
        player.addListener(object : Player.Listener {

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                title = mediaMetadata.title?.toString() ?: "Unknown"
                artist = mediaMetadata.artist?.toString() ?: "Unknown"
                album = mediaMetadata.albumTitle?.toString() ?: "Unknown"

                artwork = mediaMetadata.artworkData?.let { bytes ->
                    BitmapFactory
                        .decodeByteArray(bytes, 0, bytes.size)
                        ?.asImageBitmap()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaIndex = player.currentMediaItemIndex
                // The player stays in READY when a song auto-advances, so refresh the
                // duration here too (0 until READY fills in the real value).
                duration = player.duration.coerceAtLeast(0L)
                refreshNeighbors()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                refreshQueueOrder()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                refreshQueueOrder()
                refreshNeighbors()
            }
        })
    }

    // ---- Playback controls ----

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun playAt(index: Int) {
        player.seekTo(index, 0L)
        player.play()
    }

    /**
     * Loads a playlist and starts at [startIndex].
     * Pass autoPlay = false to load it without starting playback (useful for restoring on launch).
     */
    fun loadPlaylist(songs: List<Song>, startIndex: Int, autoPlay: Boolean = true) {
        this.songs = songs
        queue = songs
        mediaIndex = startIndex        // keeps the queue and the index in sync

        // Pass the start index here so song 0 never becomes the current item
        player.setMediaItems(
            songs.map {
                MediaItem.Builder()
                    .setUri(it.uri)
                    .setMediaId(it.uri.toString())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(it.title)
                            .setArtist(it.artist)
                            .setAlbumTitle(it.album)
                            .build()
                    ).build()
            },
            startIndex, 0L
        )
        // If shuffle is already on, keep the chosen song first instead of a random spot
        if (isShuffle) player.setShuffleOrder(shuffleOrderStartingAt(startIndex, songs.size))
        player.prepare()
        if (autoPlay) player.play()
    }
    /** Lets the UI follow a queue that the session (e.g. Android Auto) loaded into the player. */
    fun syncQueue(list: List<Song>) {
        songs = list
        queue = list
    }

    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    /** Restarts the song if more than 3 seconds have played, otherwise goes to the previous track. */
    fun previous() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    /** Always goes to the previous track (used by the swipe animation). */
    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        isShuffle = enabled
        if (enabled && player.mediaItemCount > 0) {
            // Set the order first, so the playing song leads the queue, then turn shuffle on
            player.setShuffleOrder(
                shuffleOrderStartingAt(player.currentMediaItemIndex, player.mediaItemCount)
            )
        }
        player.shuffleModeEnabled = enabled
        refreshNeighbors()
    }

    fun setRepeatMode(mode: Int) {
        repeatState = mode
        player.repeatMode = mode
        refreshNeighbors()
    }

    fun updatePosition() {
        position = player.currentPosition
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        position = ms
    }

    fun release() {
        scope.cancel()
        player.release()
    }

    // ---- Internals ----

    private fun shuffleOrderStartingAt(first: Int, count: Int): ShuffleOrder {
        val rest = (0 until count).filter { it != first }.shuffled()
        val order = (listOf(first) + rest).toIntArray()
        return ShuffleOrder.DefaultShuffleOrder(order, System.currentTimeMillis())
    }

    /** Indices into [queue], in the order they will actually play. */
    private fun refreshQueueOrder() {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) {
            queueOrder = emptyList()
            return
        }
        val shuffle = player.shuffleModeEnabled
        val order = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET) {
            order.add(i)
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        queueOrder = order
    }

    private fun refreshNeighbors() {
        val nextIdx = player.nextMediaItemIndex       // respects shuffle and repeat
        val prevIdx = player.previousMediaItemIndex
        hasNext = player.hasNextMediaItem()
        hasPrevious = player.hasPreviousMediaItem()

        neighborJob?.cancel()
        neighborJob = scope.launch {
            val n = loadArt(nextIdx)
            val p = loadArt(prevIdx)
            withContext(Dispatchers.Main) {
                nextArtwork = n
                previousArtwork = p
            }
        }
    }

    private fun loadArt(index: Int): ImageBitmap? {
        val song = songs.getOrNull(index) ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, song.uri)
            retriever.embeddedPicture?.let {
                BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

}