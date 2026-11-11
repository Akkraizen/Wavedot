# Architecture

Wavedot is one Gradle build with three top-level areas: `client/`, `server/` and `shared/`. Both the client and the services follow the same rule: **business logic sits in the middle, depends on nothing platform-specific, and talks to the outside world only through interfaces (ports).** UI, storage, networking and audio are adapters around that core.

```
              ┌──────────── client ────────────┐        ┌──────────── server ─────────────┐
 UI           │ ui                             │        │ hub/api        updates/api      │  transport
              │        │                       │        │    │               │            │
 presentation │ presentation/*                 │        │                                 │  ViewModels
              │        │                       │        │    │               │            │
 domain       │ core/domain ── core/contract   │        │ hub/domain     updates/domain   │  pure Kotlin
              │        │          │            │        │    │               │            │
 adapters     │ core/data  infra/*  sources/*  │        │ hub/data       updates/data     │  persistence, IO
              │            plugins/*           │        │                                 │
 wiring       │ app/shell  app/android  app/desktop    │ hub/app        updates/app      │  DI, main()
              └───────────────┬────────────────┘        └────────────────┬────────────────┘
                              └───────────── shared/protocol-* ──────────┘
```

## Client

All client modules are Kotlin Multiplatform with `jvm` (desktop) and Android targets. Code that both JVM-based targets can use goes into the `jvmShared` source set (javax.crypto, `java.io.File`, Ktor server). `commonMain` stays free of JVM APIs so an iOS target can be added later.

### Core

| Module | Package | Responsibility |
|---|---|---|
| `core/model` | `space.wavedot.core.model` | Immutable entities: `Track`, `Album`, `Artist`, `Playlist`, `MediaId`, `Page`, `AudioQuality`, plus stream descriptors (`StreamSource`, `ByteStream`) |
| `core/common` | `space.wavedot.core.common` | Errors (`WavedotError`, `WavedotException`), `Logger`, `DispatcherProvider`, `TimeProvider`, `KeyValueStore` |
| `core/contract` | `space.wavedot.core.contract` | **The single data contract**: `MediaSource`, `Catalog`, `Search`, `Streaming`, `RemoteLibrary`, `EntityProvider<T>`, `PagedQuery<Q, T>`, `MediaSourceRegistry` |
| `core/plugin-api` | `space.wavedot.plugin.api` | The plugin SDK: `WavedotPlugin`, `PluginManifest`, `PluginContext`, `PluginStore`, HTTP byte-stream helper |
| `core/playback-api` | `space.wavedot.playback.api` | Low-level playback ports: `AudioEngine`, `EngineState`, `StreamProxy` |
| `core/domain` | `space.wavedot.core.domain` | Use cases and player logic: `PlayerController`, `PlaybackQueue`, `ResolveStream`, `SearchCatalog`, `LoadCollection`, `ToggleFavorite`, `PlaylistEditor`, `GetLyrics`, `SyncLibrary`, `CheckForUpdates`; ports `FavoritesRepository`, `PlaylistRepository`, `LyricsCache`, `LocalLibrary`, `SyncGateway`, `SyncStateStore`, `UpdateChecker` |
| `core/data` | `space.wavedot.core.data` | `DefaultMediaSourceRegistry`, `PluginManager`, the plugin context factory |
| `core/testing` | `space.wavedot.core.testing` | Fakes (`FakeAudioEngine`, `FakeMediaSource`, `InMemoryFavoritesRepository`, `TestRegistry`) and `MediaSourceContractTest` |

### The single data contract

Every source (local files, Yandex Music, future Spotify or YouTube) is a `MediaSource`. A source exposes optional capabilities, and all of them return the same model types:

```kotlin
interface MediaSource {
    val descriptor: SourceDescriptor
    val catalog: Catalog?        // EntityProvider<Track/Album/Artist/Playlist>
    val search: Search?          // PagedQuery<SearchQuery, T>
    val streaming: Streaming?    // resolve(MediaId, AudioQuality) -> StreamSource
    val library: RemoteLibrary?  // liked tracks, like/unlike
}
```

