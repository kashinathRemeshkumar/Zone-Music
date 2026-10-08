# Zone Music

A modern, dark-themed local music player for Android, built with Kotlin, Jetpack Compose and Media3 (ExoPlayer).

Pick your music folder once and Zone scans it for songs and plays them. No account, no ads, no internet connection needed.

> **Status:** work in progress. The player screen is finished. The song library (folder picker, scanning, caching) is being built.

## Screenshots

<!-- Add screenshots here, for example:
![Player](screenshots/player.png)
![Home](screenshots/home.png)
-->

## Features

**Working now**
- Now Playing screen with album art read from the file's metadata
- Song title and artist taken from the file's tags
- Seek bar you can drag to jump to any point, with current time and total length
- Play / pause, previous and next
- Shuffle and repeat modes (off, repeat one, repeat all)
- Dark Material 3 theme
- Navigation between the home screen and the player

**In progress**
- Choose a music folder with the system folder picker (no storage permission required)
- Recursive scan of the folder for audio files (mp3, flac, m4a, wav, ogg, opus, aac, wma), listed A to Z
- Remember the chosen folder between launches (Jetpack DataStore)
- JSON cache of the song list, so the library loads instantly on start

**Planned**
- Playlist and queue, so next, previous, shuffle and repeat work across the whole library
- Bottom mini player on the home screen
- Search
- Background playback with notification and lock-screen controls
- ViewModel architecture and resuming the last played song

## Built with

| Area | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Audio | Media3 ExoPlayer |
| Navigation | Navigation Compose |
| Folder access | Storage Access Framework, `DocumentFile` |
| Settings | Jetpack DataStore (Preferences) |

## Project structure

```
app/src/main/java/com/zone/
├── MainActivity.kt      # entry point, navigation host
├── MusicEngine.kt       # wraps ExoPlayer, exposes playback state to the UI
├── HomeScreen.kt        # song library screen
├── PlayerScreen.kt      # Now Playing screen (PlayerScreen + PlayerContent)
├── SongScanner.kt       # recursive folder scan, Song data class
├── AppSettings.kt       # DataStore settings (folder, last song, shuffle, repeat)
└── ui/theme/            # colors, typography, dark theme
```

`PlayerScreen` connects the screen to the engine, and `PlayerContent` only draws the UI from plain values, which keeps it easy to preview.

## Getting started

1. Clone the repository:
   ```
   git clone https://github.com/kashinathRemeshkumar/Zone-Music.git
   ```
2. Open the project in the latest stable Android Studio.
3. Let Gradle sync, then run the app on an emulator or a real device.

## Roadmap

- [x] Player screen with seek bar and controls
- [x] Shuffle and repeat
- [x] Home and player navigation
- [ ] Folder picker and recursive scan
- [ ] Persist folder and cache the song list
- [ ] Real song list and tap to play
- [ ] Playlist, queue and mini player
- [ ] Background playback service
- [ ] Search

## Author

**Kashinath** — [GitHub](https://github.com/kashinathRemeshkumar) · [Portfolio](https://kashinath.qzz.io)
