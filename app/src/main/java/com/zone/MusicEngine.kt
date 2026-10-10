package com.zone

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.C
import androidx.media3.common.Timeline

class MusicEngine(context: Context) {

    private val player = ExoPlayer.Builder(context).build()

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

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var neighborJob: Job? = null
    private var songs: List<Song> = emptyList()

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

    fun play() {
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun load(uri: Uri) {
        val mediaItem = MediaItem.fromUri(uri)
        player.setMediaItem(mediaItem)
        player.prepare()
    }
    fun playAt(index: Int) {
        player.seekTo(index, 0L)
        player.play()
    }

    fun loadPlaylist(songs: List<Song>, startIndex: Int) {
        this.songs = songs
        queue=songs
        val items = songs.map { MediaItem.fromUri(it.uri) }
        player.setMediaItems(items)
        player.seekTo(startIndex,0L)
        player.prepare()
        player.play()
    }
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
    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun previous() {
        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        isShuffle = enabled
        player.shuffleModeEnabled = enabled
        refreshNeighbors()
    }
    fun skipToPrevious() {
        if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
    }

    fun setRepeatMode(mode: Int) {
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
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}