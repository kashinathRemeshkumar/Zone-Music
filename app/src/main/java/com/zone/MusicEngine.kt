package com.zone

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
class MusicEngine(context: Context) {

    // Order matters: appContext must be declared before anything that uses it
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)   // for the sleep timer
    private var sleepJob: Job? = null

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

    /** When the sleep timer fires, in SystemClock.elapsedRealtime() terms. 0 = timer is off. */
    var sleepEndsAt by mutableLongStateOf(0L)
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
    fun loadPlaylist(
        songs: List<Song>,
        startIndex: Int,
        autoPlay: Boolean = true,
        startPosition: Long = 0L      // used to resume where the user left off
    ) {
        this.songs = songs
        queue = songs
        lastRemoved = null
        mediaIndex = startIndex        // keeps the queue and the index in sync
        position = startPosition

        // Pass the start index here so song 0 never becomes the current item
        player.setMediaItems(
            songs.map { toMediaItem(it) },
            startIndex, startPosition
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

    // ---- Queue editing ----

    /** What the last "remove from queue" took out, so it can be undone. */
    private class RemovedSong(val index: Int, val song: Song, val orderPos: Int)
    private var lastRemoved: RemovedSong? = null

    /** Inserts [song] right after the current one (also in shuffle order). */
    fun playNext(song: Song) = insertSong(song, afterCurrent = true)

    /** Appends [song] to the end of the queue (also at the end of shuffle order). */
    fun addToQueue(song: Song) = insertSong(song, afterCurrent = false)

    /** Removes the song at [index] (an index into [queue]). The playing song can't be removed. */
    fun removeFromQueue(index: Int) {
        if (index !in queue.indices || index == player.currentMediaItemIndex) return
        if (queue.size != player.mediaItemCount) return
        lastRemoved = RemovedSong(index, queue[index], playOrder().indexOf(index))
        player.removeMediaItem(index)   // ExoPlayer keeps the shuffle order of the others
        queue = queue.toMutableList().apply { removeAt(index) }
        songs = queue
        mediaIndex = player.currentMediaItemIndex   // shifts down if we removed above it
        refreshQueueOrder()
        refreshNeighbors()
    }

    /** Puts back the song removed last, at the same place (also in shuffle order). */
    fun undoRemove() {
        val r = lastRemoved ?: return
        lastRemoved = null
        if (queue.isEmpty() || queue.size != player.mediaItemCount) return
        insertAt(r.song, r.index.coerceAtMost(queue.size)) { r.orderPos }
    }

    /** Moves the song at [index] so it plays right after the current one. */
    fun moveToNext(index: Int) {
        val current = player.currentMediaItemIndex
        if (index !in queue.indices || index == current) return

        val shuffle = player.shuffleModeEnabled
        val oldOrder = if (shuffle) playOrder() else emptyList()

        // Index the song will have after the move
        val target = if (index < current) current else current + 1
        player.moveMediaItem(index, target)
        queue = queue.toMutableList().apply { add(target, removeAt(index)) }
        songs = queue
        mediaIndex = player.currentMediaItemIndex

        if (shuffle) {
            // ExoPlayer drops a moved item at a random shuffle position, so rebuild the order
            fun remap(old: Int): Int {
                val removed = if (old > index) old - 1 else old
                return if (removed >= target) removed + 1 else removed
            }
            val rest = oldOrder.filter { it != index }.map { remap(it) }.toMutableList()
            rest.add(rest.indexOf(mediaIndex) + 1, target)
            player.setShuffleOrder(
                ShuffleOrder.DefaultShuffleOrder(rest.toIntArray(), System.nanoTime())
            )
        }
        refreshQueueOrder()
        refreshNeighbors()
    }

    /** Call after a song's file was deleted, so it doesn't stay in the queue. */
    fun songDeleted(uri: Uri) {
        if (queue.isEmpty() || queue.size != player.mediaItemCount) return
        for (i in queue.indices.reversed()) {          // backwards, so indexes stay valid
            if (queue[i].uri != uri) continue
            if (queue.size == 1) {
                clearQueue()
                return
            }
            player.removeMediaItem(i)                   // if it was playing, ExoPlayer moves on
            queue = queue.toMutableList().apply { removeAt(i) }
        }
        songs = queue
        lastRemoved = null
        mediaIndex = player.currentMediaItemIndex
        refreshQueueOrder()
        refreshNeighbors()
    }

    private fun clearQueue() {
        player.stop()
        player.clearMediaItems()
        queue = emptyList()
        songs = queue
        queueOrder = emptyList()
        lastRemoved = null
        mediaIndex = 0
        title = ""
        artist = ""
        album = ""
        artwork = null
        nextArtwork = null
        previousArtwork = null
        hasNext = false
        hasPrevious = false
        duration = 0L
        position = 0L
    }

    private fun insertSong(song: Song, afterCurrent: Boolean) {
        // Nothing loaded yet: load just this song, paused, so the pill and player show it
        if (player.mediaItemCount == 0) {
            loadPlaylist(listOf(song), 0, autoPlay = false)
            return
        }
        // The UI queue and the player disagree (e.g. an unknown item came in from Auto)
        if (queue.size != player.mediaItemCount) return

        val current = player.currentMediaItemIndex
        val at = if (afterCurrent) current + 1 else player.mediaItemCount
        insertAt(song, at) { order ->
            if (afterCurrent) order.indexOf(current) + 1 else order.size
        }
    }

    /**
     * Inserts [song] at [at] in the queue. [shufflePos] says where it goes in the shuffle
     * order (it gets the order after the shift), because ExoPlayer would pick a random spot.
     */
    private fun insertAt(song: Song, at: Int, shufflePos: (List<Int>) -> Int) {
        val shuffle = player.shuffleModeEnabled
        val oldOrder = if (shuffle) playOrder() else emptyList()

        player.addMediaItem(at, toMediaItem(song))
        queue = queue.toMutableList().apply { add(at, song) }
        songs = queue

        if (shuffle) {
            val order = oldOrder.map { if (it >= at) it + 1 else it }.toMutableList()
            order.add(shufflePos(order).coerceIn(0, order.size), at)
            player.setShuffleOrder(
                ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime())
            )
        }
        mediaIndex = player.currentMediaItemIndex   // moves up by one if we inserted above it
        refreshQueueOrder()
        refreshNeighbors()
    }

    // ---- Sleep timer ----

    fun startSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val ms = minutes * 60_000L
        sleepEndsAt = SystemClock.elapsedRealtime() + ms
        sleepJob = mainScope.launch {
            delay(ms)
            player.pause()
            sleepEndsAt = 0L
        }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        sleepEndsAt = 0L
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

    /** The live position, for saving it (the [position] state only updates when asked to). */
    fun currentPositionMs(): Long = player.currentPosition

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        position = ms
    }

    fun release() {
        mainScope.cancel()
        scope.cancel()
        player.release()
    }

    // ---- Internals ----

    private fun shuffleOrderStartingAt(first: Int, count: Int): ShuffleOrder {
        val rest = (0 until count).filter { it != first }.shuffled()
        val order = (listOf(first) + rest).toIntArray()
        return ShuffleOrder.DefaultShuffleOrder(order, System.currentTimeMillis())
    }

    private fun toMediaItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.uri.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build()
            ).build()

    /** Indices into [queue], in the order they will actually play. */
    private fun playOrder(): List<Int> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val shuffle = player.shuffleModeEnabled
        val order = ArrayList<Int>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(shuffle)
        while (i != C.INDEX_UNSET) {
            order.add(i)
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle)
        }
        return order
    }

    private fun refreshQueueOrder() {
        queueOrder = playOrder()
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