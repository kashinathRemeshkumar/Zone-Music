package com.zone

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "zone_settings")

object SettingsKeys {
    val folderUris = stringSetPreferencesKey("folder_uris")
    val lastPlayedUri = stringPreferencesKey("last_played_uri")
    val lastPosition = longPreferencesKey("last_position")
    val shuffle = booleanPreferencesKey("shuffle")
    val repeatMode = stringPreferencesKey("repeat_mode")
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

    suspend fun saveLastPlayedUri(uri: Uri) {
        context.dataStore.edit { it[SettingsKeys.lastPlayedUri] = uri.toString() }
    }

    suspend fun saveLastPosition(position: Long) {
        context.dataStore.edit { it[SettingsKeys.lastPosition] = position }
    }

    suspend fun saveShuffle(shuffle: Boolean) {
        context.dataStore.edit { it[SettingsKeys.shuffle] = shuffle }
    }

    suspend fun saveRepeatMode(repeat: String) {
        context.dataStore.edit { it[SettingsKeys.repeatMode] = repeat }
    }

    // READ
    val folderUris: Flow<Set<String>> =
        context.dataStore.data.map { it[SettingsKeys.folderUris] ?: emptySet() }

    val lastPlayedUri: Flow<Uri?> =
        context.dataStore.data.map { it[SettingsKeys.lastPlayedUri]?.toUri() }

    val lastPosition: Flow<Long?> =
        context.dataStore.data.map { it[SettingsKeys.lastPosition] }

    val shuffle: Flow<Boolean?> =
        context.dataStore.data.map { it[SettingsKeys.shuffle] }

    val repeatMode: Flow<String?> =
        context.dataStore.data.map { it[SettingsKeys.repeatMode] }
}