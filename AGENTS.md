# AGENTS.md

These rules apply to anyone changing this repository, people and coding agents alike. Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) before making structural changes.

## Project at a glance

- Kotlin Multiplatform music player (`client/`). Targets: Android and desktop JVM. iOS is planned.
- Two Ktor services (`server/hub`, `server/updates`).
- A shared wire protocol (`shared/`).
- Root package `space.wavedot`.
- Toolchain: JDK 25. Gradle 9 with the Kotlin DSL, a version catalog (`gradle/libs.versions.toml`) and convention plugins (`build-logic/`).

## Hard rules

1. **No comments in code or configuration.** Do not add comments to source files, build scripts, SQL, YAML or XML. Make code self-explanatory through naming and small functions. Documentation belongs in `docs/` and the Markdown files at the root.
2. **All documentation is written in English.**
3. **Respect layer boundaries.**
   - `core/model`, `core/contract`, `core/domain` and every server `*/domain` module must stay free of UI, storage, networking and platform APIs.
   - Features depend on `core/domain`, not on infrastructure. When a feature needs something new, declare a port in the feature or the domain and implement it in `app/shell` or `infra/*`.
   - Plugins depend only on `core/plugin-api`.
   - Server `api` modules map DTOs and must not contain business rules. Server `data` modules implement ports.
4. **One data contract.** New sources implement `MediaSource` capabilities and pass `MediaSourceContractTest`. Do not add source-specific branches to the domain or the UI.
5. **Protocol changes must be additive.** Follow [docs/PROTOCOL.md](docs/PROTOCOL.md).
6. **Every behavior change comes with tests.** Domain logic is tested with fakes from `core/testing`. Adapters are tested against a real or embedded dependency: in-memory SQLite, H2, MockEngine, testApplication, or generated audio.
7. **Commits and pull requests carry no AI attribution:**
   - no `Co-Authored-By` lines for assistants;
   - no session links;
   - no "generated with" footers.
8. Add new dependency versions through the version catalog only.
9. The project is licensed under GPLv3. New dependencies must be GPLv3-compatible (for example Apache-2.0, MIT, BSD, LGPL, GPLv3). Do not add GPLv2-only, AGPL or proprietary code.

## Commands

```bash
./gradlew jvmTest test
./gradlew :client:app:desktop:run
./gradlew :client:app:android:assembleDebug
./gradlew -Pwavedot.serverOnly=true :server:hub:app:installDist :server:updates:app:installDist
```

Use `-Pwavedot.serverOnly=true` when the Android SDK is not available. It removes the client modules and the Android targets of the shared modules.

## Conventions

- One public top-level concept per file; the file is named after it.
- Prefer `suspend` functions and `Flow`/`StateFlow`; do not block on the main thread.
- UI state lives in a `ViewModel` as `StateFlow<UiState>`. User actions arrive as a sealed `Intent`. Composables are stateless and receive `state` and `onIntent`.
- Errors that cross module boundaries are `WavedotException` (client) or `AppError` (server).
- Code that both JVM-based client targets need goes into `jvmSharedMain`, never `commonMain`.
- Keep `TODO.md` current when you add, finish or drop planned work.

## Where things go

| You are adding… | Put it in |
|---|---|
| A new streaming service | `client/plugins/<name>` + `builtInPluginsModule` |
| A new screen | `client/feature/<name>` + a destination in `app/shell` |
| A platform capability (audio, storage, notifications) | a port in `core/*`, an implementation in `client/infra/<name>` |
| A new synced data type | a collection policy in `server/hub/domain`, a payload DTO in `protocol-hub`, a `SyncRecord` variant in `core/domain` |
| A new hub feature | a new package under `server/hub/{domain,data,api}` |
