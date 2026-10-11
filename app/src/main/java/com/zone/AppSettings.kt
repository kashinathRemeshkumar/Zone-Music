package com.zone

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "zone_settings")

object SettingsKeys {
    val folderUris = stringSetPreferencesKey("folder_uris")
    val lastPlayedUri = stringPreferencesKey("last_played_uri")
    val lastPosition = longPreferencesKey("last_position")
    val shuffle = booleanPreferencesKey("shuffle")
    val repeatMode = intPreferencesKey("repeat_mode")
    val queueUris = stringPreferencesKey("queue_uris")   // queue in play order, one uri per line
}

class AppSettings(private val context: Context) {

    // WRITE
    suspend fun addFolder(uri: Uri) {
        context.dataStore.edit { settings ->
            val current = settings[SettingsKeys.folderUris] ?: emptySet()
            settings[SettingsKeys.folderUris] = current + uri.toString()
        }
    }

    suspend fun removeFolder(uri: Uri) {
        context.dataStore.edit { settings ->
            val current = settings[SettingsKeys.folderUris] ?: emptySet()
            settings[SettingsKeys.folderUris] = current - uri.toString()
        }
    }

    /** Saves the queue, the current song, shuffle and repeat together, in one atomic write. */
    suspend fun saveSession(queue: List<Uri>, current: Uri, shuffle: Boolean, repeat: Int) {
        context.dataStore.edit {
            it[SettingsKeys.queueUris] = queue.joinToString("\n")
            it[SettingsKeys.lastPlayedUri] = current.toString()
            it[SettingsKeys.shuffle] = shuffle
            it[SettingsKeys.repeatMode] = repeat
        }
    }

    suspend fun saveLastPosition(position: Long) {
        context.dataStore.edit { it[SettingsKeys.lastPosition] = position }
    }

    // READ
    val folderUris: Flow<Set<String>> =
        context.dataStore.data.map { it[SettingsKeys.folderUris] ?: emptySet() }

    val queueUris: Flow<List<Uri>> =
        context.dataStore.data.map { prefs ->
            prefs[SettingsKeys.queueUris]
                ?.split("\n")
                ?.filter { it.isNotEmpty() }
                ?.map { it.toUri() }
                ?: emptyList()
        }

    val lastPlayedUri: Flow<Uri?> =
        context.dataStore.data.map { it[SettingsKeys.lastPlayedUri]?.toUri() }

    val lastPosition: Flow<Long?> =
        context.dataStore.data.map { it[SettingsKeys.lastPosition] }

    val shuffle: Flow<Boolean?> =
        context.dataStore.data.map { it[SettingsKeys.shuffle] }

    val repeatMode: Flow<Int?> =
        context.dataStore.data.map { it[SettingsKeys.repeatMode] }
}