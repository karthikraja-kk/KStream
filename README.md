# KStream TV

> A premium Android **Leanback** OTT app for Fire TV / Android TV that streams a
> Tamil/Indian movie catalog hosted on Supabase, enriched with TMDb metadata,
> and tuned to run smoothly even on a 1 GB Fire TV Stick Lite.

This README is the single source of truth for the codebase. Read it end-to-end
before you change anything — it covers the data model, the module graph, the
runtime architecture, how each screen is wired, and the gotchas that have bitten
us before.

---

## Table of contents

1. [What KStream is](#1-what-kstream-is)
2. [Tech stack](#2-tech-stack)
3. [Repository layout](#3-repository-layout)
4. [Module graph](#4-module-graph)
5. [Data model](#5-data-model)
6. [Data flow & sync strategy](#6-data-flow--sync-strategy)
7. [Screen map & navigation](#7-screen-map--navigation)
8. [Player architecture](#8-player-architecture)
9. [Search architecture](#9-search-architecture)
10. [Settings & reset flow](#10-settings--reset-flow)
11. [Design system & device tiers](#11-design-system--device-tiers)
12. [TV input contracts (D-pad, IME, BACK)](#12-tv-input-contracts-d-pad-ime-back)
13. [Performance & memory rules](#13-performance--memory-rules)
14. [Build, install, debug](#14-build-install-debug)
15. [Coding conventions](#15-coding-conventions)
16. [Common pitfalls](#16-common-pitfalls)

---

## 1. What KStream is

- **Platform:** Android TV / Fire TV only (no mobile build on this branch).
- **Min SDK:** 25 (Android 7.1 — Fire TV Stick 4K Max / Cube / Stick Lite all
  pass).
- **Compile/target SDK:** 34.
- **Application ID:** `com.kstream.tv`.
- **Launcher category:** `LEANBACK_LAUNCHER` — the app shows up on the Fire TV
  / Google TV home tile.
- **Data source:** Supabase (Postgres + Edge Functions). The app is read-only;
  a separate scraper writes to `movies` / `media` tables.
- **Enrichment:** TMDb (free dev tier) layered on top of the catalog for
  posters, cast photos, taglines, certification, reviews.
- **Playback:** Media3 ExoPlayer over progressive MP4 (HLS/DASH wired but not
  used by the current catalog).
- **Offline:** Not on TV. Downloads exist in `:feature:downloads` for the
  mobile variant but the TV app intentionally skips them.

The branch in active development is **`kstream-tv`**. The TV app was rewritten
from a Compose prototype to native Leanback because Compose-on-low-end-Fire-TV
ANRed every cold start (Compose's first-render JIT blocked the main thread for
>5 s on 1 GB devices).

---

## 2. Tech stack

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin 1.9.22, JVM target 17 | Coroutines + Flow everywhere |
| UI framework | **Android Leanback** + classic Views + ViewBinding | AOT-compiled, native focus engine, recyclable rows — no Compose JIT cost |
| DI | **Hilt** (KSP) | Constructor injection across modules |
| Async | Coroutines + StateFlow + `repeatOnLifecycle` | Lifecycle-aware collection in Fragments |
| Persistence | **Room** (catalog + watch progress + likes + downloads + recommendations) + a second Room DB for enrichment | Strict separation so wiping the catalog never touches TMDb cache |
| Preferences | `DataStore` (Proto-less, key/value) | First-launch flag, user name, settings |
| Network | Supabase (Postgrest + Edge Functions) via the official Kotlin SDK, OkHttp for TMDb | |
| Serialization | `kotlinx.serialization.json` (lenient, ignoreUnknownKeys) | |
| Player | Media3 ExoPlayer 1.2.1 | HLS, DASH and OkHttp datasource included |
| Image loader | **Glide** 4.16 (with OkHttp + KSP) | Better RecyclerView recycling than Coil on Fire TV |
| Splash animation | Lottie 6.3 | `kstream-splash-lottie.json` plays once on first launch |
| Shimmer placeholders | Facebook `shimmer:0.5.0` | Home rails while data is warming |
| Crash + ANR | In-house `TvCrashHandler` + `AnrWatchdog` + `SafeMode` | Auto-restart on recoverable main-thread crashes; degrade to flat dark UI after repeated crashes |
| Core lib desugaring | enabled (`desugar_jdk_libs:2.0.4`) | `java.time.*` on API < 26 |

---

## 3. Repository layout

```
KStream/
├── app-tv/                 # Leanback launcher app (the only delivered binary)
├── bootstart/              # Historical bootstrap scripts / one-off tools
├── core/
│   ├── common/             # MemoryGuardian, shared utils (logging, error)
│   ├── model/              # Pure data classes: Movie, Media, WatchProgress …
│   ├── network/            # Supabase client + DTOs + KStreamNetworkDataSource
│   ├── data/               # Room DB, DataStore, repository implementations
│   ├── domain/             # Use cases + repository interfaces
│   └── enrichment/         # Self-contained TMDb integration (own Room DB)
├── feature/
│   ├── home/               # HomeViewModel — rails, hero rotator state
│   ├── details/            # DetailsViewModel — movie + media + quality pick
│   ├── player/             # PlayerManager (singleton ExoPlayer) + PlayerViewModel
│   ├── search/             # SearchViewModel — query, suggestions, scope, sort
│   ├── settings/           # SettingsViewModel — profile, cache, reset
│   └── downloads/          # Download manager (mobile-only; TV doesn't bind it)
├── docs/                   # (currently empty — historical plans live in git history)
├── apks/                   # Hand-published binaries (gitignored except as released)
├── kstream-logo*.png       # Brand sources
├── kstream-splash-lottie.json
├── APP_SPEC.md             # Supabase schema reference (single page)
├── local.properties.example
├── settings.gradle.kts     # Lists every Gradle module
└── build.gradle.kts        # Plugins-only root build
```

> The Compose mobile `:app` module was removed when we forked `kstream-tv`. If
> you ever bring mobile back, recreate it as a sibling of `:app-tv` and
> reuse the `:core` and `:feature` modules unchanged.

---

## 4. Module graph

```
                     ┌───────────────────────────┐
                     │           :app-tv         │  Leanback Activities,
                     │  (KStreamTvApp, Splash,   │  Fragments, Presenters,
                     │   Main, Home, Details,    │  Glide module, SafeMode,
                     │   Player, Search, …)      │  TvCrashHandler, AnrWatchdog
                     └────────────┬──────────────┘
                                  │ depends on every :feature:* and every :core:*
       ┌──────────────────────────┼──────────────────────────┐
       ▼                          ▼                          ▼
┌──────────────┐         ┌────────────────┐         ┌──────────────────┐
│ :feature:    │         │  :feature:     │         │  :feature:       │
│   home /     │         │   player       │  …      │   settings       │
│   details /  │         │ (ExoPlayer,    │         │ (reset, cache)   │
│   search …   │         │  PlayerManager)│         │                  │
└──────┬───────┘         └────────┬───────┘         └────────┬─────────┘
       │ ViewModels only                                     │
       └──────────────────────────┬──────────────────────────┘
                                  ▼
                       ┌─────────────────────┐
                       │     :core:domain    │  UseCases + repository
                       │ (UseCase + Repo IF) │  interfaces — no Android deps
                       └──────────┬──────────┘
                                  ▼
                       ┌─────────────────────┐
                       │     :core:data      │  Room (KStreamDatabase v7),
                       │  (Repo impls, DAOs, │  DataStore, OfflineFirst*
                       │   KStreamDataStore) │  repositories
                       └──────────┬──────────┘
                                  ▼
                       ┌─────────────────────┐
                       │   :core:network     │  Supabase Postgrest + Edge
                       │  (SupabaseDataSrc)  │  Functions, NetworkMovie DTO
                       └──────────┬──────────┘
                                  ▼
                       ┌─────────────────────┐
                       │    :core:model      │  Pure Kotlin data classes
                       │    :core:common     │  MemoryGuardian, shared utils
                       └─────────────────────┘

   ┌─────────────────────────┐
   │   :core:enrichment      │  Independent: own Room DB, own TmdbClient,
   │ (TMDb + own Room cache) │  consumed directly by feature ViewModels
   └─────────────────────────┘  and :app-tv presenters (e.g. CastCardPresenter).
```

Rules:
- A `:feature:*` module **may** depend on any `:core:*`. It **must not** depend
  on `:app-tv` or another `:feature:*`.
- `:core:enrichment` is self-contained: own DAO, own DB, own DTOs. It is *not*
  wired through `:core:data` — UI/ViewModels inject `EnrichmentRepository`
  directly and overlay the result on top of `Movie`.
- All `repository` interfaces live in `:core:domain`. Their impls live in
  `:core:data`. Hilt binds them in `core/data/.../di/DataModule.kt`.

---

## 5. Data model

### 5.1 Network DTOs (Supabase)

| Table | DTO | Notes |
|---|---|---|
| `movies` | `NetworkMovie` | Source of truth for the catalog (id, name, year, poster, synopsis, director[], cast_members[], genres[], rating, language, type, slug, updated_at). Paginated in pages of 1000. |
| `media` | `NetworkMedia` | One row per `(movie_id, quality)` with `watch_url_1/2` (streaming) and `download_url_1/2` (direct `.mp4`). |
| RPC `media-refresh` | `RefreshMediaResult` | Edge Function: asks the scraper to refresh expired links for a slug. Returns `Queued` / `Processing` / `Done` / `Failed`. |
| RPC `trigger-scan` | `ScanTriggerResponse` | Edge Function: kicks a full re-scan. Used by the side-nav Refresh button. |
| RPC `scan-status` | `ScanStatusEntry` | Polls scan progress. |

### 5.2 Domain models (`:core:model`)

```kotlin
@Serializable
data class Movie(
    val id: String,
    val movieName: String,
    val year: Int,
    val posterUrl: String,
    val duration: String,           // "02:35:07 min"
    val synopsis: String,
    val director: List<String>,
    val castMembers: List<String>,
    val genres: List<String>,
    val rating: String,             // "6.9"
    val language: String,           // "Tamil"
    val type: String,               // "Original HD"
    val slug: String,               // /happy-raj-2026-tamil-movie/
    val lastUpdated: String = ""
)

@Serializable
data class Media(
    val movieId: String,
    val quality: String,            // "1080p HD", "720p HD", "360p HD"
    val fileSize: String,
    val downloadUrl1: String?,
    val downloadUrl2: String?,
    val watchUrl1: String?,
    val watchUrl2: String?
)

data class MovieWithMedia(val movie: Movie, val media: List<Media>)

enum class ScanStatus { IDLE, RUNNING, COMPLETED, FAILED }
```

### 5.3 Room (catalog DB — `KStreamDatabase`, version 7)

```
movies_cache         (PK id)              MovieEntity        — cached movie list
watch_progress       (PK movieId)         WatchProgressEntity — lastPosition, duration, %, quality
download             (PK id)              DownloadEntity     — TV doesn't write here
liked_movies         (PK movieId)         LikedMovieEntity   — heart toggle
recommendations      (PK movieId)         RecommendationEntity — precomputed score, computedAt
```

> `List<String>` fields (director, cast, genres) are persisted as
> JSON strings inside `MovieEntity`; map back at the repository edge via
> `NetworkMapper`.

### 5.4 Room (enrichment DB — `EnrichmentDatabase`, in `:core:enrichment`)

```
movie_enrichment     (PK kstreamMovieId)  MovieEnrichmentEntity
  ├─ tmdbId, confidence (≥70 to persist)
  ├─ tagline, overview, logoUrl, posterUrl, tmdbRating, certification
  ├─ backdrops (JSON list)
  ├─ cast (JSON list of EnrichedCast)
  ├─ reviews (JSON list of EnrichedReview)
  ├─ collectionName, budget, revenue, keywords (JSON)
  └─ fetchedAtEpochMs
```

The enrichment cache is **never invalidated by the catalog clear** — TMDb
metadata for a finished film almost never meaningfully changes, and TMDb has
strict rate limits.

### 5.5 DataStore keys (`KStreamDataStore`)

| Key | Type | Meaning |
|---|---|---|
| `first_launch_completed` | Boolean | Splash → Welcome on `false`, Splash → Main on `true` |
| `user_name` | String | Greeting on Home (set from Welcome / Settings) |
| `terms_accepted` | Boolean | Gate from Welcome to Main |
| (others) | … | Settings preferences |

---

## 6. Data flow & sync strategy

### 6.1 Cold start

```
SplashActivity
   ├── read DataStore.first_launch_completed
   │     ├─ false → WelcomeActivity → TermsActivity → MainActivity
   │     └─ true  → MainActivity
   └── kicks HomePrewarmTask in parallel (StartupSyncManager)
```

`StartupSyncManager` runs `SyncMoviesUseCase` (network → Room) on a background
dispatcher, then triggers `EnrichmentRepository.refresh()` for the top **12**
visible tiles (parallelism 4). The home Fragment observes Room and renders
whatever is there immediately, so the user sees rails even before sync
finishes.

### 6.2 Offline-first repositories

Every catalog read goes through an `OfflineFirst*Repository` in `:core:data`:

```
ViewModel.collect Flow<List<Movie>>
        ↓
OfflineFirstMovieRepository.getMovies()
        ↓
   MovieDao.observeAll()         ←  one-shot SyncMoviesUseCase refreshes Room
        ↓                          when network finishes
   Flow<List<MovieEntity>>
        ↓ map at the repo edge
   Flow<List<Movie>>
```

UI never blocks on the network. The Refresh button (side-nav) re-runs
`SyncMoviesUseCase` + re-enriches the top 12 tiles, exactly mirroring first-open.

### 6.3 TMDb enrichment

```
Movie  ──► EnrichmentRepository.observe(movie) → Flow<MovieEnrichment?>
                  │
                  └─► ensureCached(movie)     (fire-and-forget on first read)
                          │
                          ├─ search TMDb by title + year
                          ├─ MovieMatcher scores candidates (title sim, year proximity,
                          │   language match, popularity)
                          ├─ if best score ≥ 70 → fetch detail with
                          │   `append_to_response=credits,images,reviews,keywords`
                          │   (one HTTP round-trip)
                          └─ persist to Room
```

All TMDb image paths are stored as **full URLs** (base + size + path) so the UI
never needs to know `TMDB_IMAGE_BASE_URL`.

### 6.4 Watch progress

- Player tick (every ~1 s) → `PlayerViewModel.saveProgress()` →
  `OfflineFirstWatchProgressRepository.upsert(WatchProgressEntity)`.
- Details screen reads `Flow<WatchProgressEntity?>` and decides:
  - `Play` button (no progress)
  - `Resume` split button (in progress, < 95 %)
  - `Watch again` + `Start Over` (≥ 95 %)
- `Resume` → seek to `lastPosition`; `Start Over` → `seekTo(0)`.

---

## 7. Screen map & navigation

```
SplashActivity                       — Theme.KStreamTv.Splash, Lottie + brand
        │
        ├─ first launch ─► WelcomeActivity ─► TermsActivity ─► MainActivity
        └─ subsequent  ─► MainActivity

MainActivity (the heart of the app)
        │     contains:
        │       - SideNavController (left rail: Home / Search / History / Liked /
        │         Settings / Refresh)
        │       - HomeRowsFragment (Leanback `RowsSupportFragment`)
        │           ├─ Hero pager (BackdropCarousel) — top 5 trending,
        │           │   7-second auto-rotation, pauses on focus
        │           ├─ Continue Watching row
        │           ├─ Recommended for you row
        │           ├─ Recently added row
        │           └─ Genre rails (one per genre tag in the catalog)
        │
        ├─► DetailsActivity → DetailsTvFragment
        │       Hero backdrop · synopsis · Play / Resume(↺ Start Over) · Like
        │       Cast row (TMDb photos, initials fallback) · Reviews · Similar
        │
        ├─► PlayerActivity → PlayerTvFragment   (custom controls; see §8)
        │
        ├─► SearchActivity → SearchTvFragment
        │       Brand keyboard input · in-app voice overlay · search-by /
        │       sort-by dropdowns (see §9) · results grid
        │
        ├─► WatchHistoryActivity / LikedMoviesActivity
        │       Personal grids backed by `PersonalMovieGridAdapter`
        │
        ├─► BrowseAllActivity   (See More from a rail)
        │
        └─► SettingsActivity → SettingsTvFragment
                Profile · Theme · Cache info · Reset & Restart (see §10)
```

Every Activity uses `Theme.KStreamTv` (or `.Splash`), all landscape-locked,
all `singleTask` for the ones the user can re-enter from the launcher.

---

## 8. Player architecture

### 8.1 Components

- **`PlayerManager`** (singleton, lives in `:feature:player`) — owns the
  ExoPlayer instance. `getPlayer()` returns the same instance across
  configuration changes; `release()` only fires from `PlayerViewModel.onCleared`.
  This avoids the "audio still playing after BACK" bug if the Fragment is
  recreated mid-playback.
- **`PlayerViewModel`** — exposes `PlayerUiState` (title, current/available
  quality, refresh/error overlays). Handles quality switching, retry, watch
  progress save tick.
- **`PlayerTvFragment`** — custom controls (no Media3 built-in controller):
  - **Top:** title + brand-styled quality dropdown chip.
  - **Center:** play/pause circular button + buffering spinner.
  - **Bottom:** position / SeekBar / duration (gold accent).
  - Quality menu is an **in-app dropdown panel** (matches the search-by /
    sort-by panels), not Android's `PopupMenu`.

### 8.2 D-pad state machine

| State | Key | Behaviour |
|---|---|---|
| Controls hidden | LEFT/RIGHT | Accumulating **±10 s** seek (`SEEK_STEP_FINE_MS`) with `[« -Ns]` / `[+Ns »]` indicator, 700 ms debounce |
| Controls hidden | CENTER / ENTER | Toggle play/pause **and** show controls immediately |
| Controls hidden | any other | Show controls |
| Controls visible, SeekBar focused | LEFT/RIGHT | Accumulating **±3 min** seek (`SEEK_STEP_COARSE_MS`) |
| Controls visible, other focus | LEFT/RIGHT | Normal navigation |
| Quality panel open | BACK | Dismiss panel only (not the controls) |
| Controls visible | BACK | Hide controls (don't leave the screen) |
| Controls hidden | BACK | Leave Player (Activity handles it) |

Both seek paths share the same `addSeek()` accumulator + 700 ms commit
debounce. The indicator auto-formats minutes vs seconds at ±60 s.

`AUTO_HIDE_MS = 5000` — controls fade after 5 s of inactivity. Any key press
resets the timer.

### 8.3 Stream selection

`watch_url_1` → `watch_url_2` → `download_url_1` (as last resort). Quality
defaults to highest available; user pick is persisted to
`WatchProgressEntity.quality` so future Resume keeps the same quality.

---

## 9. Search architecture

### 9.1 UI surfaces

- `queryEdit` — TV `EditText` with the app-wide IME contract (see §12).
- **Scope panel (`search_scope_panel`)** — "search by" filter (title / cast /
  director / genre …). Right-anchored, 240 dp wide.
- **Sort panel (`search_sort_panel`)** — sort key + direction. Same surface
  drawable (`bg_search_dropdown_panel`).
- **Voice overlay (`voice_overlay`)** — full-screen scrim with brand mic ring,
  caption, and live partial transcript (see §9.3).
- Results grid — `VerticalGridSupportFragment`.

### 9.2 Suggestions & pinned filters

`SearchViewModel` debounces query input (300 ms) and emits a `SearchUiState`
that contains either suggestions (substring matches over the cached `Movie`
list) or full results. When the user selects a suggestion of a non-title kind
("All movies by AR Rahman"), it becomes a **pinned filter** — typing keeps
it; clearing the box releases it.

### 9.3 Voice search

We do **not** launch `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` (Android shows
an app chooser between Google and Google Speech Services on Fire TV — looks
terrible). Instead:

1. `SpeechRecognizer.isRecognitionAvailable(ctx)` check.
2. If `true` → `SpeechRecognizer.createSpeechRecognizer()` + our own overlay.
   - Mic ring (160 dp inside a 240 dp `clipChildren=false` box) pulses with
     `onRmsChanged()` mapped to a subtle **1.00 – 1.12×** scale.
   - Caption updates: "Listening" → "Heard you" → result text.
   - Partial transcript shown live below the caption.
3. If `false` → fall back to the system intent.

Permission is requested via `recordPermissionLauncher`. The overlay swallows
BACK (cancels recognition cleanly).

---

## 10. Settings & reset flow

`SettingsTvFragment` exposes Profile, Theme, About, Cache Info, and **Reset &
Restart**. Reset wipes:
- `KStreamDatabase` (movies cache, watch progress, likes, downloads, recs).
- `EnrichmentDatabase` (TMDb cache).
- `KStreamDataStore` (including `first_launch_completed`).
- Glide memory + disk caches.

Then it relaunches the app via an **explicit** `ComponentName` intent to
`SplashActivity` with `EXTRA_FORCE_WELCOME=true`. We don't use
`getLaunchIntentForPackage()` because on a `LEANBACK_LAUNCHER`-only app it can
return null or the wrong Activity.

To defend against the DataStore-flush race when killing the process, the new
`SplashActivity` re-writes `setFirstLaunchCompleted(false)` if the extra is
present, then routes to Welcome.

---

## 11. Design system & device tiers

### 11.1 Brand

- Brand orange/pink accent: `accent_primary` / `accent_gold` (currently
  `#FFE91E63`).
- Surface dark: `banner_bg` `#FF08090C`.
- Logo: `kstream_logo_horizontal.png` (wordmark used in the TV banner) and
  `kstream_logo.png` (square mark for the adaptive icon).
- TV launcher banner: `drawable/tv_banner.xml` layers brand background + gold
  stroke + centered horizontal wordmark. The banner is declared on both
  `<application>` and `SplashActivity` so every launcher resolves it.

### 11.2 Device tier gating

`DeviceTier` (in `app-tv/.../tier/DeviceTier.kt`) classifies the device once
at app start:

| Tier | Heuristic | Visual budget |
|---|---|---|
| LOW | Total RAM ≤ 1.5 GB, heap ≤ 96 MB, or Fire TV Stick Lite/Gen1 model | Flat dark surfaces, gold focus ring, **no animations**, RGB_565 |
| MID | 1.5 – 3 GB RAM | Glow + scale focus, **no blur/parallax**, ARGB_8888 |
| HIGH | > 3 GB RAM (Shield, Cube, Google TV) | Ken Burns hero, glassmorphism, ambient pan, parallax |

Every presenter / Fragment reads `DeviceTier.get(context)` and degrades
gracefully. **Do not** create new animations without a tier guard.

### 11.3 Dropdown panels (cross-screen pattern)

The reusable "in-app dropdown" pattern (used by search scope/sort and the
player quality menu):

- Container: `LinearLayout` with `bg_search_dropdown_panel` background
  (`#11161F` fill, 1 dp `#232A36` stroke, 12 dp radius, `elevation=12dp`).
- Rows: `bg_search_dropdown_row` selector (focus = brand stroke, pressed =
  darker fill, default = transparent).
- Row layout: `[check icon (gold if selected)] [label]` — selected gets the
  accent-tinted check via `imageTintList`.
- BACK on a focused row dismisses the panel and returns focus to the anchor.

If you need a tooltip (e.g. the "Start Over" floating chip on Details), use a
`PopupWindow` with `bg_start_over_tip` and `isClippingEnabled=false`, anchored
above the focused View with a 6 dp gap — that way you don't reflow the row.

---

## 12. TV input contracts (D-pad, IME, BACK)

### 12.1 IME contract (applies to **every** `EditText` in the app)

1. **Do not** open the IME automatically on focus
   (`EditText.showSoftInputOnFocus = false`).
2. Open IME only on **explicit click** or **D-pad CENTER / ENTER**.
3. The IME's confirm action (tick / DONE / GO / NEXT / SEND / SEARCH /
   keyboard ENTER) closes the IME and commits the field.
4. BACK while IME is open closes the IME, **not** the screen.

A shared helper `app-tv/.../ui/common/TvEditTextIme.kt` applies this contract
to the simple cases (`Welcome`). Search and Settings use inline implementations
because they need extra D-pad routing (LEFT/RIGHT to side-nav etc.) but follow
the same contract.

### 12.2 Side-nav focus

`SideNavController` owns the left rail (Home, Search, History, Liked,
Settings, Refresh). It registers `nextFocusRight` on each item to the first
focusable in the screen content, and `nextFocusLeft` on every content area
back to the corresponding nav item.

### 12.3 BACK across screens

| Screen | BACK |
|---|---|
| Splash | exit |
| Welcome / Terms | exit |
| Main / Home | toast "Press again to exit"; second BACK exits |
| Details | finish |
| Player (controls visible) | hide controls; don't leave |
| Player (controls hidden) | finish |
| Search (voice open) | cancel voice |
| Settings | finish |
| Any in-screen panel (search dropdowns, quality dropdown) | dismiss panel first |

---

## 13. Performance & memory rules

These are the rules that have been earned the hard way — break them and
expect to spend a week on Stick Lite triage.

1. **Never block the main thread on first paint.** No first-time JIT, no
   network calls, no Room queries on the UI thread. `repeatOnLifecycle` +
   `flowOn(Dispatchers.IO)` everywhere.
2. **Glide config gated by tier:** LOW devices use `RGB_565` and disable
   placeholders animations; HIGH devices use `ARGB_8888` and Glide transitions.
   See `KStreamGlideModule`.
3. **No Compose in the TV build.** If you need a new screen, use Leanback /
   View binding.
4. **`MemoryGuardian`** clears Glide memory + disk caches on
   `onLowMemory` / `onTrimMemory`. Wire any new caches into the guardian.
5. **`TvCrashHandler`** writes a crash counter to disk. After **3** crashes
   inside 30 minutes, `SafeMode` enables: flat dark UI, no animations, no
   enrichment images — only the catalog. The user still gets a working app.
6. **`AnrWatchdog`** posts a heartbeat every 1 s on the main thread; if a beat
   is missed by 5 s it logs an ANR breadcrumb to `Log.wtf` so it's visible in
   `logcat`.
7. **Hero auto-rotation pauses on focus.** Never animate a focused row.
8. **Avoid PopupMenu / AlertDialog.** They use the system theme and look out
   of place on TV. Use the in-app dropdown panel pattern (§11.3) and
   `AppConfirmDialog` (in `ui/common`) instead.

---

## 14. Build, install, debug

### 14.1 One-time setup

1. Install JDK 17 (project pins JVM target 17). On Windows the build script
   used by the team is:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot"
   ```
2. Copy `local.properties.example` → `local.properties` and fill in:
   ```properties
   sdk.dir=C:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
   TMDB_API_KEY=your_tmdb_v3_key_here
   ```
   The TMDb key is injected at build time via `BuildConfig.TMDB_API_KEY`.
   Without it the catalog still works but every tile shows the Supabase
   poster only — no logo, cast photos, reviews, tagline.
3. Supabase credentials are read by `:core:network` from… (check
   `network/build.gradle.kts` for the exact `BuildConfig` field; if it's not
   there yet, hard-code your env values during local bring-up).

### 14.2 Common commands

```powershell
# Clean build of the TV APK
.\gradlew.bat :app-tv:clean :app-tv:assembleDebug --no-daemon

# Incremental debug build (preferred during dev)
.\gradlew.bat :app-tv:assembleDebug --no-daemon

# Install on the connected Fire TV
C:\Users\YOU\Android\Sdk\platform-tools\adb.exe install -r `
  app-tv\build\outputs\apk\debug\app-tv-debug.apk

# Tail the app's logs
adb logcat KStreamTvApp:V KStreamNetwork:V DeviceTier:I AndroidRuntime:E *:S

# Module-level unit tests
.\gradlew.bat test
```

The release variant is built by hand as needed; APKs that ship to the team are
committed under `apks/` (and the latest debug APK is force-added under
`app-tv/build/outputs/apk/debug/` because that folder is gitignored — yes,
this is intentional for now).

### 14.3 Fire TV ADB pairing (one-time per device)

1. Fire TV ▸ Settings ▸ My Fire TV ▸ Developer options ▸ ADB debugging ON.
2. On your machine: `adb connect <fire_tv_ip>:5555`.
3. Approve the prompt on the TV.

---

## 15. Coding conventions

- **Kotlin idiomatic:** prefer `data class` over `class`, `val` over `var`,
  `when` over `if/else if` chains, `Flow`/`StateFlow` over LiveData.
- **Constructor injection only.** No `@Inject lateinit var` field injection.
- **Repository interfaces in `:core:domain`, impls in `:core:data`,
  registered in `DataModule`.** Use cases call repositories, never network or
  Room directly.
- **ViewModels expose `StateFlow<UiState>`** + a few `suspend fun on…()`
  intents. No `Result<T>` leaking to UI — wrap in the UiState shape.
- **Errors:** repositories return `Flow<...>` of cached data + log network
  failures; ViewModels surface `error: String?` in their UiState. The UI shows
  an inline error card with a Retry button (`btn_retry*`).
- **Coroutines:** `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { … } }`
  for any Fragment-side collection. Never collect from `lifecycleScope` of a
  Fragment without `repeatOnLifecycle`.
- **Resource naming:**
  - Layouts: `fragment_*`, `activity_*`, `item_*`.
  - Drawables: `bg_*` (backgrounds), `ic_*` (icons), `<feature>_<kind>`.
  - IDs: snake_case, namespaced (`voice_overlay`, `quality_panel`,
    `start_over_tip`).
- **Comments:** explain *why* (especially TV quirks, Fire TV bugs). Don't
  comment obvious code.
- **Commit messages:** conventional-ish — `feat(tv): …`, `fix(player): …`,
  `build(tv): refresh debug APK`. No co-author trailer unless explicitly
  requested.

---

## 16. Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| Reset & Restart lands on Home, not Welcome | `getLaunchIntentForPackage()` on `LEANBACK_LAUNCHER`-only app + DataStore flush race | Explicit `ComponentName` intent to `SplashActivity` with `EXTRA_FORCE_WELCOME=true`; re-write the flag in the new `SplashActivity.onCreate` |
| Voice search shows "Choose Google / Google Speech Services" chooser | Used `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` | Use `SpeechRecognizer.createSpeechRecognizer()` directly + our overlay |
| Soft keyboard opens just by D-pad focus | `EditText` default behaviour | `showSoftInputOnFocus=false`; open from click / DPAD_CENTER; see §12 |
| Tick button on IME doesn't close keyboard | Only listened for `IME_ACTION_DONE` | Recognize all of `DONE/GO/NEXT/SEND/SEARCH` *and* `KEYCODE_ENTER` |
| Voice mic ring gets clipped on the edges | Ring `scale` exceeded its parent | Wrap in a bigger box with `clipChildren=false` and `clipToPadding=false`; cap pulse at ~1.12× |
| Quality popup looks "Android-default" on a brand-themed app | Used `PopupMenu` | Use the in-app dropdown panel pattern (§11.3) |
| PowerShell `-replace` silently doesn't apply | Pattern didn't match; `Set-Content` writes unchanged content with no error | Verify with `Get-Content -Tail`; prefer the `edit` tool for XML edits |
| ANR on Stick Lite cold start | Was Compose JIT, fixed by Leanback rewrite; recurs if you add a heavy first-frame view | Profile with `am profile start`; gate behind `DeviceTier.HIGH` |
| Audio keeps playing after BACK | Fragment recreated mid-playback released `PlayerView` but not the `PlayerManager` instance | Already fixed: `PlayerManager.release()` only runs in `PlayerViewModel.onCleared` |
| TV launcher tile is an empty rectangle | `tv_banner.xml` had no foreground | Layer the wordmark on the brand surface; ensure `android:banner` is set on the `LEANBACK_LAUNCHER` activity |
| Logo not visible in Fire TV launcher row | Banner shown, not icon — and the banner was empty | Same fix as above |

---

### Where to look next

- New screen → start with the existing Activity + Fragment pair that's closest
  (e.g. `LikedMoviesActivity` is the cleanest "personal grid" template).
- New rail on Home → look at `HomeRowsFragment` + `MovieCardPresenter` +
  `RailCardSizing`.
- New TMDb field → extend `MovieEnrichment` → `MovieEnrichmentEntity` →
  bump `EnrichmentDatabase` version → expose via `EnrichmentRepository.observe`.
- New Edge Function → add a method to `KStreamNetworkDataSource`, implement in
  `SupabaseKStreamNetworkDataSource`, expose through the matching repository
  in `:core:data`.

Welcome aboard. Read `APP_SPEC.md` for the raw Supabase schema, then trace
**Splash → Welcome → Home → Details → Player** end-to-end before you make
your first change — it'll save you days.
