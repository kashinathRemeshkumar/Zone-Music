package com.zone

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

data class Song(
    val name: String,
    val uri: Uri
)




class SongScanner {
    private val audioExtensions = setOf(
        "mp3", "flac", "m4a", "wav", "ogg", "opus", "aac", "wma"
    )

    fun scanFolder(context: Context,uri: Uri): List<Song>
    {
        val folder = DocumentFile.fromTreeUri(context, uri)
        val songs=mutableListOf<Song>()
        val files=folder?.listFiles() ?:emptyArray()


        for(file in files){
            val extension = file.name
                ?.substringAfterLast('.', "")
                ?.lowercase()

            val isAudio = file.type?.startsWith("audio/") == true ||
                    extension in audioExtensions

            if (isAudio){
                val song= Song(file.name.toString(),file.uri)
                songs.add(song)
            }
            if (file.isDirectory) {
                songs.addAll(scanFolder(context, file.uri))
            }
        }
        return songs
    }
}