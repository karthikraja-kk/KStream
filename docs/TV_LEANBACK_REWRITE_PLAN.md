# KStream TV — Leanback Rewrite & Premium UX Plan

> **Branch:** `kstream-tv` (TV-only, forked from `kstream-bootstrap`)
> **Status:** Plan approved · Decisions locked · Awaiting implement signal
> **Owner:** Karthik Raja
> **Goal:** OTT-grade TV app that runs flawlessly on 1 GB Fire TV Stick Lite *and* feels premium on Shield/Google TV.

---

## 1. Why this rewrite exists

The current TV build uses Jetpack Compose. On low-end Fire TV (1 GB RAM, weak CPU) it crashes with ANRs, not exceptions — the OS kills the process because Compose's first-render JIT compilation blocks the main thread for >5s. Logs repeatedly show:

```
art: Compiler allocated 4-6MB to compile [Composable]
```

No amount of Lite Mode, MemoryGuardian, or baseline profiles fully eliminates this on the lowest tier. Every premium OTT app on the same device (Netflix, Prime, Hotstar, Disney+, YouTube TV) uses **Android Leanback** (native Views). They feel fast because the framework is AOT-compiled, focus is handled by a C++ engine, and RecyclerView recycling is native.

**This rewrite ports the TV UI from Compose to Leanback while keeping every byte of the data, repository, and ViewModel layers untouched.**

---

## 2. Hard constraints (non-negotiable)

1. **Zero changes to data layer.** Models, Room schema, repositories, DAOs, network layer — frozen. TV UI consumes what's already there.
2. **All existing ViewModels reused.** `StateFlow` works fine with Leanback fragments via `repeatOnLifecycle`.
3. **Min SDK 22** for TV (Fire TV Stick Lite is API 22 / Android 5.1).
4. **No mobile code on this branch.** `kstream-tv` is TV-only; the Compose `:app` module is removed/excluded.

---

## 3. Locked decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Branch name | `kstream-tv` |
| 2 | Mobile in this branch | None — remove `:app`, keep only TV + `:core:*` + `:feature:*` |
| 3 | Home hero | Auto-rotating pager — top 5 trending, 7s interval, pauses on focus |
| 4 | First launch | Cinematic splash + welcome screen (one-time) |
| 5 | Image loader | Glide |
| 6 | Downloads | Skipped entirely on TV |
| 7 | Cast row | **Cast photos via TMDb when available**, initial-letter avatar as fallback per member, names-only row if TMDb match failed entirely |
| 8 | Trailer action | Not shown (skipped entirely) |
| 9 | Metadata enrichment | **TMDb API** (free) — new `:core:enrichment` module with Room cache |
| 10 | TMDb fields surfaced | tagline · logo · rating badge · cast photos · reviews · certification · better backdrops |
| 11 | Trailer playback | Skipped (TMDb has trailer keys but feature dropped) |
| 12 | API key storage | `local.properties` → `BuildConfig.TMDB_API_KEY` (gitignored) |

---

## 4. Premium visual direction — "Cinematic Dark Premium"

The UI is **not** a port of the current Compose look. We design fresh for a premium OTT feel, gated by device tier so even the cheapest box feels intentional.

### 4.1 Design pillars
- **Cinematic** — full-bleed imagery, gradients, atmosphere over chrome
- **Confident** — large typography, generous spacing, deliberate motion
- **Focused** — one focus target glows; everything else recedes
- **Performant** — premium effects gated to capable hardware

### 4.2 Color system
| Token | Hex | Usage |
|---|---|---|
| `bg.base` | `#08090C` | App background (near-black, slight blue) |
| `bg.surface` | `#12141A` | Cards, sheets |
| `bg.overlay` | `#000000B3` | Modal scrims (70% black) |
| `text.primary` | `#FFFFFF` | Headlines |
| `text.secondary` | `#B8BCC8` | Metadata |
| `text.tertiary` | `#6B7280` | Hints |
| `accent.gold` | `#E8C36A` | Focus glow, KStream brand pop |
| `accent.gold.muted` | `#B89548` | Pressed/secondary |
| `state.error` | `#FF5C5C` | Errors |
| `gradient.hero` | `linear(0°, bg.base 0%, transparent 60%)` | Hero text legibility |

