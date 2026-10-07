package com.zone

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.core.net.toUri

val Context.dataStore by preferencesDataStore(name = "zone_settings")

object SettingsKeys {
    val folderUri = stringPreferencesKey("folder_uri")
    val lastPlayedUri = stringPreferencesKey("last_played_uri")
    val lastPosition = longPreferencesKey("last_position")
    val shuffle = booleanPreferencesKey("shuffle")
    val repeatMode = stringPreferencesKey("repeat_mode")
}


class AppSettings(private val context: Context){

    //WRITE
    suspend fun saveFolderUri(uri: Uri) {
        context.dataStore.edit { settings ->
            settings[SettingsKeys.folderUri] = uri.toString()
        }
    }
    suspend fun saveLastPlayedUri(uri: Uri) {
        context.dataStore.edit { settings ->
            settings[SettingsKeys.lastPlayedUri] = uri.toString()
        }
    }
    suspend fun saveLastPosition(position: Long) {
        context.dataStore.edit { settings ->
            settings[SettingsKeys.lastPosition] = position
        }
    }
    suspend fun saveShuffle(shuffle: Boolean) {
        context.dataStore.edit { settings ->
            settings[SettingsKeys.shuffle] = shuffle
        }
    }
    suspend fun saveRepeatMode(repeat:String) {
        context.dataStore.edit { settings ->
            settings[SettingsKeys.repeatMode] = repeat
        }
    }

    //READ
    val folderUri: Flow<Uri?> =
        context.dataStore.data.map { settings ->
            settings[SettingsKeys.folderUri]?.let { it.toUri() } }
    val lastPlayedUri : Flow<Uri?> =
            context.dataStore.data.map {settings->
            settings[SettingsKeys.lastPlayedUri]?.let{it.toUri() }}
    val lastPosition: Flow<Long?> =
        context.dataStore.data.map { settings ->
            settings[SettingsKeys.lastPosition] }
    val shuffle : Flow<Boolean?> =
        context.dataStore.data.map {settings->
            settings[SettingsKeys.shuffle]}
    val repeatMode : Flow<String?> =
        context.dataStore.data.map {settings->
            settings[SettingsKeys.repeatMode]}



}