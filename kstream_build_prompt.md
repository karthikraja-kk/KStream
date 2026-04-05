---

**Build KStream — a Netflix-style OTT Android app that works on both phone and Android TV. One codebase, two UI targets. Here is the complete spec:**

---

## PROJECT OVERVIEW

KStream is a native Android application that acts as a UI wrapper over an existing file-index movie website. It scrapes the website via a Cloudflare Worker proxy and presents movies in a clean OTT-style interface. The app must run efficiently on low-end devices (2GB RAM phones and basic Android TV boxes).

---

## CRITICAL REQUIREMENTS

- Zero bugs on first build — every edge case must be handled
- No Android Studio needed — build happens via GitHub Actions CI/CD
- APK and AAB artifacts uploaded automatically on every push to main
- Works on Android 7.0 (API 24) and above
- Phone and TV from single codebase, multi-module architecture
- ExoPlayer handles all video playback — no browser, no WebView, no intents for video
- No paid APIs, no paid services, everything free

---

## CI/CD — GITHUB ACTIONS (Most Critical Part)

Create `.github/workflows/build.yml`:

- Trigger on every push to `main` branch and on every pull request
- Use `ubuntu-latest` runner
- Use `actions/setup-java@v3` with Java 17 and Temurin distribution
- Use `gradle/gradle-build-action@v2` for Gradle caching
- Run `./gradlew assembleDebug` for phone APK
- Run `./gradlew tv:assembleDebug` for TV APK
- Upload both APKs as artifacts using `actions/upload-artifact@v3`
- Artifact retention: 7 days
- Cache Gradle dependencies, wrapper, and build outputs between runs
- The `gradlew` file must be committed and must have execute permissions (`chmod +x gradlew`)
- `local.properties` must NOT be committed — `sdk.dir` must be set via environment variable `ANDROID_HOME` in CI which is pre-configured on GitHub Actions ubuntu runners

---

## PROJECT STRUCTURE

```
KStream/
├── .github/
│   └── workflows/
│       └── build.yml
├── core/
│   ├── network/
│   ├── data/
│   ├── domain/
│   └── player/
├── app/                          → Phone module
├── tv/                           → TV module
├── worker/
│   └── worker.js                 → Cloudflare Worker (standalone)
├── build.gradle                  → Root build file
├── settings.gradle               → Module includes
├── gradle.properties
├── gradlew
├── gradlew.bat
└── gradle/
    └── wrapper/
        └── gradle-wrapper.properties
```

---

## GRADLE SETUP

### Root `build.gradle`
- Gradle version: 8.2.0
- Android Gradle Plugin: 8.2.0
- Kotlin version: 1.9.20
- Hilt version: 2.48
- Apply plugins at root level using `plugins {}` block with `apply false`

### `settings.gradle`
Include all modules:
- `:app`
- `:tv`
- `:core:network`
- `:core:data`
- `:core:domain`
- `:core:player`

### `gradle.properties`
```
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
org.gradle.parallel=true
org.gradle.caching=true
```

### `gradle/wrapper/gradle-wrapper.properties`
Use Gradle 8.4 distribution URL.

---

## MODULE CONFIGS

### `core/network` — No Android UI
- Apply: `com.android.library`, `kotlin-android`, `kotlin-kapt`
- Dependencies: Retrofit 2.9.0, OkHttp 4.12.0, Gson converter, Kotlin coroutines

### `core/domain` — Pure Kotlin, No Android
- Apply: `kotlin` plugin only (not Android library)
- No Android dependencies
- Only Kotlin stdlib and coroutines

### `core/data` — Android Library
- Apply: `com.android.library`, `kotlin-android`, `kotlin-kapt`, `dagger.hilt.android.plugin`
- Dependencies: Room 2.6.1, Paging 3, DataStore preferences, Hilt, core:network, core:domain

### `core/player` — Android Library
- Apply: `com.android.library`, `kotlin-android`
- Dependencies: ExoPlayer (media3) 1.2.1, core:domain

### `app` — Phone Application
- Apply: `com.android.application`, `kotlin-android`, `kotlin-kapt`, `dagger.hilt.android.plugin`
- minSdk: 24, targetSdk: 34, compileSdk: 34
- Dependencies: core:domain, core:data, core:player, core:network, Coil 2.6.0, Navigation Component 2.7.7, Material 1.11.0, Lottie 6.3.0, Hilt Navigation Fragment