### 4.3 Typography
- **Display:** Inter (variable, weight 700) — hero titles, section headers
- **Body:** Inter Regular 400 — metadata, descriptions
- **Numerals:** Inter Tabular — runtimes, years, progress
- **Sizes (TV-tuned, 10 ft viewing):**
  - Hero title: 56sp
  - Section header: 28sp
  - Card title: 18sp
  - Metadata: 14sp

### 4.4 Motion language
- Standard easing: `FastOutSlowIn`, 220ms
- Focus scale: 1.0 → 1.08 over 180ms
- Focus glow: gold drop shadow, 12dp blur, fades in 220ms
- Hero crossfade: 600ms
- Ken Burns zoom: 8s linear, 1.0 → 1.06
- Shimmer skeletons: 1.4s loop, never spinners

### 4.5 Effects gated by device tier

| Effect | LOW (Fire TV Lite) | MID (1.5–3 GB) | HIGH (Shield, Google TV 4K) |
|---|:---:|:---:|:---:|
| Solid dark theme | ✓ | ✓ | ✓ |
| Bold focus ring (gold border, no glow) | ✓ | — | — |
| Soft gold glow + 1.08× scale | — | ✓ | ✓ |
| Hero crossfade | ✓ | ✓ | ✓ |
| Ken Burns zoom | — | — | ✓ |
| Backdrop blur (glassmorphism) | — | — | ✓ |
| Parallax tilt on focus | — | — | ✓ |
| Ambient backdrop pan (details idle) | — | — | ✓ |
| Shimmer skeletons | ✓ | ✓ | ✓ |

LOW tier feels like **Apple TV minimalism** — clean, sharp, fast. HIGH tier feels like **Netflix richness** — alive, atmospheric. Same app, different design language per tier.

---

## 4.6 TMDb enrichment (new pillar)

Local data layer is frozen — but we layer **TMDb metadata on top** via a new parallel module `:core:enrichment`. Local `Movie` stays untouched; enrichment is joined at view-time.

**What it gives us:**
- Tagline + better synopsis
- IMDb-style rating badge (gold star + score)
- Cast photos when available (per-member fallback to initial-letter avatar, full row falls back to names-only if no TMDb match)
- Movie logo (transparent PNG → premium hero text)
- Higher-quality backdrops in multiple sizes (perfect for tier-aware Glide loading)
- Reviews (top 1-2 shown on Details)
- Certification per country (PG-13, U/A, etc.)

**Matching strategy (Movie → TMDb):**
1. `GET /search/movie?query={movieName}&year={year}&language={language}`
2. Score candidates: exact title (+50), year match (+30), year ±1 (+10), popularity (+0-20), has poster (+5)
3. Confidence ≥70 → auto-enrich · 40-69 → enrich + flag low-confidence · <40 → skip
4. Tiebreaker: overlap with local `director` + `castMembers` arrays
5. Routing: local `type=="movie"` → `/search/movie`; `type=="tv"` → `/search/tv`

**Single-call detail fetch (no chatty requests):**
```
GET /movie/{id}?append_to_response=credits,images,videos,reviews,release_dates
```
Returns everything in one round-trip.

**Cache strategy:**
- New Room table `movie_enrichment` keyed by local `Movie.id`
- **Cache forever** — refresh manually via Settings → "Refresh metadata" or after 30 days
- Skip enrichment on LOW tier when `MemoryGuardian` is in WARNING+
- Throttle: max 10 req/s from our side (50 is TMDb limit, being polite)
- Lazy: only enrich visible cards + opened Details, never bulk-enrich

**Attribution:** "Powered by TMDb" + logo on Settings → About card (per TMDb ToS).

---

## 5. Module architecture

