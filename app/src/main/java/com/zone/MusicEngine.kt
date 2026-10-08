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

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
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
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
    }

    fun updatePosition() {
        position = player.currentPosition
    }

    fun seekTo(ms: Long) {
        player.seekTo(ms)
        position = ms
    }

    fun release() {
        player.release()
    }
}