package com.zone

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID


const val LIKED_ID="Liked_Songs"

data class Playlist(
    val id: String,
    val name: String,
    val uris: List<Uri>
)


class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlist.json")
    private val _playlists = MutableStateFlow(
        load().ifEmpty { listOf(Playlist(LIKED_ID, "Liked Songs", emptyList())) }
    )
    val playlists: StateFlow<List<Playlist>> = _playlists

    private fun save(playlists: List<Playlist>){
        val array= JSONArray()
        for(p in playlists){
            array.put(
                JSONObject()
                    .put("id",p.id)
                    .put("name",p.name)
                    .put("uris", JSONArray(p.uris.map { it.toString() }))
            )
        }
        file.writeText(array.toString())
    }

    private fun load(): List<Playlist> {
        if (!file.exists()) return emptyList()

        return try{
            val array = JSONArray(file.readText())
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                val u = o.getJSONArray("uris")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    uris = List(u.length()) { u.getString(it).toUri() }
                )
            }
        }
        catch(e: Exception){
            emptyList()
        }


    }

    private fun update(change: (List<Playlist>) -> List<Playlist>) {
        val oldList = _playlists.value      // 1. look at the current list
        val newList = change(oldList)       // 2. make the changed version
        _playlists.value = newList          // 3. put it in the box (screens refresh)
        save(newList)                       // 4. write it to the file
    }


    fun createPlaylist(name: String) {
        update { oldList -> oldList + Playlist(UUID.randomUUID().toString(), name, emptyList()) }
    }

    fun toggleSong(playlistId: String, uri: Uri) {
        update { oldList ->
            oldList.map { p ->
                if (p.id != playlistId) {
                    p
                } else if (uri in p.uris) {
                    p.copy(uris = p.uris - uri)
                } else {
                    p.copy(uris = p.uris + uri)
                }
            }
        }
    }

    fun deletePlaylist(id: String) {
        if (id == LIKED_ID) return
        update { oldList -> oldList.filter { it.id != id } }
    }

    fun songsOf(playlistId: String, all: List<Song>): List<Song> {
        val playlist = _playlists.value.firstOrNull { it.id == playlistId }
        if (playlist == null) return emptyList()

        val result = mutableListOf<Song>()
        for (uri in playlist.uris) {
            val song = all.firstOrNull { it.uri == uri }
            if (song != null) {
                result.add(song)
            }
        }
        return result
    }

}