### `tv` — TV Application
- Apply: `com.android.application`, `kotlin-android`, `kotlin-kapt`, `dagger.hilt.android.plugin`
- minSdk: 24, targetSdk: 34, compileSdk: 34
- Dependencies: core:domain, core:data, core:player, core:network, Leanback 1.2.0, Coil 2.6.0, Hilt Navigation Fragment

---

## CLOUDFLARE WORKER (`worker/worker.js`)

Standalone file. Deployed separately to Cloudflare Workers. Not part of Android build.

### Endpoints:

**`GET /ping?url=`**
Fetches target URL with HEAD request. Returns `{ ok: true/false, status: number }`. Used for source health check on app launch.

**`GET /fetch?url=`**
Fetches URL, parses Apache/Nginx directory HTML listing. Extracts all `<a href>` tags. Filters out `../` parent links. Returns `{ folders: [{ name, href }] }`.

**`GET /paginated?url=`**
Fetches page 1 of directory. Detects pagination by checking for patterns: "Next", "?page=", "&page=", "of X pages". Fetches ALL pages in parallel with `Promise.all`. Merges and deduplicates all folder lists. Returns `{ folders: [...all], totalPages: number, hasNextPage: false }`.

**`GET /movie?url=`**
Fetches movie folder page. Parses: poster image absolute URL, title, synopsis, genre, cast, rating, year, duration, any other visible metadata, inner quality subfolder links. For quality labels uses normalizeQualityLabel() which maps folder names to clean labels: 4K, 1080p, 720p, 480p, 360p, Original, HDRip, WEBRip, DVDRip, BluRay. If quality folder has nested folders (Russian Doll structure), drills up to 3 levels deep to find server links. Returns `{ title, poster, synopsis, genre, cast, rating, year, duration, qualityFolders: [{ name, label, href }], server1Url, server2Url, watchServer1Url, watchServer2Url }`.

**`GET /poster?url=`**
Fetches only movie folder page, extracts only poster image URL. Lightweight endpoint. Returns `{ posterUrl }`.

All responses must include `Access-Control-Allow-Origin: *`. All errors return `{ error: string, ok: false }` with appropriate HTTP status. Worker must handle OPTIONS preflight requests.

---

## SOURCE WEBSITE STRUCTURE

```
baseUrl/                              → year folders
baseUrl/{year}/                       → movie folders (may be paginated)
baseUrl/{year}/{movieName}/           → movie detail page with poster, info, subfolder
baseUrl/{year}/{movieName}/{name}/    → quality folders (720p, 1080p etc)
baseUrl/{year}/{movieName}/{name}/{quality}/ → server download links with ad redirects
```

The app stores the raw server URLs exactly as found on the page. ExoPlayer opens them directly. Android has no CORS restrictions so ExoPlayer can follow redirects and play the video natively.

---

## CORE:DOMAIN MODULE

### Data Models:
- `MovieDetail` — path, title, year, synopsis, genre, cast (List<String>), rating, duration, qualities (List<QualityOption>), posterUrl
- `QualityOption` — label, server1Url, server2Url, watchServer1Url, watchServer2Url
- `MovieItem` — path, title, year, posterUrl (nullable)
- `YearItem` — year, movieCount
- `ContinueWatchingItem` — moviePath, title, posterUrl, progressMs, durationMs, updatedAt
- `RecentlyVisitedItem` — moviePath, title, posterUrl, year, visitedAt
- `Resource<T>` — sealed class: Loading, Success(data, fromCache), Error(message)
- `AppSettings` — userName, baseUrl, workerUrl, backupWorkerUrl, qualityPreference, volume

### Use Cases (one class per use case):
- `GetYearsUseCase`
- `GetMoviesByYearUseCase` — returns Flow<PagingData<MovieItem>>
- `GetMovieDetailUseCase` — returns Flow<Resource<MovieDetail>>
- `GetPosterUseCase`
- `UpdateWatchProgressUseCase`
- `GetContinueWatchingUseCase`
- `RemoveFromContinueWatchingUseCase`
- `GetRecentlyVisitedUseCase`
- `AddRecentlyVisitedUseCase`
- `PingSourceUseCase`
- `GetAppSettingsUseCase`
- `UpdateAppSettingsUseCase`

---

## CORE:NETWORK MODULE

### Retrofit API interface `WorkerApi`:
- All functions are `suspend` functions
- Use `@GET` with `@Query("url")` for all endpoints
- Return response data classes matching Worker JSON responses

### OkHttp client:
- Connect timeout: 15 seconds
- Read timeout: 30 seconds
- Write timeout: 15 seconds
- HTTP response cache: 10MB cache in app cache dir
- Cache-Control interceptor: cache responses for 1 hour
- Logging interceptor in debug builds only

