# BandSongbook (Android)

MVP scaffold for a worship/band songbook app:
- Song library (lyrics + chords in ChordPro-like format)
- Transposition (+/- semitones)
- Song auto-scroll with speed control
- Audio attachments per song (URI-based)
- Setlists with named events (e.g. "Воскресенье 29.03.26")

## Stack
- Kotlin
- Jetpack Compose
- Room
- Navigation Compose
- DataStore
- Media3 ExoPlayer

## Project status
Implemented foundation (v0.3):
1. Data model + Room entities/DAO
2. Song CRUD screens
3. Song viewer with chord rendering + transposition + auto-scroll controls
4. Audio attachment list + playback trigger
5. Setlists list/editor (add/remove songs + move up/down ordering)
6. Internet sync foundation (server-driven push/pull for group members)
7. Conflict-aware snapshot merge on pull (newer `updatedAt` wins)
8. Round-trip sync flow: pull -> merge -> push
9. Background periodic sync via WorkManager (configurable interval)
10. Backup import/export JSON from Settings screen
11. Sync auth support via Bearer token/JWT field in app settings
12. Group sync state in Settings (last pusher, server update time, members list)
13. Smarter background sync scheduling (network+battery constraints, exponential backoff, non-retry for hard 4xx errors)

## Internet sync contract
Client expects remote endpoints:
- `POST /sync/push` body: `{ groupCode, snapshot }`
- `POST /sync/pull` body: `{ groupCode }` -> response: `SyncSnapshotDto`
- `POST /sync/meta` body: `{ groupCode }` -> response: group metadata only

If auth is enabled on server, client sends:
- `Authorization: Bearer <token-or-jwt>`

Snapshot includes songs, song audio refs, setlists, and setlist items.
Server may also include metadata in pull response:
- `lastPushedBy`
- `serverUpdatedAt`
- `members`: `[{ name, lastSeenAt }]`

There are two backend options in `sync-server/`:
- `server.py` — minimal reference server
- `fastapi_server.py` — production-friendly FastAPI + SQLite backend

See `sync-server/README.md` for setup and env-based auth.

## Open items
- Better drag-and-drop reordering in setlist
- More robust conflict merge for concurrent setlist order edits
- Proper transposition display for current key
- More robust ChordPro parser/renderer
- UI polishing and validation

## Build locally
Use Android Studio (JDK 17), open project root and run app module.

If you prefer CLI, install Gradle and run:

```bash
./gradlew assembleDebug
```

(Wrapper is not generated in this environment; easiest path is Android Studio sync.)
