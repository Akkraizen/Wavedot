# Plugins

A plugin contributes one or more `MediaSource`s to the player. Plugins compile against `core/plugin-api` only, which re-exports `core/contract`, `core/model` and `core/common`. They never see the domain, the UI or other plugins.

## Lifecycle

```kotlin
class MyPlugin : WavedotPlugin {
    override val manifest = PluginManifest(
        id = "my-service",
        name = "My Service",
        version = "1.0.0",
        settings = listOf(PluginSettingSpec("token", "API token", secret = true)),
    )

    override suspend fun onLoad(context: PluginContext): PluginContribution {
        val token = context.secrets.get("token") ?: return PluginContribution()
        return PluginContribution(listOf(MySource(context.httpClient, token)))
    }
}
```

The lifecycle works like this:

1. `PluginManager.loadAll()` runs at startup.
2. For each plugin, the manager checks `apiVersion`, builds a `PluginContext` and calls `onLoad`. The returned sources are registered in the `MediaSourceRegistry`.
3. If loading fails, the plugin is marked `Failed` with a reason, and the rest of the app keeps working.
4. Settings lists plugins under Settings → Plugins, grouped by `PluginManifest.features`: a plugin with `SOURCES` appears under Sources, a lyrics-only plugin (`LYRICS`) under Lyrics. The default is `SOURCES`.
5. When the user saves a setting declared in the manifest, the host stores it (secrets separately) and reloads the plugin. Settings show their current value in Settings; secrets only show that a value is saved. `PluginSettingSpec.hint` is shown as a placeholder.

`PluginContext` provides:

- `httpClient`: the host's configured Ktor client.
- `settings` and `secrets`: key-value stores namespaced to the plugin.
- `logger`.

## Writing a source

A source implements whichever capabilities its service supports. Its `SourceDescriptor.kind` is `LOCAL`, `STREAMING` or `RADIO`. Radio sources emit tracks without a duration; the player shows them as live and disables seeking, and lyrics are not requested for them. It must:

- use its own `SourceId` in every `MediaId` it emits;
- turn backend paging into `Page`/`Cursor`;
- throw `WavedotException(WavedotError.Unauthorized(...))` when credentials are missing or rejected;
- return the simplest `StreamSource` that works: `Remote` for plain URLs, `Custom` when bytes must be transformed (decryption, chunk assembly).

`openByteStream(url, offset, headers)` in `space.wavedot.plugin.api.http` returns a `ByteStream` that issues an HTTP `Range` request and reports the total length. It is the building block for `Custom` streams.

Add a test that extends `MediaSourceContractTest`, using `ktor-client-mock` in place of the real service.

## Lyrics providers

Lyrics come from `LyricsProvider`s. A source can expose its own provider through `MediaSource.lyrics`, and a plugin can contribute standalone providers through `PluginContribution.lyricsProviders`; these search by track metadata and work for any source. `GetLyrics` only serves tracks from streaming sources (`SourceKind.STREAMING`). It asks the track's own source first, then the standalone providers in registration order, and returns the first synced result. If no synced result exists, it returns the first plain one. Results and misses are cached in the local database (misses for three days); failures are not cached. Synced lyrics use LRC; `LrcParser` in `core/model` handles timestamps, multiple stamps per line and the `[offset:]` tag.

Built-in providers:

- **Yandex Music.** `GET /tracks/{id}/lyrics` with `format` set to `LRC` or `TEXT`, `timeStamp`, and `sign = Base64(HMAC-SHA256(key, trackId + timeStamp))`. The response contains a `downloadUrl` with the text, plus writers and the licensing partner, which are shown as credits.
- **LRCLIB** (`plugins/lrclib`). This is the free lrclib.net API and needs no key. The plugin first tries an exact match with `GET /api/get`, using title, artist, album and duration. If that fails, it retries with a cleaned title, without suffixes such as "(Remastered)". As a last resort it calls `GET /api/search` and accepts a candidate within 3 seconds of the track's duration, preferring synced lyrics.