### Hilt NetworkModule:
- Provide `WorkerApi` as singleton
- Worker base URL read from DataStore at startup
- If worker URL changes in settings, rebuild Retrofit instance

---

## CORE:DATA MODULE

### Room Database `KStreamDatabase`:

**Tables:**
- `years` — year (PK), movieCount, cachedAt
- `movies` — path (PK), title, year, synopsis, genre, cast (JSON), rating, duration, qualityFoldersJson, server1Url, server2Url, watchServer1Url, watchServer2Url, cachedAt, lastAccessedAt
- `posters` — moviePath (PK), posterUrl, cachedAt
- `continue_watching` — moviePath (PK), title, posterUrl, progressMs, durationMs, updatedAt
- `recently_visited` — moviePath (PK), title, posterUrl, year, visitedAt

**Cache eviction rules enforced in DAOs:**
- Movies table: max 100 entries. On insert, delete oldest by lastAccessedAt if count exceeds 100
- Posters: max 200 entries. Delete oldest by cachedAt if exceeded
- Continue watching: max 20 entries. Delete oldest by updatedAt if exceeded
- Recently visited: max 10 entries. Delete oldest by visitedAt if exceeded

**Cache freshness:**
- Movie detail: fresh if cachedAt within 24 hours, stale if 1-7 days, expired if over 7 days
- Posters: fresh if within 30 days
- Year list: fresh if within 24 hours

### DataStore:
- Store: userName, baseUrl, workerUrl, backupWorkerUrl, qualityPreference, volumeLevel
- Provide as Flow for reactive updates
- Provide suspend functions for writes

### Repository implementations:
- `MovieRepository` — implements stale-while-revalidate: emit cached data immediately if exists, fetch fresh in background if stale, update cache on success, silently fail if stale data exists
- `PosterRepository` — fetch and cache poster URLs independently from movie detail
- `WatchRepository` — manage continue watching and recently visited
- `SettingsRepository` — wrap DataStore

### Paging:
- `MoviePagingSource` — checks Room cache first per page, fetches from Worker if missing, stores in Room, returns Paging 3 LoadResult
- PagingConfig: pageSize=20, prefetchDistance=5, enablePlaceholders=true

---

## CORE:PLAYER MODULE

### ExoPlayerManager:
- Detect low RAM device via `ActivityManager.isLowRamDevice()`
- Low RAM: min buffer 15s, max buffer 30s, force lowest bitrate, cap at SD resolution
- Normal: min buffer 30s, max buffer 60s
- Build `DefaultTrackSelector` with appropriate parameters
- Provide `buildPlayer()` function returning configured ExoPlayer instance
- Provide `buildMediaItem(url, title)` helper

---

## PHONE APP MODULE

### Architecture: Single Activity, multiple Fragments, Navigation Component

### `MainActivity`:
- Single NavHostFragment
- Handle back press with Navigation Component
- Show/hide bottom navigation based on current destination
- Observe network connectivity and show snackbar when offline

### Fragments and their behavior:

**WelcomeFragment:**
- Show only if userName or baseUrl missing in DataStore
- KStream logo with violet glow
- Name input field
- Base URL input field with URL validation
- Worker URL input field
- "Start Watching" button — disabled until all fields valid
- On submit: save to DataStore, navigate to HomeFragment, popBackStack to remove welcome from back stack

**HomeFragment:**
- Observe source health on start — ping baseUrl, show persistent error banner if unreachable with "Update in Settings" button
- Observe offline state — show banner if offline
- RecyclerView with two section types: horizontal scroll rows and year grid
- Section 1: Continue Watching horizontal scroll — show only if list non-empty, each card shows poster + violet progress bar
- Section 2: Recently Visited horizontal scroll — show only if list non-empty
- Section 3: Browse by Year — grid of year tiles sorted newest first
- Search icon in toolbar — opens search fragment or expands search bar
- Settings icon in toolbar — opens SettingsBottomSheet

**YearFragment:**
- Receives year as nav argument
- Toolbar title = year
- Search/filter EditText at top — filters visible list in real time without re-fetching
- RecyclerView with GridLayoutManager — 2 columns on phones under 600dp, 4 columns on tablets
- Uses PagingDataAdapter with DiffUtil
- LoadStateAdapter for footer — shows spinner while loading next page, retry button on error
- Each MovieCard: poster image (Coil lazy load), gradient overlay, title text below
- On image load error: show violet gradient placeholder with first letter of title
- Poster fetched separately — card renders immediately with placeholder, poster loads when card visible
- On card click: navigate to MovieDetailFragment

