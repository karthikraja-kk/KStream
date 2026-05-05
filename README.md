# KStreamV2 - Netflix-style OTT App

A production-ready Android OTT application for Mobile and Android TV.

## Architecture
- **Clean Architecture** with **MVVM**.
- **Multi-module** structure:
    - `:app`: Main entry point.
    - `:core:network`: Supabase integration.
    - `:core:data`: Room, DataStore, Repositories.
    - `:core:domain`: Use cases.
    - `:core:ui`: Shared design system.
    - `:feature:*`: Feature-specific modules.

## Tech Stack
- **Kotlin**, **Coroutines**, **Flow**.
- **Jetpack Compose** (Mobile & TV).
- **Hilt** for Dependency Injection.
- **Supabase** for remote data.
- **Media3 ExoPlayer** for playback.
- **Media3 DownloadManager** for downloads.
- **Room** for local persistence.
- **DataStore** for preferences.
- **Coil** for image loading.

## Features
- **Welcome Screen**: Onboarding and username setup.
- **Home Screen**: Discovery rails (New Release, Continue Watching, Recommendations).
- **Details Screen**: Metadata and quality selection.
- **Player**: Online/Offline playback with progress sync.
- **Downloads**: Background downloading and offline playback.
- **Search**: Discovery via name and metadata.
- **Settings**: Profile and cache management.
- **TV Support**: D-pad focus management and 10-foot UI.

## Setup
1. Add `SUPABASE_URL` and `SUPABASE_KEY` to `local.properties`.
2. Sync Gradle.
3. Run the `app` module on a Mobile or TV emulator/device.

## Testing
- Unit tests for Use Cases and Repositories are located in their respective `test` directories.
- Run tests using `./gradlew test`.

## Final Implementation Report
- **Architecture**: Modularized Clean Architecture.
- **Screen Map**: Welcome -> Home -> Details -> Player.
- **Data Model**: Movie, Media, WatchProgress, Download.
- **Performance**: 60fps scrolling targets.
- **Reliability**: Offline-first with local caching.
