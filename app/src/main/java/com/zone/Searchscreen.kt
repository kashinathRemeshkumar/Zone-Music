package com.zone

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

// ---------- Matching ----------

private val accentMarks = Regex("\\p{Mn}+")

// Lowercase and strip accents, so "beyonce" finds "Beyoncé"
fun foldText(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(accentMarks, "").lowercase()

// A song plus its pre-folded search text, built once per library change.
// Folder and playlist names are included, so "workout" finds the songs in that playlist.
private class Indexed(val song: Song, folder: String, playlistNames: String) {
    val title = foldText(song.title)
    val all = foldText(
        "${song.title} ${song.artist} ${song.album} ${song.fileName} $folder $playlistNames"
    )
}

// ---------- UI ----------

// The text field that replaces the "Zone Music" title while searching.
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }   // open the keyboard right away

    Row(
        modifier
            .fillMaxWidth()
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Close search",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search songs, artists, albums, folders, playlists",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 18.sp
                        )
                    }
                    inner()
                }
            }
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

// Matches every word of the query against title, artist, album and file name.
// Results reuse songUI, so the three-dot menu works exactly like on the Songs tab.
@Composable
fun SearchResults(
    query: String,
    songs: List<Song>?,
    onSongClick: (List<Song>, Int) -> Unit,
    playlists: List<Playlist>,
    onToggleSong: (String, Uri) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val playlistNamesByUri = remember(playlists) {
        val map = HashMap<Uri, String>()
        for (p in playlists) for (u in p.uris) map[u] = (map[u] ?: "") + " " + p.name
        map
    }
    val index = remember(songs, playlistNamesByUri) {
        songs.orEmpty().map {
            Indexed(it, folderName(it.source), playlistNamesByUri[it.uri] ?: "")
        }
    }

    val results = remember(query, index) {
        val tokens = foldText(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            emptyList()
        } else {
            val first = tokens.first()
            index
                .filter { entry -> tokens.all { it in entry.all } }
                // Title matches first; the sort is stable, so each group stays A-Z
                .sortedBy {
                    when {
                        it.title.startsWith(first) -> 0
                        first in it.title -> 1
                        else -> 2
                    }
                }
                .map { it.song }
        }
    }

    when {
        query.isBlank() -> SearchHint("Search songs, artists, albums, folders or playlists", modifier)
        results.isEmpty() -> SearchHint("No results for \"${query.trim()}\"", modifier)
        else -> songUI(
            songs = results,
            onSongClick = { i ->
                focusManager.clearFocus()   // hide the keyboard
                onSongClick(results, i)
            },
            modifier = modifier,
            playlists = playlists,
            onToggleSong = onToggleSong,
            onDeleteSong = onDeleteSong,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onPlayAll = { onPlayAll(results) },
            onShuffleAll = { onShuffleAll(results) }
        )
    }
}

@Composable
private fun SearchHint(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}