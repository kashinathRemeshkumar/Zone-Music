package com.zone

import android.content.Context
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SongCache(context: Context) {

    private val file = File(context.filesDir, "songs.json")

    fun save(songs: List<Song>) {
        val array = JSONArray()
        for (song in songs) {
            array.put(
                JSONObject()
                    .put("fileName", song.fileName)
                    .put("uri", song.uri.toString())
                    .put("title", song.title)
                    .put("artist", song.artist)
                    .put("album", song.album)
                    .put("source", song.source)
            )
        }
        file.writeText(array.toString())
    }

    fun load(): List<Song> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Song(
                    fileName = o.getString("fileName"),
                    uri = o.getString("uri").toUri(),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    album = o.getString("album"),
                    source = o.getString("source")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}