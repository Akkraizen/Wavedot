# TODO

## Sources (plugins)

- [ ] **Spotify**: catalog, search, liked songs and playlists through the Web API. Playback options need research (Spotify Connect or a licensed SDK), because the Web API does not expose audio streams.
- [ ] **YouTube / YouTube Music**: search, library and playlists; audio stream resolution.
- [ ] **SoundCloud**: search, likes and stream resolution.
- [x] **Subsonic / OpenSubsonic** (Navidrome, Gonic, Airsonic, Ampache): search, albums, artists, streaming with bitrate caps, stars as likes.
- [ ] Subsonic: several servers at once (plugin instances with their own `SourceId`), OpenSubsonic lyrics (`getLyricsBySongId`), server playlists.
- [ ] **Jellyfin / Emby**: self-hosted libraries.
- [x] **Internet radio** through Radio Browser: station search and live playback.
- [ ] Internet radio: "now playing" from ICY metadata, station browsing by country and tag, HLS stations on Android.
- [x] **Audius**: search, playlists, artists and streaming without an account.
- [ ] **Internet Archive** and **Jamendo**: free catalogs.
- [ ] Yandex Music: albums, artists, playlists, "My Wave" radio, lyrics.
- [ ] Yandex Music: OAuth device flow in Settings instead of pasting a token.
- [ ] Load plugins at runtime from JARs (desktop: `ServiceLoader` and an isolated class loader), with signature checks. Needs process-level isolation or a restricted API and a per-plugin network policy first (see the trust model in docs/PLUGINS.md).
- [ ] Subsonic: OpenSubsonic API key authentication instead of the salted password token.

## Library and core features

- [ ] Favorites: two-way sync with each source's likes. Today, likes are imported and local toggles are mirrored, but removals made on the remote side are not detected.
- [ ] Cross-source matching: resolve the same track across sources (ISRC, title/artist/duration).
- [x] Local playlists: the library is a list of playlists (Favorites, On this device, user playlists); add tracks, albums or the queue from any track menu or the player.
- [ ] Playlists: drag-and-drop reordering.
- [ ] Playlists: import playlists from sources (a `Playlists` capability on `MediaSource`).
- [ ] Playlists: sync through the hub (`playlists` collection).
- [ ] Listening history and statistics, synced through the hub (`history` collection).
- [ ] Persist the local file index in the database instead of rescanning on every start; watch folders for changes.
- [x] Artwork loading and caching (Coil 3), embedded and folder covers for local files, MediaStore album art on Android.
- [ ] Extract embedded covers on Android when MediaStore has no album art.
- [ ] Offline cache for streamed tracks.
- [ ] Persist and restore the queue and playback position between launches.
- [x] Search: tracks, albums and artists with scope filters; album and artist pages.
- [x] Search: filter results by source.
- [ ] Search: playlists, search history, infinite scrolling of results, remembered source filter.

## Appearance

- [x] Accent color presets, Android dynamic color and the Windows accent color.
- [ ] Light, dark and system theme mode switch.
- [ ] Accent color extracted from the current track's artwork.
- [ ] Custom accent color picker.

## Playback

- [ ] Desktop: replace the JavaSound output with a WASAPI or miniaudio backend called through the FFM API (lower latency, exclusive mode, device selection).
- [ ] Desktop: generate FFmpeg bindings with jextract (FFM) instead of the JNI presets, and ship only the native libraries for the target platform.
- [ ] Gapless playback and crossfade (pre-open the next decoder).
- [ ] Equalizer and ReplayGain in the desktop DSP chain.
- [ ] Android: wrap the session player in a `ForwardingPlayer` so notification and Bluetooth controls drive `PlayerController` (next, previous, queue).
- [ ] Desktop media keys and OS integration: Windows SMTC, MPRIS on Linux, macOS Now Playing.
- [x] Lyrics for streaming tracks (Yandex Music, LRCLIB) with synced highlighting and tap-to-seek.
- [ ] Lyrics for local files (embedded tags and sidecar `.lrc` files).
- [ ] Per-track lyrics offset adjustment and provider order in Settings.
- [ ] Word-level (karaoke) sync and translations.
- [ ] Scrobbling (Last.fm, ListenBrainz).

## Sync and accounts (hub)

- [ ] Pair additional devices to an existing account (pairing code or QR). Until then an account has exactly one device.
- [x] List and revoke devices, delete the account.
- [ ] Use the WebSocket revision events on the client to trigger sync automatically.
- [ ] Settings sync (`settings` collection) and playlist sync (`playlists` collection).
- [ ] Optional end-to-end encryption of payloads.
- [x] Rate limiting, request size limits and per-account quotas.
- [ ] An audit log.
- [ ] Purge tombstones that every device has already pulled.
- [ ] Device token rotation and expiry of devices that have not been seen for a long time.
- [ ] Testcontainers-based PostgreSQL tests next to the H2 ones.

## Updates

- [x] Sign artifacts (ECDSA P-256, available on every supported Android version without extra dependencies) and verify signatures in the client in addition to SHA-256.
- [ ] Signing key rotation policy and a second trusted key in release builds.
- [ ] Install flow: Android package installer, Windows MSI/EXE handoff, Linux packages.
- [ ] S3-compatible `ArtifactStorage` implementation.
- [ ] Release notes rendering and a "what's new" screen.
- [ ] CI pipeline that publishes builds to the updates service.

## Platforms and security

- [ ] iOS target (Compose Multiplatform, AVAudioEngine-based `AudioEngine`, MediaPlayer integration).
- [x] Secure secret storage on Android (Keystore) and Windows (DPAPI).
- [ ] Secure secret storage on Linux (libsecret) and macOS (Keychain); today the file is only restricted to its owner.
- [ ] Desktop installer polish: icons, file associations, auto-start option.

## Engineering

- [x] CI: layer check, server and client tests, Android debug build, Windows secret storage tests.
- [ ] CI: Android lint, desktop packaging.
- [x] Dependency update checks (Dependabot) and a pinned Gradle distribution checksum.
- [ ] Static analysis (detekt, ktlint).
- [ ] Gradle dependency verification metadata, and container images and GitHub Actions pinned by digest.
- [ ] UI tests for the Compose screens.
