# TuneFlow — Music Streaming Platform

A production-shaped music streaming platform: **Java 21 / Spring Boot 3.5 modular monolith backend**
and a **Flutter + Riverpod client**. Built to the TuneFlow Technical Documentation (v1.0):
modular monolith with clean module boundaries, JWT auth with refresh rotation, Redis caching,
Kafka eventing, S3/MinIO media with short-lived signed URLs, FFmpeg processing, and a
PostgreSQL-backed catalog — packaged so it boots with nothing but a JVM in dev and scales
horizontally in production.

---

## Table of contents

- [What is in the box](#what-is-in-the-box)
- [Repository layout](#repository-layout)
- [Quick start](#quick-start)
- [Demo accounts](#demo-accounts)
- [Architecture](#architecture)
- [API surface](#api-surface)
- [Response envelope](#response-envelope)
- [Authentication](#authentication)
- [Streaming and media](#streaming-and-media)
- [Search](#search)
- [Caching strategy](#caching-strategy)
- [Asynchronous events](#asynchronous-events)
- [Rate limiting](#rate-limiting)
- [Storage and FFmpeg](#storage-and-ffmpeg)
- [Observability](#observability)
- [Testing](#testing)
- [Deployment](#deployment)
- [Scope decisions and roadmap](#scope-decisions-and-roadmap)

---

## What is in the box

| Capability | Status | Notes |
|---|---|---|
| Email/password auth, JWT access + refresh rotation | ✅ | Replay of a spent refresh token is detected and rejected |
| RBAC (`USER`, `ARTIST`, `ADMIN`) | ✅ | Enforced by role on routes and by ownership in services |
| Catalog: songs, albums, artists, genres | ✅ | Soft delete, UUID v4 PKs, DB-level constraints |
| Artist song publishing | ✅ | In-app upload: audio + cover to `/uploads/*`, **chunked with resume + per-part retry for anything ≥ 2 MB**, server-measured duration, metadata form, then `POST /songs`; artists may omit `artistId`, listeners see an explanation instead of a form that would 403 |
| Playlists (CRUD, add/remove/reorder songs) | ✅ | Add songs from the playlist screen: search/multi-select sheet over the catalog, already-added tracks disabled, duplicates tolerated per track; ownership checks return 404 for foreign resources, not 403 |
| Likes and liked-songs library | ✅ | Duplicate likes rejected with `ALREADY_LIKED` |
| Play history and listening stats | ✅ | Async final-update semantics from the player |
| Streaming with signed short-lived URLs | ✅ | Audio never proxied by application code in production |
| Downloads | ✅ | Size estimate from bitrate, `song.downloaded` event |
| Recommendations | ✅ | Rule-based strategy (genre affinity → trending), deterministic and testable |
| Search + typeahead suggestions | ✅ | PostgreSQL `pg_trgm` fuzzy matching (no Elasticsearch in the MVP) |
| Lyrics with LRC parsing | ✅ | Multi-timestamp hooks, precision variants, sync validation |
| Notifications and new-release fan-out | ✅ | Kafka-driven, in-process by default |
| Rate limiting | ✅ | Bucket per client + route class, `X-RateLimit-*` / `Retry-After` |
| Caching (Caffeine / Redis) | ✅ | Profile-selected, TTL per cache, cache-aside with eviction on writes |
| Kafka eventing | ✅ | `noop` (in-process) or `kafka` publisher behind one interface |
| S3/MinIO + local storage | ✅ | One `StorageService` abstraction, two implementations |
| FFmpeg/FFprobe media pipeline | ✅ | Duration probing + transcode/HLS behind a feature flag |
| Observability | ✅ | Actuator health, Micrometer, Prometheus, cache stats |
| Flutter client | ✅ | Feature-first clean architecture, Riverpod, Dio, persistent mini-player, chunked uploader; `flutter analyze` clean and 101/101 tests passing |
| Admin dashboard | ❌ | Out of scope by explicit decision (see [Scope](#scope-decisions-and-roadmap)) |

---

## Repository layout

```
tuneflow/
├── backend/                 Spring Boot modular monolith (Maven)
│   ├── src/main/java/com/tuneflow/
│   │   ├── auth/            JWT issuing/parsing, refresh tokens, password reset
│   │   ├── user/            Profile, password change, liked songs
│   │   ├── artist/          Artist profiles, follows
│   │   ├── album/           Albums and track listings
│   │   ├── music/           Songs, genres, likes
│   │   ├── playlist/        Playlists and ordering
│   │   ├── history/         Playback events, recently played, stats
│   │   ├── streaming/       Stream URLs, downloads
│   │   ├── storage/         StorageService (local + S3), MediaController, FFmpeg
│   │   ├── search/          SearchProvider (JPA implementation), suggestions
│   │   ├── recommendation/  Strategies and the home-feed builder
│   │   ├── lyrics/          LRC parser and lyrics persistence
│   │   ├── notification/    Notification store + Kafka consumers
│   │   └── common/          Envelope, exceptions, security, rate limiting, events
│   ├── src/main/resources/db/migration/{postgresql,h2}
│   ├── src/test/java/com/tuneflow/
│   ├── Dockerfile
│   └── .env.example
├── frontend/                Flutter client (feature-first clean architecture)
├── deploy/nginx/            API gateway config
└── docker-compose.yml       Postgres + Redis + Kafka + MinIO (+ optional Elasticsearch)
```

Each backend module owns its `dto/`, `entity/`, `repository/`, `service/` and `controller/`
packages. Cross-module calls go through the owning module's service interface, never through
another module's repository — the boundary that makes the later split into services mechanical.

---

## Quick start

### Option 1 — Zero dependencies (H2, fastest)

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Boots in ~10 s with an in-memory H2 database, Caffeine cache, local disk storage and in-process
event fan-out. Demo data (users, artists, albums, songs, likes, a playlist, history) is seeded.
API at `http://localhost:8080/api/v1`, Swagger UI at `http://localhost:8080/swagger-ui.html`.

> **On Windows without Maven installed?** The repository ships the Apache Maven Wrapper. A JDK is
> the only requirement — the one bundled with Android Studio is fine:
>
> ```bat
> cd backend
> set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
> mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
> ```
>
> `run-dev.cmd` in `backend\` does that for you (it builds the jar on first run, then starts the dev
> profile). The wrapper downloads Maven 3.9.9 itself the first time it runs, so that first start
> needs an internet connection.

### Option 2 — PostgreSQL, everything else in-process

```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run
```

### Option 3 — Full stack

```bash
docker compose up -d postgres redis kafka minio minio-init
cd backend && SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
# or build the API too:  docker compose --profile app up -d --build
```

| Service | URL | Credentials |
|---|---|---|
| API | http://localhost:8080/api/v1 | — |
| Swagger UI | http://localhost:8080/swagger-ui.html | dev profile only |
| Health | http://localhost:8080/actuator/health | — |
| Prometheus metrics | http://localhost:8080/actuator/prometheus | — |
| MinIO console | http://localhost:9001 | `minioadmin` / `minioadmin` |
| PostgreSQL | `localhost:5432/tuneflow` | `tuneflow` / `tuneflow` |
| Redis | `localhost:6379` | — |
| Kafka (external listener) | `localhost:29092` | — |

### Frontend

```bash
cd frontend
flutter pub get
flutter run --dart-define=API_BASE_URL=http://localhost:8080/api/v1
```

Android emulator: use `http://10.0.2.2:8080/api/v1`.

Physical Android phone over USB — `adb reverse` points the phone's own `localhost:8080` at this PC,
so no IP address or firewall rule is involved:

```bash
adb reverse tcp:8080 tcp:8080
flutter run --dart-define=API_BASE_URL=http://localhost:8080/api/v1
```

`run-android.cmd` in `frontend\` runs both steps on Windows. See
[`frontend/README.md`](frontend/README.md#troubleshooting-the-android-build-on-windows) for the
Android/Gradle problems this setup avoids and how to fix them if they appear.

---

## Demo accounts

Seeded by the `dev` profile (password for all three: `Password123`):

| Email | Role | Purpose |
|---|---|---|
| `listener@tuneflow.dev` | USER | Listener with likes, a playlist and play history |
| `artist@tuneflow.dev` | ARTIST | Artist profile "Aria Vance", owns albums and songs |
| `admin@tuneflow.dev` | ADMIN | Full catalog access (no admin UI in this scope) |

Seed data: 10 genres, 2 albums, 6 songs, likes, a follow, the playlist *Late Night Drive* and
play history, so every feed on the home screen is populated on first launch.

---

## Architecture

**Modular monolith** (docs §4–§6): a single deployable with strict internal module boundaries.

```
Controller  →  Service (interface)  →  Repository  →  PostgreSQL
     ↓              ↓
   DTOs         Domain rules, caching, events
```

Rules enforced across the codebase:

- Controllers never touch repositories; they translate HTTP ↔ DTOs and delegate.
- Entities never leave the service layer — responses are built by explicit mappers.
- Cross-module access goes through service interfaces (`ArtistService`, `SongService`, …).
- Flyway owns the schema; Hibernate runs with `ddl-auto: validate`, so a mapping drift fails the
  build at boot instead of silently mutating production tables.
- Errors are translated centrally by `GlobalExceptionHandler` into the error envelope; a module
  throws an `ApiException` with a typed `ErrorCode`.

### Profiles

| Profile | Database | Cache | Events | Storage | Use |
|---|---|---|---|---|---|
| default | PostgreSQL | Caffeine | in-process | local disk | Local development with Docker Postgres |
| `dev` | H2 (in-memory) | Caffeine | in-process | local disk | Zero-dependency demo, CI |
| `prod` | PostgreSQL | Redis | Kafka | S3/MinIO | Real deployments |
| `test` | H2 (in-memory) | Caffeine | in-process | local disk | Integration tests |

---

## API surface

Base path `/api/v1`; 60+ operations across 17 controllers (53 documented paths). Every list
endpoint is paginated with `page`/`size` (default size 20, max 100) and returns a consistent
paged payload.

| Group | Representative endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `POST /auth/logout-all`, `POST /auth/forgot-password`, `POST /auth/reset-password` |
| Users | `GET/PUT/DELETE /users/me`, `PUT /users/me/password`, `GET /users/me/liked-songs` |
| Artists | `GET /artists`, `GET /artists/popular`, `GET /artists/{id}`, `PUT /artists/{id}`, `POST/DELETE /artists/{id}/follow`, `GET /artists/following`, `GET/PUT /artists/me` |
| Albums | `GET /albums`, `GET /albums/new-releases`, `GET /albums/{id}`, `POST/PATCH/DELETE /albums/{id}` |
| Songs | `GET /songs` (filters: `artistId`, `albumId`, `genre`, `q`), `GET /songs/trending`, `GET /songs/new-releases`, `GET /songs/{id}`, `POST/PUT/DELETE /songs/{id}`, `POST/DELETE /songs/{id}/like` |
| Genres | `GET /genres`, `POST /genres`, `PUT/DELETE /genres/{id}` (genre track lists come from `GET /songs?genre=<slug>`) |
| Playlists | `GET /playlists`, `GET /playlists/public`, `POST /playlists`, `GET/PUT/DELETE /playlists/{id}`, `POST /playlists/{id}/songs` (add), `PUT /playlists/{id}/songs` (reorder), `DELETE /playlists/{id}/songs/{songId}`, `DELETE /playlists/{id}/songs` (clear) |
| Streaming | `GET /songs/{id}/stream`, `POST /downloads`, `GET /downloads`, `POST /downloads/{id}/complete`, `DELETE /downloads/{id}` |
| History | `POST /history`, `GET /history/recently-played`, `GET /history/stats`, `DELETE /history` |
| Search | `GET /search`, `GET /search/suggest` |
| Discovery | `GET /home`, `GET /recommendations`, `GET /recommendations/strategy` |
| Lyrics | `GET/PUT /songs/{songId}/lyrics`, `GET /songs/{songId}/lyrics/languages`, `DELETE /songs/{songId}/lyrics/{language}` |
| Notifications | `GET /notifications`, `GET /notifications/unread-count`, `POST /notifications/{id}/read`, `POST /notifications/read-all` |
| Uploads | `POST /uploads/audio`, `POST /uploads/image` (single-shot, ≤ 2 MB in the client) |
| Chunked uploads | `POST /uploads/audio/initiate` → `PUT /uploads/audio/{id}/parts/{n}` → `GET /uploads/audio/{id}` → `POST /uploads/audio/{id}/complete` → `DELETE /uploads/audio/{id}` |
| Media | `GET /media/{key}` — signed read endpoint |
| Ops | `GET /actuator/health`, `GET /actuator/prometheus` |

Public (no token): catalog reads, search, home, recommendations, genre lists, artist and album
listings, and signed media URLs (the signature *is* the authorisation).
Everything else returns `401 UNAUTHENTICATED` with the standard error envelope.

---

## Response envelope

Success:

```json
{
  "success": true,
  "message": "Login successful",
  "data": { "accessToken": "…", "refreshToken": "…", "tokenType": "Bearer", "expiresIn": 900 },
  "timestamp": "2026-10-06T09:15:04.221Z"
}
```

Error — HTTP status is meaningful (400/401/403/404/409/429/500), and `errorCode` is the stable
machine-readable key:

```json
{
  "success": false,
  "message": "Authentication required",
  "errorCode": "UNAUTHENTICATED",
  "path": "/api/v1/users/me",
  "timestamp": "2026-10-06T09:15:04.221Z"
}
```

Validation failures additionally carry `fieldErrors: [{ "field", "message", "rejectedValue" }]`.

Selected error codes: `VALIDATION_FAILED`, `BAD_CREDENTIALS`, `UNAUTHENTICATED`, `FORBIDDEN`,
`TOKEN_EXPIRED`, `INVALID_TOKEN`, `INVALID_REFRESH_TOKEN`, `EMAIL_ALREADY_EXISTS`,
`RESOURCE_NOT_FOUND`, `SONG_NOT_FOUND`, `PLAYLIST_NOT_FOUND`, `ALREADY_LIKED`, `NOT_LIKED`,
`ALREADY_FOLLOWING`, `RATE_LIMIT_EXCEEDED`, `UNSUPPORTED_MEDIA_TYPE`, `FILE_TOO_LARGE`,
`STORAGE_ERROR`, `INTERNAL_ERROR`.

---

## Authentication

- **Access token** — HS256 JWT, 15 minutes, claims `sub` (user id), `email`, `name`, `role`, `iat`, `exp`.
- **Refresh token** — opaque random value, SHA-256 hashed at rest, 30 days, **rotated on every use**.
  Presenting a token that was already rotated invalidates the family and returns
  `INVALID_REFRESH_TOKEN` — the standard defence against refresh-token theft.
- **Logout** revokes the supplied refresh token.
- **Password reset** — single-use token with a short TTL; the token store is Caffeine in development
  and Redis in production. With `tuneflow.security.expose-reset-token: true` (dev only) the token is
  returned in the response so the flow can be exercised without an SMTP server.
- **Clients must refresh on `401`**, which is why authentication failures return 401 rather than 403:
  the Flutter interceptor can distinguish "get a new token" from "you are not allowed".

---

## Streaming and media

The documentation is explicit: **audio must never be proxied through the application** (§17, §33).
TuneFlow follows that in production and stays testable locally:

1. `GET /songs/{id}/stream` (authenticated) → `{ url, expiresInSeconds, format, bitrateKbps }`.
2. The URL carries an HMAC-SHA256 signature over `key + expires + client`, so a URL cannot be
   shared, extended or edited.
3. In production that URL points at S3/MinIO or the CDN (swap in `TUNEFLOW_CDN_BASE_URL`).
   With the local storage provider it points at `GET /api/v1/media/{key}` — a purpose-built
   byte-range streamer: manual `RandomAccessFile` reads, 64 KB buffer, `Range` honoured with
   `206 Partial Content` + `Content-Range`, `Accept-Ranges: bytes`, 1 MB per-response cap,
   `Cache-Control: private, no-store`.
4. Tampered, foreign or expired signatures return `403`.

The media path is the one place where a hand-written controller beats a framework helper:
returning `ResourceRegion` from a controller with explicit `Content-Length`/`Content-Range`
headers fails at runtime (Spring writes those headers into an unmodifiable map, and a preset
`Content-Type` defeats content negotiation). The manual streamer is deliberate — do not refactor
it back.

---

## Search

MVP search runs entirely on PostgreSQL — no Elasticsearch (§24 allows either, and the MVP does not
need a second stateful service):

- `GET /search?q=&type=all|songs|artists|albums|playlists` with per-type caps and a combined
  `totalResults`.
- Two passes: an exact `ILIKE` match over title/name fields; if that returns nothing, a **tokenized
  fallback** that matches the leading token, so a typo in a trailing word still finds the track
  (`neon skylne` → *Neon Skyline*, reported as `fuzzyMatched: true`).
- `fuzzyMatched` is only true when the fallback actually returned rows — an empty result never
  advertises a fuzzy match.
- The `search` cache is short-lived (5 minutes) so newly uploaded tracks appear quickly.
- `GET /search/suggest?q=` returns lightweight typeahead entries (titles, artists, genres).
- The provider sits behind a `SearchProvider` interface with `tuneflow.search.provider=jpa|elasticsearch`,
  so an Elasticsearch implementation can be dropped in without touching a single controller.

---

## Caching strategy

Cache-aside with explicit invalidation on writes; provider chosen by profile
(`caffeine` or `redis`), and cache statistics are exported to Micrometer.

| Cache | TTL | Invalidated by |
|---|---|---|
| `songs` | 15m | song create/update/delete, like/unlike, play-count updates |
| `albums` | 15m | album create/update/delete |
| `artists` | 30m | artist update, follow/unfollow |
| `search` | 5m | TTL only (short by design) |
| `homeFeed` | 5m | TTL only |
| `recommendations` | 30m | TTL only |
| `genres` | long | genre create |

The subtle one: incrementing a play count must evict the song cache entry, otherwise
`GET /songs/{id}` keeps serving a stale counter. `HistoryServiceImpl.incrementPlayCount` carries
`@CacheEvict(cacheNames = "songs", key = "#songId")` for exactly that reason, verified end to end
(1240 → 1241 within ~2 s of an async history post).

Eviction uses `flushAutomatically` but **not** `clearAutomatically`: clearing the persistence
context detaches entities that are still being mapped into the response and produces
`LazyInitializationException`. This is a known trap in this codebase.

---

## Asynchronous events

One `EventPublisher` interface, two implementations (`noop` in-process, `kafka`), selected by
`tuneflow.events.publisher`. Business code never knows which one is active.

| Event | Emitted when | Consumers |
|---|---|---|
| `song.played` | a playback is recorded | history aggregations |
| `song.completed` | a track finishes | recommendation signals |
| `song.liked` / `song.unliked` | like toggled | taste profile |
| `song.created` | artist uploads a track | new-release notification fan-out |
| `song.downloaded` | a download completes | analytics |
| `playlist.created` / `playlist.updated` | playlist mutations | activity feeds |
| `user.followed` | a listener follows an artist | artist notifications |

Kafka publishing is best-effort and never blocks the HTTP response: the request path performs the
write, then hands the event to the publisher. This keeps the modular monolith honest — the same
events already carry the information a future extracted service would need.

---

## Rate limiting

`RateLimitFilter` (Redis-backed counters when Redis is enabled, in-memory otherwise) applies
per-client buckets by route class:

| Route class | Limit |
|---|---|
| Authentication (`/auth/**`) | 5 requests/minute per IP |
| Uploads (`/uploads/**`) | 10 requests/minute per user |
| Upload chunks (`PUT /uploads/audio/{id}/parts/{n}`) | 240/minute per user, so a long track is never throttled mid-transfer |
| Everything else | 100 requests/minute per user (or IP when anonymous) |

Exceeded requests get `429` plus `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`
and `X-RateLimit-Reset`. Development relaxes the auth bucket to 20/minute so manual testing is not
throttled.

---

## Storage and FFmpeg

`StorageService` is the single seam for object storage:

- **`local`** — filesystem under `TUNEFLOW_MEDIA_PATH`, keys are `images|audio/YYYY/MM/{uuid}.{ext}`,
  URLs signed by the application.
- **`s3`** — S3-compatible (AWS S3, MinIO), path-style access for MinIO, presigned URLs for reads.

Uploads are validated before they are stored: extension and content type whitelists, size limits
(100 MB audio, 10 MB images), and non-audio content is rejected outright.

**Large uploads are chunked and resumable.** `POST /uploads/audio/initiate` opens a session and the
*server* dictates the part size (`TUNEFLOW_UPLOAD_CHUNK_SIZE`, default 4 MB, never below 64 KB), so
one request never has to carry a whole album track:

```
POST /uploads/audio/initiate   { fileName, sizeBytes, contentType }
  -> { uploadId, chunkSizeBytes, partCount, sizeBytes, receivedParts, expiresAt }

PUT  /uploads/audio/{id}/parts/{n}     application/octet-stream, retryable, any order
GET  /uploads/audio/{id}               -> which parts already arrived (resume after a dropped link)
POST /uploads/audio/{id}/complete      -> assembles, verifies the byte count, hands off to the same
                                          validation + storage path as a single-shot upload
DELETE /uploads/audio/{id}             -> abandon
```

Sessions live under `TUNEFLOW_UPLOAD_TEMP_PATH`, are addressable only by their owner (someone else's
id answers `RESOURCE_NOT_FOUND`), expire after `TUNEFLOW_UPLOAD_SESSION_TTL` (24 h) and are swept by
a scheduled purge every 30 minutes. Because parts are independent, a flaky phone connection costs one
part, not the whole file.

**Stream descriptors report the real format.** `GET /songs/{id}/stream` derives `contentType` from the
stored object's extension, so a wav/flac/m4a upload is not advertised as `audio/mpeg` — a player that
trusts the header would otherwise refuse a perfectly good track. In the `dev` profile signed media
URLs are absolute by default (`tuneflow.storage.public-base-url: http://localhost:8080`); set
`TUNEFLOW_PUBLIC_BASE_URL` when the API is reached through another host, or via a CDN.

FFmpeg/FFprobe integration provides duration probing and, behind `tuneflow.media.hls-enabled`,
transcoding/HLS packaging. When the binaries are absent the pipeline degrades to metadata-only
processing instead of failing the upload — a developer machine without FFmpeg can still exercise
the whole upload flow.

---

## Observability

- `/actuator/health` with liveness and readiness groups; Redis health is checked only in `prod`
  (dev runs Caffeine, so a missing Redis must not report the app as down).
- `/actuator/prometheus` — HTTP metrics, JVM metrics, connection pool, and cache metrics
  (`cache_gets_total`, `cache_evictions_total`, …) thanks to `.recordStats()` on the cache manager.
- Structured logging with a per-request correlation id echoed back in the response.
- Every error path logs with context (user, route, error code) while the client receives a
  sanitised message — stack traces never leak to API consumers.

---

## Testing

**44 automated tests, all green** (`mvn test`) — no Docker and no external services required.

| Suite | Type | What it covers |
|---|---|---|
| `JwtServiceImplTest` | Unit | Claim round-trip, tampering, expiry reported separately, foreign signing key, fail-fast on a short secret |
| `LrcParserTest` | Unit | Timestamp precisions, repeated hooks, ordering, LRC detection |
| `RuleBasedRecommendationStrategyTest` | Unit | Cold-start fallback, genre affinity, already-liked exclusion, limit |
| `SongServiceLikeTest` | Unit | Like/unlike rules, counter side effects, soft-delete invisibility, artist ownership vs admin override, guest callers |
| `ChunkedUploadServiceTest` | Unit | The upload-session state machine: server-dictated chunking (payload → part count), out-of-range and oversized parts rejected, resend of a part, owner isolation, byte-count verification on `complete`, TTL expiry and the scheduled purge |
| `StreamingContentTypeTest` | Unit | The stream descriptor never lies about the object: mp3/wav/m4a/flac/ogg map to their real MIME types, an extensionless key falls back to `application/octet-stream`, and HLS always reports the manifest type |
| `ApiIntegrationTest` | Integration | Full journey on the real Spring context (register → login → browse → search → stream → like → playlist → history → lyrics → refresh rotation → logout), plus validation shape and resource-hiding |
| `PostgresIT` | Integration | Real PostgreSQL via Testcontainers: migrations apply, Hibernate `validate` passes, catalog queries and counter updates behave |

```bash
cd backend
mvn test                                                # the 44 hermetic tests

# The PostgreSQL/Testcontainers suite is named *IT (integration test), so it is deliberately not
# bound to the default lifecycle - a developer without Docker still gets a green `mvn test`.
mvn test -Dtest=PostgresIT -DfailIfNoTests=false        # requires Docker
```

The Flutter client carries its own suite (`cd frontend && flutter test` — 101 tests, with a clean
`flutter analyze`); see `frontend/README.md`, which also walks through the two client features the
backend exists to serve: **[adding songs to a playlist](frontend/README.md#adding-songs-to-a-playlist)** (search +
multi-select picker, reachable from the playlist, the album, any track menu and the library) and
**[publishing a song as an artist](frontend/README.md#publishing-a-song-as-an-artist)** (pick audio + cover → upload →
metadata → live in the catalogue).

The integration suite is what caught two real defects during development: the cold-start
recommendation path trusted the repository's page size instead of enforcing the limit itself, and
the search fallback advertised `fuzzyMatched: true` even when it had found nothing.

---

## Deployment

```bash
docker compose --profile app up -d --build
```

- **Dockerfile** — multi-stage Maven → JRE-alpine image, non-root `tuneflow` user, `curl`-based
  container healthcheck against `/actuator/health/readiness`, FFmpeg included for the media
  pipeline, `MaxRAMPercentage` tuned for containers.
- **Reverse proxy** (`deploy/nginx/tuneflow.conf`) — TLS termination, security headers,
  `client_max_body_size 110m`, strict per-IP limits on `/api/v1/auth/`, upstream keepalive.
- **Media** bypasses the gateway entirely in production: clients fetch signed CDN/object-storage
  URLs directly.
- **Scaling** — the application is stateless (JWTs, Redis cache, S3 media), so replicas scale
  horizontally behind the load balancer. Because module boundaries are service interfaces, the
  busiest module (search/streaming/notification) can be extracted into its own service without a
  rewrite — the modular monolith is a staging post, not a dead end.

---

## Scope decisions and roadmap

Deliberate deviations from the documentation, agreed up front:

| Decision | Rationale |
|---|---|
| **No Elasticsearch** | PostgreSQL `pg_trgm`/tokenized search covers the MVP's catalog size. The `SearchProvider` seam keeps the upgrade path open; the compose file has an optional `search` profile when it is needed. |
| **No admin dashboard or admin REST API** | Effort went into the listener/artist experience and the Flutter client instead. Role `ADMIN` still exists and is honoured by ownership rules. |
| **HLS transcoding off by default** | `tuneflow.media.hls-enabled` exists and is wired; progressive streaming with range requests covers MVP playback. |
| **Signed-URL streaming only** | Matches the documentation's hard requirement that audio is not proxied by the API in production. |

Known work remaining:

- Flutter client: background playback via `audio_service`, and real offline files for downloads
  (the API's `PENDING → COMPLETED` licence flow is already wired; writing bytes to disk is not).
- Backend: bulk delete for history clearing (currently row-by-row, capped), artist-cache eviction
  on profile updates, and an outbox for guaranteed at-least-once event delivery under Kafka.
- Optional: Elasticsearch provider, HLS packaging, admin reporting endpoints, offline download
  manifests for the mobile client.

---

## Licence

Provided as a reference implementation for the TuneFlow technical documentation.