- Every entity is addressed by `MediaId(source, key)`. The source part routes a request to the right `MediaSource` through the `MediaSourceRegistry`.
- Lists are always `Page<T>` with an opaque `Cursor`, whatever paging scheme the backend uses (offsets, page numbers, tokens).
- `StreamSource` describes how to get audio without saying how to play it. It is one of three kinds:
  - `LocalFile`: a path or `content://` URI.
  - `Remote`: a URL plus headers.
  - `Custom`: a seekable `ByteStreamOpener`, used for decrypted and generated streams.

Every source implementation must pass `MediaSourceContractTest`.

### Playback

`AudioEngine` is the only thing the domain knows about audio. The adapters are:

- **Desktop** (`infra/playback-desktop`): `FfmpegAudioEngine`, built from two parts:
  - `FfmpegDecoder` demuxes, decodes and resamples to S16 PCM through the FFmpeg C API (JavaCPP presets). Formats: FLAC, MP3, AAC, ALAC, Opus, WAV, plus FLAC/AAC in MP4.
  - An `AudioOutput` plays the PCM. It is currently `JavaSoundOutput`. Volume is applied in software, and position is derived from the frames actually played.
- **Android** (`infra/playback-android`): `Media3AudioEngine` on ExoPlayer, plus `WavedotPlaybackService` (a `MediaSessionService`) for the notification, lock screen and Bluetooth controls.

`StreamSource.Custom` streams reach either engine through `infra/stream-proxy`. `LoopbackStreamProxy` is a small Ktor server on `127.0.0.1`. It publishes each custom stream under a random URL and supports `Range`, so seeking works for every engine without engine-specific integration.

### Infrastructure and sources

| Module | Role |
|---|---|
| `infra/database` | SQLDelight schema: favorites, playlists, the lyrics cache, the pending change log for sync, and the sync revision. Implements `FavoritesRepository`, `PlaylistRepository`, `LyricsCache` and `SyncStateStore`. Schema changes ship as numbered `.sqm` migrations |
| `infra/storage` | `FileKeyValueStore`, used for settings and plugin secrets |
| `infra/network` | `createHttpClient` and `defaultHttpClient` (Ktor with OkHttp) |
| `infra/stream-proxy` | `LoopbackStreamProxy` |
| `infra/hub-client` | `HubClient`, which implements `SyncGateway` over `protocol-hub`, and `HubSettings` |
| `infra/updater` | `HttpUpdateChecker`, which implements `UpdateChecker`, and `UpdateDownloader` (SHA-256 verified) |
| `sources/local` | `LocalMediaSource`, with `FileSystemTrackScanner` (desktop, jaudiotagger) and `MediaStoreTrackScanner` (Android) |
| `plugins/yandex-music` | The built-in Yandex Music plugin. See [PLUGINS.md](PLUGINS.md) |
| `plugins/subsonic` | Subsonic and OpenSubsonic servers (Navidrome, Gonic, Airsonic, Ampache) |
| `plugins/audius` | The open Audius catalog, no account needed |
| `plugins/radio-browser` | Internet radio stations from the Radio Browser directory (`SourceKind.RADIO`) |
| `plugins/lrclib` | Lyrics from LRCLIB |

### Presentation and UI

The client's interface is split in two layers:

| Module | Package | Contents | Compose |
|---|---|---|---|
| `presentation/<feature>` (`library`, `player`, `search`, `settings`) | `space.wavedot.presentation.<feature>` | `ViewModel`, `UiState`, `Intent`, and the ports a feature needs (for example `SettingsBackend`) | No |
| `ui` | `space.wavedot.ui.*` | Every composable: the design system (`ui.designsystem`), the screens (`ui.library`, `ui.search`, `ui.player`, `ui.settings`), the root `WavedotApp` (`ui.app`) and the desktop window chrome (`ui.window`, `jvmMain`) | Yes |

