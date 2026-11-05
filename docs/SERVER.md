# Server

The backend consists of two independent services. They share only `server/common/*` and the wire protocol in `shared/`. Each service has its own database, migrations, configuration and container image, so they can be deployed and scaled separately.

## Hub (`server/hub`)

The hub is the account and synchronization service. It is deliberately not named after its first feature: sync is only its first area of responsibility. Future ones include device pairing, social features and recommendations, each added as a new package in `hub/domain` and `hub/api`.

### Model

- **Account and Device.** `POST /v1/accounts` creates an account and its first device, and returns a device token. Only the token's SHA-256 hash is stored.
- **Library items.** Each item is keyed by `(account, collection, key)` and stores the latest state: payload, deleted flag, the client `updatedAt`, and a per-account monotonically increasing `revision`.
- **Collections.** The known collections are `favorites`, `playlists`, `history` and `settings`. Each has a `CollectionPolicy` that validates changes. Adding a collection means registering a policy; the schema does not change.

### Sync algorithm

- **Push** (`POST /v1/sync/changes`): for each change, the hub validates it against its collection policy and then applies last-writer-wins. A change older than the stored one is rejected and reported back to the client. Every accepted change gets the next account revision. Subscribers are notified when anything was applied.
- **Pull** (`GET /v1/sync/changes?since=R&limit=N`): returns the latest state of every item whose revision is greater than `R`, ordered by revision, and `hasMore` for paging. The client stores the last revision it received.
- **Events** (`WS /v1/sync/events`): pushes `{ "revision": N }` when the account changes, so clients can pull right away.

Third-party credentials (Yandex, Spotify and others) are never sent to the hub. Only `MediaId` references and display metadata are synced.

### Configuration

The settings live in `hub.yaml`. Each one can be overridden with an environment variable:

| Variable | Default |
|---|---|
| `HUB_HOST`, `HUB_PORT` | `0.0.0.0`, `8081` |
| `HUB_DATABASE_URL` | `jdbc:postgresql://localhost:5432/wavedot_hub` |
| `HUB_DATABASE_USER`, `HUB_DATABASE_PASSWORD` | `wavedot`, `wavedot` |
| `HUB_DATABASE_POOL` | `10` |

## Updates (`server/updates`)

The updates service publishes and serves application releases.

- **Releases.** A release has a semantic version, a channel (`stable` < `beta` < `nightly`), notes, a `mandatory` flag, a rollout percentage, and one artifact per platform/architecture (`universal` matches any architecture).
- **Resolution** (`GET /v1/releases/latest?platform&arch&channel&current&installId`): returns the highest release newer than `current` that matches the platform/architecture and the client's channel (a beta client also receives stable releases). Yanked releases are skipped. When the rollout is below 100%, a release is offered only to installs whose deterministic bucket, `SHA-256(releaseId:installId) mod 100`, falls under the percentage. The response is `204` when the client is up to date.
- **Downloads** (`GET /v1/artifacts/{releaseId}/{fileName}`): support `Range` requests, so downloads can be resumed.
- **Admin** (bearer `UPDATES_ADMIN_TOKEN`):
  - `POST /v1/admin/releases`: a multipart request with a `metadata` part (`PublishReleaseRequest` JSON) and one file part per artifact, named `artifact:<platform>:<arch>`. The service computes the SHA-256 and size of each artifact.
  - `POST /v1/admin/releases/{id}/yank`: withdraws a release.
- **Storage.** Artifacts are stored by the `ArtifactStorage` port. The current implementation uses the file system; an S3-compatible implementation is planned.

The settings live in `updates.yaml`:

| Variable | Default |
|---|---|
| `UPDATES_HOST`, `UPDATES_PORT` | `0.0.0.0`, `8082` |
| `UPDATES_DATABASE_URL` | `jdbc:postgresql://localhost:5432/wavedot_updates` |
| `UPDATES_DATABASE_USER`, `UPDATES_DATABASE_PASSWORD` | `wavedot`, `wavedot` |
| `UPDATES_STORAGE_ROOT` | `./data/artifacts` |
| `UPDATES_ADMIN_TOKEN` | empty (admin routes disabled) |

To publish a release:

```bash
curl -H "Authorization: Bearer $UPDATES_ADMIN_TOKEN" \
  -F 'metadata={"version":"0.2.0","channel":"stable","notes":"Fixes"}' \
  -F 'artifact:windows:x64=@Wavedot-0.2.0.msi' \
  http://localhost:8082/v1/admin/releases
```

## Shared behavior

- Errors are returned as `application/json` `ProblemDetails` objects (`type`, `title`, `status`, `detail`).
- `X-Request-Id` is accepted or generated, echoed back, and included in logs.
- `/health/live` and `/health/ready` are available; readiness checks the database.
- Flyway migrations run on startup.

## Running locally

```bash
cp deploy/.env.example deploy/.env
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up --build
```

The images are built with `-Pwavedot.serverOnly=true`, so the Android SDK is not needed.