**MovieDetailFragment:**
- Receives movie path as nav argument
- Show skeleton layout while loading
- If cached data exists: show immediately, show subtle "Updating..." indicator while revalidating
- Layout: blurred poster as backdrop, poster on left, info on right (two-panel on tablets, stacked on phones)
- Info: title, genre chips, year, duration, rating, synopsis, cast
- Quality chips below info: auto-select user's preferred quality
- "Watch Now" and "Download" buttons — enabled only after quality selected
- Watch Now: navigate to PlayerFragment with server1Url, server2Url, title, moviePath
- Download: use Android DownloadManager with notification
- Add to recently visited on fragment start

**PlayerFragment:**
- Receives url, server2Url, title, moviePath as nav args
- Force landscape orientation on start, restore on stop
- StyledPlayerView fills screen
- Show loading indicator until playback starts
- On player error: if server2Url available and not yet tried, switch to server2 automatically, show toast "Trying backup server..."
- If server2 also fails: show error card with "Download Instead" button
- Save progress to Room every 5 seconds while playing
- Restore saved position on start if continue watching entry exists
- Back button: save final position, navigate back

**SettingsBottomSheet:**
- BottomSheetDialogFragment
- Source URL field with test connection button
- Worker URL field
- Backup worker URL field
- Name field
- Quality preference spinner
- Cache stats: entries count, estimated size, oldest entry date
- Clear cache button — clears movies and posters tables, NOT continue watching or recently visited
- Reset everything button — clears all data, navigates to WelcomeFragment

### Navigation Graph (`nav_graph.xml`):
- Start destination: HomeFragment (with conditional redirect to WelcomeFragment if settings incomplete)
- Define all fragment destinations and actions with argument types
- Use Safe Args plugin for type-safe navigation arguments

### Theming (`themes.xml`):
- Parent: `Theme.MaterialComponents.DayNight.NoActionBar`
- Primary color: `#7C3AED` electric violet
- Background: `#000000` black
- Surface: `#111111`
- On-surface: `#FFFFFF`
- Custom toolbar style with black background and violet accent

---

## TV MODULE

### Architecture: Single Activity, multiple Fragments, Navigation Component

### Leanback Fragments:

**TvWelcomeFragment:**
- Full screen dark layout
- KStream logo centered
- D-pad navigable input fields for name, base URL, worker URL
- Focus management: Tab order must be correct for D-pad

**TvHomeFragment extends BrowseSupportFragment:**
- Brand color: `#7C3AED`
- Title: "KStream"
- Headers enabled
- Row 1: Continue Watching — horizontal list of movie cards with progress bars
- Row 2: Recently Visited
- Row 3+: One row per year, each row shows movie cards for that year
- Load year rows lazily — fetch movies for a row only when it receives focus
- Item click: navigate to TvDetailFragment

**TvYearFragment extends VerticalGridSupportFragment:**
- 5 columns
- Show all movies for selected year
- Paging: load more as user scrolls to bottom

**TvDetailFragment extends DetailsSupportFragment:**
- DetailsOverviewRow: poster, title, synopsis
- Action buttons: Watch Now, Download
- Quality selection: shown as a row of action buttons

**TvPlayerFragment extends VideoSupportFragment:**
- Use `ExoPlayerAdapter` with `ProgressTransportControlGlue`
- D-pad controls: play/pause, seek forward/back, volume
- Show title overlay
- Save progress every 5 seconds
- Auto fallback to server2 on error

### TV AndroidManifest:
```xml
<uses-feature android:name="android.software.leanback" android:required="true"/>
<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>
```
- Main activity must have `android.intent.category.LEANBACK_LAUNCHER` intent filter

---

## PHONE ANDROIDMANIFEST

```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
```
- Main activity: `android:configChanges="orientation|screenSize"` to handle rotation without recreating
- `android:screenOrientation="portrait"` on MainActivity — PlayerFragment overrides to landscape

---

## HILT DEPENDENCY INJECTION

- `@HiltAndroidApp` on Application class in both app and tv modules
- `@AndroidEntryPoint` on all Activities and Fragments
- `@ViewModelInject` (Hilt ViewModel) on all ViewModels
- NetworkModule: provides WorkerApi singleton
- DatabaseModule: provides Room database and all DAOs as singletons
- RepositoryModule: binds repository interfaces to implementations
- PlayerModule: provides ExoPlayerManager

---

## LOW-END DEVICE OPTIMIZATIONS