```
KStream/ (kstream-tv branch)
├── app-tv/                  NEW — Leanback application module
│   ├── ui/
│   │   ├── splash/          SplashActivity + welcome flow
│   │   ├── browse/          MainBrowseFragment (Home), VerticalGridFragment (Browse-All)
│   │   ├── details/         DetailsActivity, DetailsFragment, presenters
│   │   ├── player/          PlayerActivity, PlaybackVideoFragment (Glue)
│   │   ├── search/          SearchActivity, SearchFragment
│   │   ├── settings/        SettingsActivity (Preference screens)
│   │   └── error/           SafeModeActivity, ErrorFragment
│   ├── presenter/           CardPresenter, HeroPresenter, DetailsDescriptionPresenter
│   ├── adapter/             ArrayObjectAdapter helpers
│   ├── tier/                DeviceTier detector (LOW/MID/HIGH)
│   ├── focus/               FocusRing drawable, focus helpers
│   ├── anr/                 ANRWatchdog (NEW)
│   ├── safe/                SafeModeGuard (5 crashes / 5 min → minimal UI)
│   └── KStreamTvApp.kt      Application + Hilt + MemoryGuardian + Glide config
├── core/
│   ├── common/              UNCHANGED
│   ├── model/               UNCHANGED (Movie, Media, etc. — frozen)
│   ├── network/             UNCHANGED
│   ├── datastore/           UNCHANGED
│   ├── database/            UNCHANGED
│   ├── domain/              UNCHANGED
│   └── enrichment/          NEW — TMDb client + Room cache + matcher
│       ├── api/             TmdbApi (Retrofit), DTOs
│       ├── db/              EnrichmentDao, MovieEnrichmentEntity
│       ├── repository/      EnrichmentRepository (lazy fetch + cache)
│       ├── matcher/         TmdbMatcher (scoring algorithm)
│       └── di/              EnrichmentModule (Hilt)
├── feature/                 UNCHANGED (all ViewModels reused as-is)
│   ├── home/                HomeViewModel exposes rails → ListRow
│   ├── details/             DetailsViewModel
│   ├── player/              PlayerViewModel + PlayerManager (ExoPlayer)
│   └── search/              SearchViewModel
└── [REMOVED on this branch: app/ (mobile), feature/downloads usages]
```

### 5.1 Dependencies added/removed
**Add to `app-tv`:**
- `androidx.leanback:leanback:1.2.0`
- `androidx.leanback:leanback-preference:1.2.0`
- `com.github.bumptech.glide:glide:4.16.0`
- `com.github.bumptech.glide:okhttp3-integration:4.16.0`

**Add to `core:enrichment`:**
- Retrofit + OkHttp (already in project)
- Moshi or kotlinx-serialization (already in project)
- Room (already in project)
- `BuildConfig.TMDB_API_KEY` from `local.properties`

**Remove from TV scope:**
- `androidx.compose.*`, `androidx.tv.material3`, `androidx.tv.foundation`
- `io.coil-kt:coil-compose` (mobile only, not on this branch)

**Keep shared:**
- ExoPlayer (Media3), Hilt, Coroutines, Room, DataStore, Retrofit, Moshi/kotlinx-serialization

---

## 6. Data layer mapping (confirmed reuse)

| Existing | Leanback consumer |
|---|---|
| `HomeUiState.rails: List<MovieRail>` | `ArrayObjectAdapter<ListRow>` in `MainBrowseFragment` |
| `HomeUiState.heroMovies: List<Movie>` | Custom hero pager view above rows |
| `Movie` model | `CardPresenter` for `ImageCardView` |
| `MovieWithMedia` | `DetailsActivity.intent.extra` |
| `WatchProgress` | Progress bar overlay on cards (already in state) |
| Search prefixes (`history:`, `liked:`, `all:`, `recommended:`, `genre:`, `year:`) | Wired into `SearchFragment.onQueryTextChange` |
| `PlayerUiState.qualities` | Settings menu in `PlaybackVideoFragment` |
| `PlayerUiState.FUNNY_MESSAGES` | Link-refresh overlay |
| `PlayerManager` (ExoPlayer wrapper) | `LeanbackPlayerAdapter(playerManager.player)` |

No model changes. No repo changes. No ViewModel changes.

---

## 7. Crash & memory resilience (5 layers)

| Layer | Status | Catches |
|---|---|---|
| 1. `UncaughtExceptionHandler` | Reused from `kstream-bootstrap` | Java exceptions |
| 2. **ANR Watchdog** (NEW) | Build fresh | Main thread stalls (5s ping) |
| 3. Crash loop guard | Reused | 3 crashes in 60s → factory reset state |
| 4. `MemoryGuardian` | Reused (Glide cache instead of Coil) | OOM pressure (4 levels) |
| 5. **Safe Mode** (NEW) | Build fresh | 5+ crashes in 5 min → minimal text-only UI |

### 7.1 Safe Mode behavior
- Strips down to a `RecyclerView` of titles only (no images, no animations, no hero)
- Banner at top: "Running in Safe Mode — tap here to retry full UI"
- Persists until user explicitly retries OR 24 hours pass

