package com.zone

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class Song(
    val fileName: String,
    val uri: Uri,
    val title: String = fileName,
    val artist: String = "Unknown",
    val album: String = "Unknown",
    val source: String = ""   // the folder uri this song came from
)

class SongScanner {
    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "wav", "ogg", "opus", "aac", "wma"
    )

    fun scanFolder(context: Context, folderUri: Uri): List<Song> {
        val root = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return scanDocument(root).map { it.copy(source = folderUri.toString()) }
    }

    private fun scanDocument(folder: DocumentFile): List<Song> {
        val songs = mutableListOf<Song>()

        for (file in folder.listFiles()) {
            if (file.isDirectory) {
                songs.addAll(scanDocument(file))
            } else {
                val extension = file.name
                    ?.substringAfterLast('.', "")
                    ?.lowercase()

                val isAudio = file.type?.startsWith("audio/") == true ||
                        extension in audioExtensions

                if (isAudio) {
                    songs.add(Song(file.name ?: "Unknown", file.uri))
                }
            }
        }
        return songs
    }

    // Next step: call this on Dispatchers.IO for each song
    fun readTags(context: Context, song: Song): Song {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, song.uri)
            song.copy(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: song.fileName,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: "Unknown",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?: "Unknown"
            )
        } catch (e: Exception) {
            android.util.Log.e("Zone", "readTags failed for ${song.fileName}", e)
            song
        } finally {
            retriever.release()
        }
    }
}