- `presentation/*` depends on `core/domain` and `lifecycle-viewmodel` only, so view models are tested as plain Kotlin and cannot reach the UI toolkit. `ui` depends on `presentation/*` and never on `app/shell` or `infra/*`.
- `app/shell` wires `presentation/*` to the domain and infrastructure with Koin and has no Compose dependency. `app/desktop` and `app/android` are the only places that host the UI: they create the window or activity and call `WavedotApp(httpClient, imageCacheDirectory, …)`.
- The design system (`ui.designsystem`) holds the Material 3 Expressive theme, icons and shared components. The accent color is user-selectable: presets generate a full light and dark scheme from a seed color (`color/SeedScheme.kt`, CIE LCh tonal palettes with Material 3 role tones), `System` uses Android 12+ dynamic color or the Windows accent color, and the chosen id is stored by `AppearanceSettings` in `app/shell` and exposed to the UI by `AppearanceViewModel`. Artwork is loaded with Coil 3: remote URLs go through the app's Ktor client, local covers are plain file paths, and `WavedotApp` installs the image loader with a disk cache in the platform cache directory.
- Each `presentation/*` module has a `ViewModel` that exposes a `StateFlow<UiState>` and accepts `Intent`s (MVI); the matching composables in `ui` are stateless.
- Presentation modules depend only on `core/domain` and on ports they declare themselves. For example, `SettingsBackend` lets the settings view model stay free of plugin and hub internals.
- Settings is split into sections (Appearance, Plugins, Library, Sync, About). Wide windows show the section list and the open section side by side; narrow screens show the list first and open a section on its own page.
- The playlist picker is `PlaylistPickerViewModel` in `presentation/library` and `PlaylistPickerSheet` in `ui.library`. `WavedotApp` hosts a single picker above all screens and passes an `onAddToPlaylist` callback to the library, search and player screens, so every track list and the player open the same "Save to" sheet.
- `app/shell` is the composition root shared by both apps. It contains the Koin modules (`wavedotModules(platformModule)`), `bootstrap()` and the adapters that implement presentation ports.
- `app/android` and `app/desktop` provide the platform module (the engine, database driver, storage locations and scanner) and host `WavedotApp`.

### Data flow: playing a Yandex Music track

1. `SearchViewModel` calls `SearchCatalog`, which fans out to every `Search` capability (tracks, albums and artists, depending on the selected scope), or only to the sources picked in the source filter. Each source's results are reported separately, so a failing source does not hide the others.
2. A tap calls `PlayerController.play(tracks, index)`. `PlaybackQueue` is pure and immutable.
3. `ResolveStream` picks the best supported quality and calls `Streaming.resolve` on the track's source.
4. The Yandex plugin signs `get-file-info` and returns `StreamSource.Custom` with an `EncryptedStreamOpener`.
5. The engine turns it into a loopback URL. Each request runs the following chain:

   ```
   Range request → aligned HTTP range to the CDN → AES-CTR decryption from that block → PCM
   ```
6. When the engine reports `ENDED`, `PlayerController` advances the queue.

## Server

Each service is split into the same four layers:

| Layer | Depends on | Contents |
|---|---|---|
| `domain` | `common/kernel` only | Entities, use cases, repository ports. No Ktor, no Exposed |
| `data` | `domain`, `common/persistence` | Exposed tables and repositories, Flyway migrations (`db/migration/<service>`), storage adapters |
| `api` | `domain`, `common/ktor`, `shared/protocol-*` | Ktor routes, authentication, DTO ↔ domain mapping |
| `app` | everything above | `main()`, configuration (Hoplite YAML + environment), Koin wiring |

The shared server modules are:

- `common/kernel`: `AppError`, `TransactionRunner`, `IdGenerator`.
- `common/config`: typed configuration loading.
- `common/ktor`: JSON, `ProblemDetails` error mapping, call IDs, health endpoints, bearer authentication.
- `common/persistence`: Hikari, Flyway, `ExposedTransactionRunner`.
- `common/testing`: H2 test database and a mutable clock.

See [SERVER.md](SERVER.md) for each service's behavior.

## Testing strategy

- **Domain logic** (queue, player controller, search aggregation, sync, release resolution) is tested in `commonTest` or plain JVM tests against fakes. No IO.
- **Contract tests**: every `MediaSource` extends `MediaSourceContractTest`.
- **Adapters** are tested against their real dependency:
  - SQLDelight runs on in-memory SQLite.
  - Exposed runs on H2 in PostgreSQL mode.
  - FFmpeg decodes generated WAV files.
  - The stream proxy serves real HTTP on loopback.
  - Ktor clients use `MockEngine`.
  - Ktor servers use `testApplication`.
- **The Yandex Music plugin** is tested in two ways: the request signature is checked against the reference Python implementation, and decryption is checked at arbitrary byte offsets.
- **Koin wiring** is checked by `ModuleGraphTest` in `app/shell`.