### 7.2 ANR Watchdog
- Background `HandlerThread` pings main thread every 2s
- If main doesn't respond in 5s → log stack trace + save state + restart to last screen
- Active only on LOW/MID tiers (HIGH tier turns it off, no need)

---

## 8. Device tier detection

Detect once at app start, cache in `DeviceTier.current`.

```
LOW:  RAM < 1.5 GB              OR model matches Fire TV Stick Lite/Gen 1
MID:  RAM 1.5–3 GB              AND not in LOW list
HIGH: RAM > 3 GB                AND (Shield TV / Google TV 4K / Mi Box S 2nd gen+)
```

Used by:
- Image sizes (Glide `.override()`)
- Cache budgets (Glide memory cache % of heap)
- Animation toggles
- Hero auto-rotate (HIGH only; LOW/MID = static after 1st)
- Ken Burns / blur / parallax flags
- ANR watchdog active

| Setting | LOW | MID | HIGH |
|---|---|---|---|
| Card tile size | 360×540 | 480×720 | 720×1080 |
| Glide mem cache | 8% heap | 12% | 15% |
| Bitmap config | RGB_565 | ARGB_8888 | ARGB_8888 |
| Hero rotate | ✗ | ✓ (no Ken Burns) | ✓ (with Ken Burns) |
| Rows preloaded | 2 | 4 | All |
| Cards per row preloaded | 5 | 8 | 12 |

---

## 9. Screen-by-screen UX

### 9.1 Splash (first launch + every cold start)
- 1.2s minimum, KStream wordmark fades up, gold underline draws across
- Behind the scenes: tier detection, Hilt init, MemoryGuardian start, initial repo warmup
- Cold-start budget: < 2.0s to Home on HIGH, < 3.5s on LOW

### 9.2 Welcome (first launch only)
- 3 cards swipe with D-pad: "Stream", "Discover", "Continue Watching"
- Each card: full-bleed image + headline + 1-line sub
- "Get Started" pill button (gold) on last card → Home
- Skippable with BACK → flag set, never shown again

### 9.3 Home (Browse)
```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│        [Hero — 60% of screen, auto-rotates]             │
│        Title (56sp)                                     │
│        Genre · Year · Runtime                           │
│        [▶ Play]  [+ My List]  [ⓘ Info]                  │
│                                                         │
├─────────────────────────────────────────────────────────┤
│ Continue Watching                                       │
│ [card] [card] [card] [card] [card] →                    │
├─────────────────────────────────────────────────────────┤
│ Trending Now                                            │
│ [card] [card] [card] [card] [card] →                    │
├─────────────────────────────────────────────────────────┤
│ Because you watched X                                   │
│ [card] [card] [card] [card] [card] →                    │
└─────────────────────────────────────────────────────────┘
```
- Hero pager: 5 trending, 7s interval, pauses on focus or D-pad activity
- Rails come from `HomeViewModel.uiState.rails` — no changes
- D-pad: UP from first row goes to top nav (Search · Home · My List · Settings)
- LEFT from first card in a row goes to top nav
- Long press DOWN on a card → quick actions sheet (Play / Add to List / Details)

### 9.4 Details
```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│        [Backdrop image, full-bleed]                     │
│                                                         │
│        Title (56sp)                                     │
│        2024 · 2h 14m · ★ 8.2 · HD                       │
│        Drama · Thriller                                 │
│                                                         │
│        [▶ Resume 1:23:45]  [+ My List]  [♥ Like]        │
│                                                         │
│  Description text wraps to 2-3 lines max                │
├─────────────────────────────────────────────────────────┤
│ Cast                                                    │
│ Name · Name · Name · Name · Name                        │
├─────────────────────────────────────────────────────────┤
│ More Like This                                          │
│ [card] [card] [card] [card] [card] →                    │
└─────────────────────────────────────────────────────────┘
```
- HIGH tier: ambient backdrop slow-pan after 5s idle
- Resume button shows `WatchProgress.positionMs` if > 0, else "Play"
- No Trailer button (decision 8)
- **Cast row (decision 7)** — horizontal `RecyclerView` of circular avatars + names below:
  - For each name in local `Movie.castMembers`, look up TMDb credits by name match
  - If TMDb has `profile_path` → load via Glide into circular avatar (96dp on HIGH, 72dp on MID, 56dp on LOW)
  - If TMDb knows the actor but has no photo → gold-on-dark initial-letter avatar
  - If movie's TMDb match failed entirely → degrade to plain `TextView` row, names joined by ` · `
  - Limit: first 10 cast members; truncate with "and N more" pill at the end
