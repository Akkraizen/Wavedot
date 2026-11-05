# Protocol

The client and the services exchange JSON defined in `shared/protocol-*` (Kotlin Multiplatform, kotlinx.serialization). Both sides compile against the same classes, so an incompatible change breaks the build instead of production.

| Module | Contents |
|---|---|
| `protocol-core` | `ProtocolVersion` (header `X-Wavedot-Protocol`), `ProblemDetails`, `ProblemTypes`, `PageDto`, `PlatformDto`, `ArchDto`, `ProtocolJson` |
| `protocol-hub` | `HubRoutes`, account DTOs, `ChangeDto`, push/pull DTOs, `RevisionEvent`, `SyncCollections`, `FavoritePayloadDto` |
| `protocol-updates` | `UpdatesRoutes`, `ReleaseDto`, `ArtifactDto`, `ChannelDto`, `PublishReleaseRequest` |

## Rules

- DTOs are transport types only. Client and server map them to their own domain models and never share domain classes.
- Every change must be additive: add only optional fields with defaults, and never rename or remove anything. `ProtocolJson` ignores unknown keys, so older clients keep working.
- A breaking change requires a new route version (`/v2/...`) and a bump of `ProtocolVersion.CURRENT`.
- Enum wire names are lowercase (`@SerialName`).
- Timestamps are Unix epoch milliseconds (`Long`).

## Sync payloads

`ChangeDto.payload` is an arbitrary JSON object owned by the collection. The hub stores it opaquely. Clients define the payload schema per collection in `protocol-hub`; `FavoritePayloadDto` is the schema for `favorites`. A client ignores changes for collections it does not know about.
