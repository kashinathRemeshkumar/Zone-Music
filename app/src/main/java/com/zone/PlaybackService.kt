package com.zone

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.text.Collator

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null
    private var cachedSongs: List<Song>? = null
    private val collator = Collator.getInstance()

    private val engine: MusicEngine get() = (application as ZoneApp).engine

    override fun onCreate() {
        super.onCreate()

        val openApp = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        session = MediaLibrarySession.Builder(this, engine.sessionPlayer, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    // Keep playing when the app is swiped away; stop only if nothing is playing
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()    // session only, not the engine's player
        session = null
        super.onDestroy()
    }

    // ---------- Data ----------

    private fun loadSongs(): List<Song> =
        SongCache(applicationContext).load()
            .sortedWith { a, b -> collator.compare(a.title, b.title) }

    private fun songs(): List<Song> = cachedSongs ?: loadSongs().also { cachedSongs = it }

    private fun folderName(source: String): String {
        val last = Uri.parse(source).lastPathSegment ?: return "Folder"
        return last.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }
    }

    // ---------- MediaItem builders ----------

    private fun browsable(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            ).build()

    private fun songItem(song: Song): MediaItem =
        MediaItem.Builder()
            .setMediaId(song.uri.toString())
            .setUri(song.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            ).build()

    /** Turns id-only items from a controller into full items. Returns null for unknown ids. */
    private fun resolve(items: List<MediaItem>): List<Song>? {
        val all = songs()
        val byId = all.associateBy { it.uri.toString() }
        val result = items.map { byId[it.mediaId] ?: return null }
        return result
    }

    // ---------- Session callback ----------

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            cachedSongs = null   // pick up rescans each time Auto connects
            return Futures.immediateFuture(
                LibraryResult.ofItem(browsable("root", "Zone Music"), params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items: List<MediaItem> = when {
                parentId == "root" ->
                    listOf(browsable("songs", "Songs"), browsable("folders", "Folders"))
                parentId == "songs" ->
                    songs().map { songItem(it) }
                parentId == "folders" ->
                    songs().map { it.source }.filter { it.isNotEmpty() }.distinct()
                        .map { browsable("folder:$it", folderName(it)) }
                parentId.startsWith("folder:") -> {
                    val source = parentId.removePrefix("folder:")
                    songs().filter { it.source == source }.map { songItem(it) }
                }
                else -> emptyList()
            }
            val paged = items.drop(page * pageSize).take(pageSize)
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val song = songs().firstOrNull { it.uri.toString() == mediaId }
            return Futures.immediateFuture(
                if (song != null) LibraryResult.ofItem(songItem(song), null)
                else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            )
        }

        // "Add to queue" style requests
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = resolve(mediaItems)
                ?.map { songItem(it) }?.toMutableList()
                ?: mediaItems.map { it.buildUpon().setUri(it.mediaId).build() }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        // "Play this" requests. Needs Media3 1.3+.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val all = songs()
            val chosen = resolve(mediaItems)

            // One song tapped: queue the whole library starting at it
            if (chosen != null && chosen.size == 1) {
                val index = all.indexOfFirst { it.uri == chosen[0].uri }
                if (index >= 0) {
                    engine.syncQueue(all)
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(
                            all.map { songItem(it) }, index, 0L
                        )
                    )
                }
            }

            if (chosen != null) {
                engine.syncQueue(chosen)
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(
                        chosen.map { songItem(it) }, startIndex, startPositionMs
                    )
                )
            }

            // Unknown ids: let them through; the UI queue can't follow these
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    mediaItems.map { it.buildUpon().setUri(it.mediaId).build() },
                    startIndex, startPositionMs
                )
            )
        }
    }
}