- Detect `ActivityManager.isLowRamDevice()` and adjust all buffer sizes accordingly
- RecyclerView: `setHasFixedSize(true)`, `setItemViewCacheSize(20)`, `setRecycledViewPool` shared between similar lists
- Coil: `size(ViewSizeResolver(imageView))` — downsample to exact view dimensions, never load full resolution into a thumbnail
- Coil: set `memoryCachePolicy` and `diskCachePolicy` to ENABLED
- Paging 3: pageSize 20 — never load more than needed
- ExoPlayer: release in `onStop`, never hold player across fragments
- Room: all queries on IO dispatcher, never on main thread
- WorkManager for any background tasks: require network, require not low battery
- Avoid memory leaks: clear all view references in `onDestroyView`, use `viewLifecycleOwner` for all observers

---

## ERROR HANDLING — EVERY CASE MUST BE HANDLED

- No internet on launch: show offline banner, show cached data if available, show empty state if no cache
- Source URL unreachable: show persistent banner with settings shortcut
- Worker URL not set: show setup prompt
- Movie page parse fails: show error card with retry button
- Poster load fails: show violet gradient placeholder with movie initial letter, never show broken image
- Video playback fails on server1: auto switch to server2 silently
- Video playback fails on both servers: show error with download option
- Download fails: show notification with error
- Room query fails: show error state, offer retry
- All network errors must show user-friendly messages, never raw exception messages
- Paging load errors: show retry button in footer, never crash

---

## FILE STRUCTURE — COMPLETE

```
KStream/
├── .github/
│   └── workflows/
│       └── build.yml
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── core/
│   ├── network/
│   │   └── src/main/
│   │       ├── WorkerApi.kt
│   │       ├── NetworkModule.kt
│   │       ├── CacheInterceptor.kt
│   │       └── responses/             → data classes matching Worker JSON
│   ├── domain/
│   │   └── src/main/
│   │       ├── models/
│   │       └── usecases/
│   ├── data/
│   │   └── src/main/
│   │       ├── db/
│   │       │   ├── KStreamDatabase.kt
│   │       │   ├── entities/
│   │       │   └── daos/
│   │       ├── datastore/
│   │       ├── repositories/
│   │       ├── paging/
│   │       └── di/
│   └── player/
│       └── src/main/
│           └── ExoPlayerManager.kt
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── KStreamApp.kt              → @HiltAndroidApp
│       ├── MainActivity.kt
│       ├── ui/
│       │   ├── welcome/
│       │   ├── home/
│       │   ├── year/
│       │   ├── detail/
│       │   ├── player/
│       │   └── settings/
│       ├── res/
│       │   ├── layout/
│       │   ├── navigation/
│       │   │   └── nav_graph.xml
│       │   ├── values/
│       │   │   ├── themes.xml
│       │   │   ├── colors.xml
│       │   │   └── strings.xml
│       │   └── drawable/
│       └── di/
├── tv/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── TvApp.kt
│       ├── TvMainActivity.kt
│       ├── ui/
│       │   ├── welcome/
│       │   ├── home/
│       │   ├── year/
│       │   ├── detail/
│       │   └── player/
│       ├── res/
│       │   ├── layout/
│       │   ├── navigation/
│       │   └── values/
│       └── di/
├── worker/
│   └── worker.js
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
└── gradlew.bat
```

---

## BUILD ORDER

Build in this exact sequence to avoid dependency errors:

1. `gradle/wrapper/` and root `build.gradle` and `settings.gradle`
2. `core/domain` — models and use case interfaces, pure Kotlin
3. `core/network` — WorkerApi, Retrofit, response models, NetworkModule
4. `core/data` — Room, DataStore, repositories, paging, DatabaseModule
5. `core/player` — ExoPlayerManager
6. `worker/worker.js` — Cloudflare Worker
7. `app/` — Phone UI, all fragments, viewmodels, nav graph, themes
8. `tv/` — TV UI, all Leanback fragments, viewmodels, nav graph
9. `.github/workflows/build.yml` — CI/CD last, after everything else exists

---

## GITHUB ACTIONS REQUIREMENTS

- Both phone APK and TV APK must be built and uploaded as artifacts on every push to main
- Build must succeed without local.properties
- ANDROID_SDK_ROOT is pre-set on GitHub Actions ubuntu runners — do not hardcode SDK path
- Gradle wrapper must be executable — add `run: chmod +x gradlew` as a step before build
- Cache Gradle at `~/.gradle/caches` and `~/.gradle/wrapper` between runs
- Name artifacts clearly: `KStream-phone-debug.apk` and `KStream-tv-debug.apk`
- On pull requests: build only, do not upload artifacts