- BACK from details → previous screen (Home or Search)

### 9.5 Player
- Pure black background, ExoPlayer surface
- Controls hidden by default; appear on D-pad press, fade after 4s
- Minimal control bar (bottom): progress bar + current time / total
- D-pad LEFT/RIGHT while controls hidden → 10s seek (with ripple animation at edge)
- D-pad LEFT/RIGHT while controls visible → focus prev/next control
- UP from progress bar → quality / subtitle menu
- BACK → exit player → return to Details (NOT home, kills any back-stack between)
- Link refresh: show full-screen overlay with rotating `FUNNY_MESSAGES`
- On error: 3 retries auto, then show retry/back dialog

### 9.6 Search
```
┌─────────────────────────────────────────────────────────┐
│  Search ▎                                               │
│  ┌─────────────────┐  ┌──────────────────────────────┐  │
│  │ Q W E R T Y U I │  │ Results grid (5 cols)        │  │
│  │ O P A S D F G H │  │ [card] [card] [card] [card]  │  │
│  │ J K L Z X C V B │  │ [card] [card] [card] [card]  │  │
│  │ N M . SPACE ⌫   │  │                              │  │
│  └─────────────────┘  └──────────────────────────────┘  │
│                                                         │
│  Suggestions: history · trending searches               │
└─────────────────────────────────────────────────────────┘
```
- Voice search button if device supports it
- Live results as user types (debounced 300ms)
- Reserved prefixes auto-detected (`history:`, `liked:`, etc.) — show as chips above results
- "Did you mean?" line under search box uses `SearchUiState.suggestedQuery`

### 9.7 Browse-All (See more from rail)
- Vertical grid, 5 columns, infinite scroll
- Title at top: rail name + count
- Same `CardPresenter` as Home rails

### 9.8 Settings (card grid, not list)
- Account · Playback · Display · About · Diagnostics
- Each card: icon + label, 4×2 grid
- Diagnostics card opens MemoryGuardian status + tier info + crash count

### 9.9 Error / Safe Mode
- Friendly message + retry pill
- Safe Mode banner if active
- "Report issue" hint with anonymous diagnostic dump option

### 9.10 Welcome-back (returning users)
- Skipped — go straight from Splash to Home
- Continue Watching rail at top of Home does the personalization

---

## 10. Navigation topology

```
Splash ──first?──► Welcome ─► Home (top-level)
              └─not first?─► Home
Home ─► Details ─► Player ─► (BACK to Details, NOT Home)
Home ─► Search ─► (results) ─► Details
Home ─► Browse-All ─► Details
Home ─► Settings
Any ─► SafeMode (on crash threshold) ─► (manual retry) ─► Home
```

D-pad rules:
- BACK from Player → Details
- BACK from Details → previous (Home or Search)
- BACK from Home → exit (with confirm if playing)
- UP from any first row → top nav bar
- Top nav bar: Search · Home · My List · Settings

---

## 11. Implementation phases (19–22 days)

| # | Phase | Days | Outcome |
|---|---|:---:|---|
| **P0** | Branch + module setup | 1 | `kstream-tv` branch, `:app-tv` skeleton, Hilt wires, manifest, theme |
| **P1** | Strip mobile, fix builds | 0.5 | Remove `:app`, ensure `:app-tv` + `:core:*` + `:feature:*` build clean |
| **P2** | Device tier + Glide + theme | 1 | `DeviceTier`, Glide config, dark theme, typography, color tokens |
| **P3** | Splash + Welcome | 1 | Cold-start budget met, welcome flow once-only |
| **P4** | Home Browse skeleton | 2 | `MainBrowseFragment`, rails wired from `HomeViewModel`, basic `CardPresenter` |
| **P5** | Hero pager | 1.5 | Auto-rotating top 5, focus-pause, tier-gated Ken Burns |
| **P6** | CardPresenter polish | 1 | Focus glow/ring per tier, progress overlay, shimmer skeletons |
| **P6.5** | TMDb enrichment module | 2 | `:core:enrichment` module, TmdbApi, Room cache, matcher with confidence scoring, BuildConfig key wiring |
| **P7** | Details screen (with enrichment) | 2.5 | Full layout, backdrop, actions, cast photos, tagline, rating badge, certification, reviews, More-Like-This, ambient pan (HIGH) |
| **P8** | Player | 2.5 | `PlaybackVideoFragment` + `LeanbackPlayerAdapter`, 10s skip, controls fade, settings menu |
| **P9** | Link refresh + funny messages | 0.5 | Overlay using `FUNNY_MESSAGES`, retry flow |
| **P10** | Search | 1.5 | Split keyboard + grid, prefixes, live results |
| **P11** | Browse-All (vertical grid) | 1 | See-more pages |
| **P12** | Settings (card grid) | 1 | Preference fragments + diagnostics card |
| **P13** | ANR Watchdog + Safe Mode | 1.5 | Watchdog thread, Safe Mode activity, crash threshold tracking |
| **P14** | QA pass on real Fire TV Lite | 1.5 | Cold start, 30-min soak, navigation stress, memory profile |

