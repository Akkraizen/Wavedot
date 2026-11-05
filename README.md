<p align="center"><img src="docs/assets/wavedot-icon.svg" width="128" alt="Wavedot logo"></p>

# Wavedot

Wavedot is a modular music player built with Kotlin Multiplatform. It plays local files, streams from online services through plugins, and keeps a single library of favorites across sources and devices.

| Part | What it is |
|---|---|
| `client/` | The player for Android and desktop (JVM: Windows, Linux, macOS), with a Compose Multiplatform UI |
| `server/` | Two independent backend services: **hub** (sync and future account features) and **updates** (app releases) |
| `shared/` | Wire protocol shared by client and server (DTOs, routes, error format) |

## Status

This is an early scaffold. The following already works and is covered by tests:

- local playback on desktop: an FFmpeg decoder with a JavaSound output
- local playback on Android: Media3/ExoPlayer
- the play queue, with shuffle and repeat
- aggregated search across all sources
- favorites, stored in SQLite and mirrored to sources that support likes
- the plugin system, with the built-in **Yandex Music** plugin (signed `get-file-info`, AES-CTR decryption on the fly, seekable streams)
- sync of favorites through the hub
- update checks against the updates service

See [TODO.md](TODO.md) for what is planned next.

## Requirements

- JDK 25. The Gradle toolchain resolver can provision it.
- Android SDK with platform 37, for the Android app only.
- Docker, for running the services locally.

## Common commands

```bash
./gradlew :client:app:desktop:run
./gradlew :client:app:android:assembleDebug
./gradlew jvmTest test
./gradlew -Pwavedot.serverOnly=true :server:hub:app:installDist :server:updates:app:installDist
docker compose -f deploy/docker-compose.yml up --build
```

`-Pwavedot.serverOnly=true` drops the client modules and the Android targets. Use it for server builds on machines without an Android SDK; the Docker images are built this way.

## Documentation

- [Architecture](docs/ARCHITECTURE.md): modules, layers and data flow
- [Plugins](docs/PLUGINS.md): how sources are added
- [Server](docs/SERVER.md): the hub and updates services, configuration and deployment
- [Protocol](docs/PROTOCOL.md): the client–server wire contract
- [AGENTS.md](AGENTS.md): rules for contributors and coding agents