## UI extensions

Plugins change the interface declaratively. A plugin returns a `PluginUi` in `PluginContribution.ui`; the app draws it with its own components, so plugins never depend on Compose and every extension looks like the rest of the app. The types live in `space.wavedot.core.contract.ui` and are re-exported by the plugin API.

| Extension point | Type | Where it appears |
|---|---|---|
| Screens | `PluginScreen(id, title, icon, content)` | A destination in the navigation rail or bar, between Search and Settings |
| Library sections | `PluginSection(id, title, content)` | Below the playlist grid in the library |
| Track actions | `PluginTrackAction(id, label, icon, action, appliesTo)` | Every track menu (library, search, queue, plugin lists) and the player |
| Settings page | `PluginUi.settings` | Replaces the generated fields of the plugin in Settings → Plugins |
| Accent colors | `PluginAccent(id, name, argb)` | Added to Settings → Appearance; the scheme is generated from the seed |

`content` is a `Flow<List<UiNode>>`: emit new lists to update the screen (for example after a setting changes). The host shows a loading indicator until the first list arrives and turns a failed flow into an error banner. Nodes:

| Node | Rendering |
|---|---|
| `Heading`, `Text` (`TITLE`, `BODY`, `CAPTION`), `Status` (`INFO`, `SUCCESS`, `WARNING`, `ERROR`) | Text and banners |
| `Tracks` | A track list; tapping a track plays the list from it |
| `Cards` | A horizontal row of artwork cards with an action |
| `Button` (`PRIMARY`, `TONAL`, `TEXT`), `Toggle`, `TextField`, `Choice` | Controls; each calls the plugin's `UiAction` or `UiValueAction` |
| `Loading` | A loading indicator |

Actions are suspend functions that return a `UiEffect`: `None`, `Message(text)` (shown as a snackbar) or `Play(tracks, startIndex)`. Exceptions thrown by actions become messages. Icons come from the fixed `UiIcon` set. Keys are namespaced by plugin id (`radio-browser/stations`), so plugins cannot collide, and everything a plugin contributed disappears when it is unloaded or reloaded.

The built-in plugins use this API: Internet radio adds a **Radio** screen with the most popular stations by country and a settings page with the same choice; Audius adds a **Trending on Audius** library section, a **More from this artist** track action and an **Audius** accent color.

## Registering a built-in plugin

Add the module to `settings.gradle.kts`, depend on it from `client/app/shell`, and list it in `builtInPluginsModule` (`WavedotModules.kt`). Loading external plugins from JARs at runtime is planned (see TODO.md).

## Built-in: Audius (`plugins/audius`)

Audius is an open music catalog with a public API, so the plugin needs no account and no key. Every request carries `app_name=Wavedot`.

| Capability | Endpoint |
|---|---|
| Catalog | `GET /v1/tracks/{id}` |
| Search | `GET /v1/tracks/search`, `/v1/playlists/search`, `/v1/users/search` with `offset` and `limit` |
| Album and artist tracks | `GET /v1/playlists/{id}/tracks`, `GET /v1/users/{id}/tracks` |
| Streaming | `GET /v1/tracks/{id}/stream`, which redirects to an MP3 on a content node |

Audius playlists and albums both appear as albums, with keys prefixed `playlist/`; users appear as artists with keys prefixed `user/`. Search hides tracks that are not streamable without signing in (`is_streamable = false`), but paging still advances by the raw page size.

## Built-in: Internet radio (`plugins/radio-browser`)

