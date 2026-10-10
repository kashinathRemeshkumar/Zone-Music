package com.zone

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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

    suspend fun scanFolder(context: Context, folderUri: Uri): List<Song> {
        val resolver = context.contentResolver
        val source = folderUri.toString()
        val result = mutableListOf<Song>()
        val pending = ArrayDeque<String>()
        pending.add(DocumentsContract.getTreeDocumentId(folderUri))

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()   // lets a new scan cancel this one
            val dirId = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, dirId)

            resolver.query(childrenUri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2)

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.add(id)
                    } else {
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (mime?.startsWith("audio/") == true || ext in audioExtensions) {
                            result.add(
                                Song(
                                    fileName = name,
                                    uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id),
                                    source = source
                                )
                            )
                        }
                    }
                }
            }
        }
        return result
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