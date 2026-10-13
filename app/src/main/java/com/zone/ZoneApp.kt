package com.zone

import android.app.Application

class ZoneApp : Application() {
    val engine: MusicEngine by lazy { MusicEngine(this) }
    val playlists: PlaylistStore by lazy { PlaylistStore(this) }
}