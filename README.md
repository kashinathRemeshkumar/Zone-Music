# Zone Music

A local-first music player for Android, built with Jetpack Compose and Media3 (ExoPlayer). Pick the folders your music lives in and Zone scans them, reads the tags and plays everything with a clean, gesture-driven player.

> **Status:** early development. Local playback works today. Google Drive streaming and Android Auto are the next milestones (see the [Roadmap](#roadmap)).

## Features

### Library
- **Folder-based library:** choose one or more folders with the system folder picker (Storage Access Framework), so no broad storage permission is needed.
- **Fast scanning:** folders are listed with `DocumentsContract` queries instead of per-file calls, which keeps big libraries quick.
- **Background refresh:** the library rescans when the app returns to the foreground (throttled), picks up new songs, and drops deleted ones.
- **Incremental tag reading:** tags are only read for new files, in small batches on a limited thread pool, and saved to a JSON cache as it goes.
- **Instant start:** the cached library is shown immediately, then refreshed in the background.
- **Alphabetical order:** songs are sorted by title using a locale-aware collator.
- **Album art thumbnails:** loaded lazily for visible rows, with a memory cache and a disk cache.

### Player
- **Now Playing screen** with a blurred album-art background and colours taken from the artwork.
- **Swipe the album art** left or right to change tracks. The neighbouring covers slide in as you drag.
- **Pull down to dismiss:** the player is an overlay that follows your finger and reveals the library underneath.
- **Queue sheet** showing the playlist the engine is using, with the current song highlighted. Tap a row to jump to it.
- **Shuffle** that keeps the current song first and shuffles the rest, and the queue shows the real play order.
- **Repeat modes:** off, one, all.
- **Seek bar**, play/pause, previous (restarts the song after 3 seconds) and next.
- **Press feedback** on buttons and rows.
- **Mini player pill** on the home screen.

## Tech stack

| Area | Library |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Playback | Media3 / ExoPlayer |
| Settings | Jetpack DataStore (Preferences) |
| Colours from artwork | AndroidX Palette |
| File access | Storage Access Framework, `DocumentsContract` |
| Concurrency | Kotlin Coroutines |

## Project structure

```
app/src/main/java/com/zone/
├── MainActivity.kt    Hosts the home screen and the player overlay
├── HomeScreen.kt      Library list, mini player pill, thumbnail loading
├── PlayerScreen.kt    Now Playing UI, art carousel, queue sheet
├── MusicEngine.kt     ExoPlayer wrapper that exposes Compose state
├── SongScanner.kt     Folder scanning and tag reading
├── SongCache.kt       JSON cache of scanned songs
└── AppSettings.kt     DataStore-backed settings (folders, last played, ...)
```

## Getting started

1. Clone the repository.
   ```bash
   git clone https://github.com/kashinathRemeshkumar/Zone-Music.git
   ```
2. Open it in Android Studio.
3. Let Gradle sync, then run the `app` configuration on a device or emulator.
4. Tap **Click to add folder** on the home screen and choose a folder that contains music.

## Roadmap

### Google Drive streaming (planned)
Stream music straight from Google Drive without downloading it first.
- [ ] Sign in with Google and request read-only Drive access
- [ ] Choose Drive folders as library sources, next to local folders
- [ ] List and scan audio files in Drive folders
- [ ] Stream through ExoPlayer with authorised requests
- [ ] Read tags and artwork for remote files, and cache the metadata
- [ ] Optional offline caching of played songs
- [ ] Handle token refresh, offline mode and network errors

### Android Auto (planned)
Browse and control the library from the car's display.
- [ ] Move playback into a `MediaLibraryService` backed by a `MediaSession`, so audio keeps playing when the app is closed
- [ ] Add a playback notification with media controls
- [ ] Expose a browsable library tree (songs, and later artists and albums) to Android Auto
- [ ] Support voice commands and steering-wheel controls
- [ ] Test with the Android Auto Desktop Head Unit

Moving playback into a service comes first, because both Android Auto and background playback depend on it.

### Other ideas
- Search
- Artist and album views
- Playlists and queue editing
- Sleep timer
- Equalizer

## Contributing

This is a personal project, but issues and suggestions are welcome.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

```
Copyright 2026 Kashinath Remeshkumar

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
