# Architecture

Developer notes for [Sannyas](../README.md). Everything here is about how the app
is put together, not what it does — the README covers that.

`minSdk 24`, `targetSdk 36`, `compileSdk 36`. Kotlin 2.0, AGP 8.13, single module.

---

## Building

The Gradle wrapper is checked in, so a clean clone needs nothing but a JDK 17+
and the Android SDK:

```bash
./gradlew assembleDebug
```

A release build additionally needs signing credentials. Put them in
`local.properties` (git-ignored) or export them as environment variables:

```
RELEASE_STORE_FILE=/path/to/release.jks
RELEASE_STORE_PASSWORD=…
RELEASE_KEY_ALIAS=…
RELEASE_KEY_PASSWORD=…
```

Without them the release build still runs — it just produces an unsigned
artifact rather than failing, so anyone can verify that R8 is behaving.

```bash
./gradlew bundleRelease      # AAB for Play
./gradlew assembleRelease    # APK for direct distribution
```

---

## Layout

```
com.sannyas.player
├── domain/                 Pure Kotlin. No Android, no Media3, no Room.
│   ├── model/              Discourse, Track, PlayerState, DownloadStatus…
│   └── repository/         Interfaces the UI depends on
│
├── data/                   Implementations of the domain interfaces
│   ├── local/catalog/      Parses assets/discourses.json into memory
│   ├── local/database/     Room entities + DAOs (user data only)
│   ├── local/datastore/    Preferences
│   ├── download/           Media3 DownloadManager wiring
│   └── repository/         Catalog / UserData / Download repositories
│
├── playback/               PlaybackService, PlayerConnection, SleepTimer, MediaTree
├── core/                   Design system (theme, components) + shared utilities
├── ui/                     One package per screen: ViewModel + Composables
└── di/                     Hilt modules
```

---

## Two rules that shape everything

**The service owns the player.** There is exactly one `ExoPlayer`, inside
`PlaybackService`. The UI never touches it — it talks to a `MediaController`
through `PlayerConnection`, which republishes player callbacks as a single
`StateFlow<PlayerState>`. Every surface (mini player, full player, track rows,
queue) reads that one state, so they cannot disagree about what is playing, and
playback survives the Activity being destroyed.

**Catalog content never enters the database.** Room stores only user-generated
state — favourites, history, resume positions, bookmarks, recent searches —
keyed by opaque catalog ids. That means `discourses.json` can be replaced
wholesale without a schema migration.

---

## The catalog

`app/src/main/assets/discourses.json`:

```json
{
  "version": 1,
  "categories": [ { "name": "Zen And Zen Masters", "discourseCount": 16 } ],
  "discourses": [
    {
      "id": "a-bird-on-the-wing-01-11",
      "title": "A Bird on the Wing",
      "description": "…",
      "image": "https://oshoworld.com/uploads/series-a-bird-on-the-wing.jpg",
      "category": "Zen And Zen Masters",
      "duration": "17:27:10",
      "speaker": "Osho",
      "tags": ["zen", "koan", "stillness"],
      "tracks": [
        {
          "title": "A Bird on the Wing 01",
          "url": "https://oshoworld.com/wp-content/uploads/newAudios/…__01.mp3",
          "duration": "01:37:49"
        }
      ]
    }
  ]
}
```

Every field except `title`, `tracks[].title` and `tracks[].url` is optional —
the parser runs with `ignoreUnknownKeys` and recomputes missing durations from
the track list, so the file can be hand-edited or swapped for a different
catalog without touching code. Track ids are derived positionally
(`<discourseId>#<n>`), so reordering a series' tracks will detach existing
resume positions for it.

Note that most source filenames contain spaces; URLs in the catalog are already
percent-encoded.

The file ships uncompressed (`noCompress += "json"`) so it can be memory-mapped
quickly at startup.

### Regenerating it

`tools/build_catalog.py` rebuilds the file from harvested OshoWorld metadata.
It carries over only factual metadata — titles, artwork, durations and audio
URLs — and generates the series blurbs itself. The discourse transcripts
published on the source site are deliberately **not** copied into the app.

---

## Playback

- Background playback with notification and lock-screen controls
- Bluetooth, wired headsets, and media-button handling
- Android Auto / Assistant browsing via `MediaLibraryService` (`MediaTree`
  exposes root → categories → series → talks)
- Audio focus handling, and pause-on-becoming-noisy
- Resume positions written every 5 s while playing, plus on player collapse
- Playback speed, shuffle, repeat, queue editing
- Sleep timer (fixed duration or end-of-talk)
- Skip silence — applied by the service, which observes the same DataStore the
  settings screen writes to

### Caching

Two separate Media3 caches, chained in `PlaybackModule`:

- **Download cache** (`filesDir/downloads`) — permanent, `NoOpCacheEvictor`.
  Only `DownloadManager` writes to it; the player reads it read-only, so casual
  listening can never inflate the Downloads storage figure.
- **Stream cache** (`cacheDir/media-cache`) — 512 MB, LRU. This is what
  "Clear cache" in Settings empties.

---

## Design system

Dark-first, with full light-theme and Material You dynamic-colour support.
Everything lives in `core/designsystem`:

- `theme/` — colour roles, the aurora palette, type scale, shape tokens.
  Colour roles cross-fade on theme change rather than snapping.
- `component/` — `AuroraBackground` (drifting multi-blob gradient drawn in
  `drawBehind`, so it never allocates layout nodes), `Artwork` (deterministic
  gradient + initials fallback so cards are never grey boxes), glassmorphism and
  soft-shadow modifiers, shimmer skeletons, cards, buttons, empty/error states.

### Typography

The app ships with the platform font family so it builds with no binary assets.
To switch to Manrope, drop the four TTFs into `app/src/main/res/font/` and
replace the single `OshoFontFamily` definition in `Type.kt` — the whole scale is
defined in terms of it. Instructions are in the KDoc there.

---

## Branding assets

`branding/` holds the store assets and the scripts that generate them. Both
scripts read the shipped launcher vectors rather than duplicating any values, so
the store and the app can never disagree:

```bash
python3 branding/render_icon.py              # 512x512 listing icon
python3 branding/render_feature_graphic.py   # 1024x500 feature graphic
```

The feature graphic also counts its catalog figures out of `discourses.json`
rather than hardcoding them.

---

## Accessibility

- Every interactive element has a ≥48 dp touch target
- Cards and rows merge into single semantics nodes with composed descriptions,
  so TalkBack reads "A Bird on the Wing. 11 talks · 17h 27m" rather than six
  fragments
- Section titles are marked as headings for rotor navigation
- Decorative content (equaliser bars, fallback initials, duplicated labels) is
  cleared from the semantics tree
- Type scales with the system font size; no fixed-height text containers
