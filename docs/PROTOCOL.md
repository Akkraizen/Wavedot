# Protocol

The client and the services exchange JSON defined in `shared/protocol-*` (Kotlin Multiplatform, kotlinx.serialization). Both sides compile against the same classes, so an incompatible change breaks the build instead of production.

| Module | Contents |
|---|---|
| `protocol-core` | `ProtocolVersion` (header `X-Wavedot-Protocol`), `ProblemDetails`, `ProblemTypes`, `PageDto`, `PlatformDto`, `ArchDto`, `ProtocolJson`, `wireName()` / `fromWireName()` for enum wire names |
| `protocol-hub` | `HubRoutes`, account and device DTOs, `ChangeDto`, push/pull DTOs, `RevisionEvent`, `SyncCollections`, `FavoritePayloadDto` |
| `protocol-updates` | `UpdatesRoutes`, `ReleaseDto`, `ArtifactDto`, `ChannelDto`, `PublishReleaseRequest`, `UpdateManifest` (the signed artifact manifest) |

## Rules

- DTOs are transport types only. Client and server map them to their own domain models and never share domain classes.
- Every change must be additive: add only optional fields with defaults, and never rename or remove anything. `ProtocolJson` ignores unknown keys, so older clients keep working.
- A breaking change requires a new route version (`/v2/...`) and a bump of `ProtocolVersion.CURRENT`. Clients send their version in `X-Wavedot-Protocol`; a server refuses versions newer than its own with `400` and the `unsupported-protocol` problem type.
- Enum wire names are lowercase (`@SerialName`).
- Timestamps are Unix epoch milliseconds (`Long`).

## Sync payloads

`ChangeDto.payload` is a JSON object owned by the collection. The hub checks its size and stores it opaquely. Clients define the payload schema per collection in `protocol-hub`; `FavoritePayloadDto` is the schema for `favorites`. A client ignores changes for collections it does not know about and records it cannot parse. A client pushes at most 500 changes per request.

Payloads never carry credentials: artwork that needs signing travels as a `wavedot-artwork:<source>:<key>` reference, and favorites from device-only sources (local files) are not synced.