Stations come from the community [Radio Browser](https://www.radio-browser.info) directory through `all.api.radio-browser.info`. The source is `SourceKind.RADIO` with the id `radio`; a station is a `Track` keyed by its `stationuuid`, with the country, two tags and the codec as the artist line and no duration.

| Capability | Endpoint |
|---|---|
| Catalog | `GET /json/stations/byuuid?uuids=…` |
| Search | `GET /json/stations/search?name=…&hidebroken=true&order=clickcount&reverse=true`, paged by `offset` |
| Streaming | `GET /json/url/{uuid}`, which registers a click and returns the stream URL; `url_resolved` is the fallback |

Requests send a `User-Agent` of `Wavedot/0.1`, as the directory asks. Streams are played as plain remote URLs.

## Built-in: Subsonic (`plugins/subsonic`)

Connects to one server that speaks the Subsonic API 1.16.1, including OpenSubsonic servers such as Navidrome. Settings: server URL, username and password (a secret). The plugin stays inactive until all three are set; on load it calls `ping` and fails with `Unauthorized` if the credentials are wrong, while an unreachable server only logs a warning.

Every request uses token authentication: `u`, `t = md5(password + salt)`, `s` (a random salt per session), `v = 1.16.1`, `c = Wavedot` and `f = json`. The password itself is never sent.

| Capability | Endpoint |
|---|---|
| Catalog | `getSong`, `getAlbum`, `getArtist` |
| Search | `search3`, one call per kind with `songCount`/`songOffset` and the matching album and artist parameters |
| Streaming | `stream` with `maxBitRate` 128, 192 or 320 kbps for low, normal and high quality, and the original file for lossless |
| Likes | `getStarred2`, `star`, `unstar` |
| Artwork | `getCoverArt` at 480 px |

Album keys are prefixed `album/` and artist keys `artist/`. Artist tracks are loaded ten albums per page. Stream and artwork URLs contain the salted token, so they stop working when the password changes.

## Built-in: Yandex Music (`plugins/yandex-music`)

This plugin needs the user's own OAuth token, entered in Settings, and uses the API the official apps use.

| Capability | Endpoint |
|---|---|
| Catalog | `POST /tracks` (`track-ids`) |
| Search | `GET /search?type=track&page=N` (the cursor is the page number) |
| Likes | `GET /users/{uid}/likes/tracks`, `POST /users/{uid}/likes/tracks/add-multiple` and `…/remove` (the uid comes from `GET /account/status`) |
| Streaming | `GET /get-file-info` |

Every request carries the headers `Authorization: OAuth <token>` and `X-Yandex-Music-Client: <client>`.

### How streaming works

The approach is based on [llistochek/yandex-music-downloader](https://github.com/llistochek/yandex-music-downloader), changed so that tracks are streamed instead of downloaded.

1. **Request.** `get-file-info` is called with the following parameters:
   - `ts`
   - `trackId`
   - `quality`: `lq`, `nq` or `lossless`
   - `codecs`: `flac,flac-mp4,mp3,aac,he-aac,aac-mp4,he-aac-mp4`
   - `transports=encraw`
   - `sign`
2. **Signature.** `sign` is computed as `Base64(HMAC-SHA256(key, ts + trackId + quality + codecsWithoutCommas + transports))` with the last character removed. The key and the matching client header form a `ClientProfile`. `ANDROID` is the default and matches the reference implementation; `DESKTOP` is the alternative.
3. **Response.** `downloadInfo` contains `urls`, `codec` and, for encrypted transports, a hex `key`.
4. **Decryption.** The content is AES-128-CTR encrypted with a 12-byte zero nonce and a 4-byte big-endian block counter. CTR allows random access: to start at byte `n`, the plugin does the following:
   1. Requests the range from `n - n % 16`.
   2. Initializes the counter to `n / 16`.
   3. Drops the first `n % 16` decrypted bytes.

   This is what makes seeking possible without downloading the whole file.
5. **Delivery.** The decrypted bytes go to the audio engine through the loopback stream proxy. FLAC-in-MP4 and AAC-in-MP4 are played directly by FFmpeg (desktop) and ExoPlayer (Android).