**Total: 21.5 days minimum, 24-25 with polish buffer** (added 2.5 days for P6.5 enrichment + extended P7).

---

## 12. Reuse vs rewrite

| Component | Action |
|---|---|
| `:core:model`, `:core:network`, `:core:datastore`, `:core:database`, `:core:domain` | Reuse |
| `MemoryGuardian`, `AppState`, `NetworkMonitor`, crash recovery prefs | Reuse |
| All `:feature:*` ViewModels | Reuse |
| `PlayerManager` (ExoPlayer wrapper) | Reuse |
| `CustomDownloadManager` | Not used on TV |
| Compose UI in `:app` | Removed from `kstream-tv` branch |
| `:feature:home/ui`, `:feature:details/ui`, `:feature:player/ui`, `:feature:search/ui` Composables | Removed from `kstream-tv` branch |
| Navigation graphs (Compose Navigation) | Rewritten as Activity-based + Leanback Fragments |
| Image loading (Coil) | Replaced by Glide |
| Crash handler / Application class | Ported to `KStreamTvApp` |

---

## 13. Performance & memory budgets

| Metric | LOW target | MID | HIGH |
|---|---|---|---|
| Cold start to Home | < 3.5s | < 2.5s | < 2.0s |
| Frame drops (60fps target) | 0 in 30s navigation | 0 in 60s | 0 in 5min |
| Steady-state heap | < 60 MB | < 90 MB | < 140 MB |
| Glide cache | 8% heap | 12% | 15% |
| ANR count (30 min soak) | 0 | 0 | 0 |
| Crash count (30 min soak) | 0 | 0 | 0 |

---

## 14. Acceptance criteria

1. ✅ Installs and launches on Fire TV Stick Lite (1 GB, API 22)
2. ✅ Zero ANRs in 30-minute navigation soak on Lite
3. ✅ Zero crashes across all tiers in 30-minute soak
4. ✅ 60fps focus and scroll on all tiers (measured with `gfxinfo`)
5. ✅ Cold start under tier budgets above
6. ✅ Hero pager smooth on HIGH, static fallback graceful on LOW
7. ✅ Safe Mode triggers after 5 crashes / 5 min and recovers
8. ✅ All existing data flows (Home rails, Details, Player, Search) work unchanged
9. ✅ Visual QA matches premium OTT bar on HIGH tier
10. ✅ LOW tier feels intentional (Apple TV minimal aesthetic), not crippled

---

## 15. Open items (post-MVP, not blocking)

- Voice search refinement
- Profiles / multi-user
- Trailer playback in-app (TMDb gives keys, deferred for now)
- OMDb integration for additional rating sources (Rotten Tomatoes, Metacritic)
- Picture-in-picture mini-player (Android TV 12+ only)
- Watch party / shared sessions
- Recommendations on home screen channel (TV launcher integration)
- Baseline profile regeneration with macrobenchmark

---

## 16. Out of scope (will NOT do)

- Any change to `:core:model` (Movie/Media/etc.)
- Any change to repositories or DAOs
- Any change to network / API contracts
- Mobile app changes on this branch
- New backend endpoints
- Downloads on TV
- Trailer button without backend data
- Compose anywhere in `:app-tv`
- Writing enrichment data back to your backend
- Modifying TMDb API key for commercial use (Developer tier only)

---

**End of plan. Ready to execute when given the go signal.**
