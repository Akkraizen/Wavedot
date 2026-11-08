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
4. When the user saves a setting declared in the manifest, the host stores it (secrets separately) and reloads the plugin.

`PluginContext` provides:

- `httpClient`: the host's configured Ktor client.
- `settings` and `secrets`: key-value stores namespaced to the plugin.
- `logger`.

## Writing a source

A source implements whichever capabilities its service supports. It must:

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

## Registering a built-in plugin

Add the module to `settings.gradle.kts`, depend on it from `client/app/shell`, and list it in `builtInPluginsModule` (`WavedotModules.kt`). Loading external plugins from JARs at runtime is planned (see TODO.md).

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
