BandSongbook sync server

В папке есть 2 варианта сервера:

1) server.py — простой MVP (http.server + JSON-файл)
2) fastapi_server.py — production-friendly вариант (FastAPI + SQLite)

Оба поддерживают API:
- POST /sync/push { groupCode, snapshot }
- POST /sync/pull { groupCode }
- POST /sync/meta { groupCode }  (только метаданные группы, без полного snapshot)

FastAPI вариант дополнительно поддерживает audio object-storage API:
- POST /audio/exists { groupCode, contentHash }
- POST /audio/upload-url { groupCode, contentHash, mimeType?, sizeBytes?, fileName? }
- POST /audio/confirm { groupCode, objectKey, contentHash, ... }
- POST /audio/download-url { groupCode, objectKey }

/sync/pull response содержит snapshot + метаданные:
- lastPushedBy
- serverUpdatedAt
- members: [{ name, lastSeenAt }]

/sync/meta response содержит только метаданные:
- lastPushedBy
- serverUpdatedAt
- members: [{ name, lastSeenAt }]

Auth (для обоих серверов)
- Клиент отправляет: Authorization: Bearer <token>
- Настройка через env:
  - SYNC_AUTH_MODE=auto|off|bearer|jwt
  - SYNC_AUTH_TOKEN=<global static token>
  - SYNC_GROUP_TOKENS='{"groupA":"tokenA","groupB":"tokenB"}'
  - SYNC_JWT_SECRET=<HS256 secret>

Поведение режимов:
- off: auth отключён
- bearer: только static bearer token
- jwt: только HS256 JWT (payload: groupCode или groups[], optional exp)
- auto: если auth env заданы, принимает bearer или jwt; если не заданы — работает без auth

----------------------------
FastAPI + SQLite (рекомендуется)
----------------------------
Установка:
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

Запуск:
SYNC_PORT=8787 SYNC_AUTH_MODE=bearer SYNC_AUTH_TOKEN=CHANGE_ME \
python3 fastapi_server.py

Или через uvicorn:
uvicorn fastapi_server:app --host 0.0.0.0 --port 8787

Проверка:
GET /health

Хранилище:
- SQLite DB: sync.db (можно переопределить SYNC_DB_PATH)
- При старте можно мигрировать старый store.json в SQLite:
  - SYNC_MIGRATE_STORE_ON_START=1 (default)
  - SYNC_STORE_JSON_PATH=<path>

Object storage для аудио поддерживает 2 режима:

1) S3-compatible:
- SYNC_OBJECT_STORAGE_MODE=s3
- SYNC_S3_BUCKET=<bucket>
- SYNC_S3_REGION=<region>
- SYNC_S3_ENDPOINT_URL=<endpoint, optional for AWS>
- SYNC_S3_ACCESS_KEY_ID=<key>
- SYNC_S3_SECRET_ACCESS_KEY=<secret>
- SYNC_S3_OBJECT_PREFIX=bandsongbook   (optional)
- SYNC_OBJECT_URL_TTL_SECONDS=900       (signed URL TTL)

2) Локальное хранение на самом сервере:
- SYNC_OBJECT_STORAGE_MODE=local
- SYNC_LOCAL_AUDIO_DIR=/absolute/path/to/audio_objects   (optional)
- SYNC_URL_SIGNING_SECRET=<random-secret-for-download-links>   (recommended)
- SYNC_OBJECT_URL_TTL_SECONDS=900

В режиме local сервер:
- принимает PUT upload во внутренний local endpoint
- сохраняет аудио на диск сервера
- отдаёт подписанные download URL без S3

Если object storage выключен (SYNC_OBJECT_STORAGE_MODE=off), audio endpoints вернут 503.

----------------------------
MVP server.py (legacy/simple)
----------------------------
Запуск:
python3 server.py

Хранилище:
- store.json рядом с server.py

----------------------------
Android app Settings
----------------------------
Заполни:
- URL: http://<server-ip>:8787
- Group code: например fithealthzone-main
- Member name: твоё имя
- Token/JWT: нужный bearer token или jwt (если auth включён)
