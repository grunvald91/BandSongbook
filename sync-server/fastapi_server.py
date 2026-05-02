#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import os
import re
import sqlite3
import time
import urllib.parse
from pathlib import Path
from typing import Any, Optional

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel

try:
    import boto3  # type: ignore
except Exception:
    boto3 = None

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = Path(os.getenv("SYNC_DB_PATH", str(BASE_DIR / "sync.db"))).resolve()
STORE_JSON_PATH = Path(os.getenv("SYNC_STORE_JSON_PATH", str(BASE_DIR / "store.json"))).resolve()
MIGRATE_STORE_ON_START = os.getenv("SYNC_MIGRATE_STORE_ON_START", "1").strip().lower() not in {"0", "false", "no"}

AUTH_MODE = os.getenv("SYNC_AUTH_MODE", "auto").strip().lower()  # off|bearer|jwt|auto
GLOBAL_AUTH_TOKEN = os.getenv("SYNC_AUTH_TOKEN", "").strip()
JWT_SECRET = os.getenv("SYNC_JWT_SECRET", "").strip()

OBJECT_STORAGE_MODE = os.getenv("SYNC_OBJECT_STORAGE_MODE", "off").strip().lower()  # off|s3|local

def _env_int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except Exception:
        return default

OBJECT_URL_TTL_SECONDS = max(60, min(3600, _env_int("SYNC_OBJECT_URL_TTL_SECONDS", 900)))
MAX_AUDIO_FILE_BYTES = max(1, _env_int("SYNC_MAX_AUDIO_FILE_BYTES", 20 * 1024 * 1024))

GROUP_CODE_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.-]{1,63}$")
CONTENT_HASH_RE = re.compile(r"^[0-9a-f]{64}$")
OBJECT_KEY_RE = re.compile(r"^[A-Za-z0-9/_\-.]{6,255}$")

S3_ENDPOINT_URL = os.getenv("SYNC_S3_ENDPOINT_URL", "").strip() or None
S3_REGION = os.getenv("SYNC_S3_REGION", "us-east-1").strip() or "us-east-1"
S3_BUCKET = os.getenv("SYNC_S3_BUCKET", "").strip()
S3_ACCESS_KEY_ID = os.getenv("SYNC_S3_ACCESS_KEY_ID", "").strip()
S3_SECRET_ACCESS_KEY = os.getenv("SYNC_S3_SECRET_ACCESS_KEY", "").strip()
S3_OBJECT_PREFIX = os.getenv("SYNC_S3_OBJECT_PREFIX", "bandsongbook").strip("/")
LOCAL_AUDIO_DIR = Path(os.getenv("SYNC_LOCAL_AUDIO_DIR", str(BASE_DIR / "audio_objects"))).resolve()
URL_SIGNING_SECRET = (
    os.getenv("SYNC_URL_SIGNING_SECRET", "").strip()
    or JWT_SECRET
    or GLOBAL_AUTH_TOKEN
    or "bandsongbook-local-audio-dev-secret"
)




def _load_group_tokens() -> dict[str, str]:
    raw = os.getenv("SYNC_GROUP_TOKENS", "").strip()
    if not raw:
        return {}
    try:
        obj = json.loads(raw)
        if isinstance(obj, dict):
            return {str(k): str(v) for k, v in obj.items()}
    except Exception:
        pass
    return {}


GROUP_TOKENS = _load_group_tokens()


class SyncPullRequest(BaseModel):
    groupCode: str


class SyncPushRequest(BaseModel):
    groupCode: str
    snapshot: dict[str, Any]


class AudioExistsRequest(BaseModel):
    groupCode: str
    contentHash: str


class AudioUploadUrlRequest(BaseModel):
    groupCode: str
    contentHash: str
    mimeType: Optional[str] = None
    sizeBytes: Optional[int] = None
    fileName: Optional[str] = None


class AudioConfirmRequest(BaseModel):
    groupCode: str
    objectKey: str
    contentHash: str
    sizeBytes: Optional[int] = None
    mimeType: Optional[str] = None
    durationMs: Optional[int] = None
    title: Optional[str] = None
    uploadedBy: Optional[str] = None


class AudioDownloadUrlRequest(BaseModel):
    groupCode: str
    objectKey: str


app = FastAPI(title="BandSongbook Sync API", version="1.1.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["POST", "OPTIONS", "GET"],
    allow_headers=["Content-Type", "Authorization"],
)



def _db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn



def _default_snapshot() -> dict[str, Any]:
    return {
        "songs": [],
        "audio": [],
        "setlists": [],
        "setlistItems": [],
        "pushedBy": "",
    }



def _meta_payload(last_pushed_by: str, updated_at: int, members_rows) -> dict[str, Any]:
    return {
        "lastPushedBy": str(last_pushed_by or ""),
        "serverUpdatedAt": int(updated_at or 0),
        "members": [{"name": str(r["member_name"]), "lastSeenAt": int(r["last_seen_at"])} for r in members_rows],
    }



def _extract_bearer_token(authorization: Optional[str]) -> str:
    if not authorization:
        return ""
    if not authorization.startswith("Bearer "):
        return ""
    return authorization[len("Bearer ") :].strip()



def _b64url_decode(data: str) -> bytes:
    pad = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode((data + pad).encode("utf-8"))



def _verify_jwt_hs256(token: str, group_code: str) -> bool:
    if not token or not JWT_SECRET:
        return False

    parts = token.split(".")
    if len(parts) != 3:
        return False

    head_b64, payload_b64, sig_b64 = parts
    signing_input = f"{head_b64}.{payload_b64}".encode("utf-8")

    try:
        header = json.loads(_b64url_decode(head_b64).decode("utf-8"))
        payload = json.loads(_b64url_decode(payload_b64).decode("utf-8"))
    except Exception:
        return False

    if header.get("alg") != "HS256":
        return False

    expected_sig = hmac.new(JWT_SECRET.encode("utf-8"), signing_input, hashlib.sha256).digest()
    try:
        got_sig = _b64url_decode(sig_b64)
    except Exception:
        return False

    if not hmac.compare_digest(expected_sig, got_sig):
        return False

    exp = payload.get("exp")
    if exp is not None:
        try:
            if int(exp) < int(time.time()):
                return False
        except Exception:
            return False

    token_group = payload.get("groupCode")
    token_groups = payload.get("groups")

    if isinstance(token_group, str):
        return token_group == group_code
    if isinstance(token_groups, list):
        return group_code in [str(x) for x in token_groups]

    return False



def _verify_bearer(token: str, group_code: str) -> bool:
    if not token:
        return False
    group_token = GROUP_TOKENS.get(group_code)
    if group_token:
        return hmac.compare_digest(token, group_token)
    if GLOBAL_AUTH_TOKEN:
        return hmac.compare_digest(token, GLOBAL_AUTH_TOKEN)
    return False



def _is_auth_enabled() -> bool:
    if AUTH_MODE == "off":
        return False
    if AUTH_MODE in {"bearer", "jwt"}:
        return True
    return bool(GLOBAL_AUTH_TOKEN or GROUP_TOKENS or JWT_SECRET)



def _enforce_access(group_code: str, authorization: Optional[str]) -> None:
    if not _is_auth_enabled():
        return

    token = _extract_bearer_token(authorization)
    if AUTH_MODE == "bearer":
        if not _verify_bearer(token, group_code):
            raise HTTPException(401, "invalid bearer token")
        return
    if AUTH_MODE == "jwt":
        if not _verify_jwt_hs256(token, group_code):
            raise HTTPException(401, "invalid jwt token")
        return

    if _verify_bearer(token, group_code) or _verify_jwt_hs256(token, group_code):
        return
    raise HTTPException(401, "invalid token")



def _is_storage_enabled() -> bool:
    if OBJECT_STORAGE_MODE == "s3":
        return bool(S3_BUCKET)
    if OBJECT_STORAGE_MODE == "local":
        return True
    return False


def _is_local_storage_enabled() -> bool:
    return OBJECT_STORAGE_MODE == "local"



def _validate_group_code(value: str) -> str:
    group = value.strip()
    if not GROUP_CODE_RE.fullmatch(group):
        raise HTTPException(400, "invalid groupCode format")
    return group



def _validate_content_hash(value: str) -> str:
    content_hash = value.strip().lower()
    if not CONTENT_HASH_RE.fullmatch(content_hash):
        raise HTTPException(400, "contentHash must be lowercase sha256 hex")
    return content_hash



def _expected_group_prefix(group_code: str) -> str:
    prefix = f"{S3_OBJECT_PREFIX}/" if S3_OBJECT_PREFIX else ""
    g = group_code.strip().replace("/", "_")
    return f"{prefix}{g}/"



def _validate_object_key(value: str, group_code: str) -> str:
    object_key = value.strip()
    if not OBJECT_KEY_RE.fullmatch(object_key):
        raise HTTPException(400, "invalid objectKey format")
    expected_prefix = _expected_group_prefix(group_code)
    if _is_storage_enabled() and not object_key.startswith(expected_prefix):
        raise HTTPException(400, "objectKey does not match group scope")
    return object_key



def _s3_client():
    if not _is_storage_enabled():
        return None
    if boto3 is None:
        raise HTTPException(503, "boto3 is not installed on sync server")

    kwargs = {
        "region_name": S3_REGION,
        "aws_access_key_id": S3_ACCESS_KEY_ID or None,
        "aws_secret_access_key": S3_SECRET_ACCESS_KEY or None,
    }
    if S3_ENDPOINT_URL:
        kwargs["endpoint_url"] = S3_ENDPOINT_URL
    return boto3.client("s3", **kwargs)



def _detect_extension(file_name: Optional[str], mime_type: Optional[str]) -> str:
    name = (file_name or "").strip().lower()
    if "." in name and not name.endswith("."):
        ext = name.rsplit(".", 1)[1]
        if ext and len(ext) <= 8:
            return ext
    mapping = {
        "audio/mpeg": "mp3",
        "audio/mp4": "m4a",
        "audio/aac": "aac",
        "audio/ogg": "ogg",
        "audio/opus": "opus",
        "audio/wav": "wav",
        "audio/x-wav": "wav",
        "audio/flac": "flac",
        "audio/webm": "webm",
    }
    return mapping.get((mime_type or "").strip().lower(), "bin")



def _build_object_key(group_code: str, content_hash: str, file_name: Optional[str], mime_type: Optional[str]) -> str:
    ext = _detect_extension(file_name, mime_type)
    prefix = f"{S3_OBJECT_PREFIX}/" if S3_OBJECT_PREFIX else ""
    g = group_code.strip().replace("/", "_")
    h = content_hash.strip().lower()
    return f"{prefix}{g}/{h[:2]}/{h}.{ext}"



def _local_audio_path(object_key: str) -> Path:
    safe_key = object_key.strip().lstrip("/")
    path = (LOCAL_AUDIO_DIR / safe_key).resolve()
    base = LOCAL_AUDIO_DIR.resolve()
    if path != base and base not in path.parents:
        raise HTTPException(400, "invalid local audio path")
    return path



def _build_local_signature(object_key: str, expires_at: int) -> str:
    payload = f"{object_key}:{int(expires_at)}".encode("utf-8")
    return hmac.new(URL_SIGNING_SECRET.encode("utf-8"), payload, hashlib.sha256).hexdigest()



def _build_local_download_url(base_url: str, object_key: str) -> str:
    expires_at = int(time.time()) + OBJECT_URL_TTL_SECONDS
    sig = _build_local_signature(object_key, expires_at)
    quoted_key = urllib.parse.quote(object_key, safe="/")
    return f"{base_url.rstrip('/')}/audio/file/{quoted_key}?exp={expires_at}&sig={sig}"



def _build_local_upload_url(base_url: str, group_code: str, content_hash: str, object_key: str) -> str:
    params = urllib.parse.urlencode({
        "groupCode": group_code,
        "contentHash": content_hash,
        "objectKey": object_key,
    })
    return f"{base_url.rstrip('/')}/audio/upload-local?{params}"



def _signed_put_url(object_key: str, content_type: Optional[str], size_bytes: Optional[int]):
    s3 = _s3_client()
    if s3 is None:
        raise HTTPException(503, "object storage is disabled")
    params: dict[str, Any] = {"Bucket": S3_BUCKET, "Key": object_key}
    if content_type:
        params["ContentType"] = content_type
    if size_bytes is not None and size_bytes >= 0:
        params["ContentLength"] = int(size_bytes)
    url = s3.generate_presigned_url(
        "put_object",
        Params=params,
        ExpiresIn=OBJECT_URL_TTL_SECONDS,
        HttpMethod="PUT",
    )
    return url



def _signed_get_url(object_key: str) -> str:
    s3 = _s3_client()
    if s3 is None:
        raise HTTPException(503, "object storage is disabled")
    return s3.generate_presigned_url(
        "get_object",
        Params={"Bucket": S3_BUCKET, "Key": object_key},
        ExpiresIn=OBJECT_URL_TTL_SECONDS,
        HttpMethod="GET",
    )



def _s3_object_exists(object_key: str) -> bool:
    s3 = _s3_client()
    if s3 is None:
        return False
    try:
        s3.head_object(Bucket=S3_BUCKET, Key=object_key)
        return True
    except Exception:
        return False



def _register_pending_upload(conn: sqlite3.Connection, group_code: str, content_hash: str, object_key: str) -> None:
    now = int(time.time())
    expires_at = now + OBJECT_URL_TTL_SECONDS
    conn.execute(
        """
        INSERT INTO pending_audio_uploads(group_code, content_hash, object_key, expires_at, created_at)
        VALUES(?, ?, ?, ?, ?)
        ON CONFLICT(group_code, content_hash, object_key) DO UPDATE SET
            expires_at=excluded.expires_at
        """,
        (group_code, content_hash, object_key, expires_at, now),
    )



def _pending_upload_exists(conn: sqlite3.Connection, group_code: str, content_hash: str, object_key: str) -> bool:
    now = int(time.time())
    row = conn.execute(
        """
        SELECT expires_at FROM pending_audio_uploads
        WHERE group_code=? AND content_hash=? AND object_key=?
        LIMIT 1
        """,
        (group_code, content_hash, object_key),
    ).fetchone()
    if row is None:
        return False

    if int(row["expires_at"] or 0) < now:
        conn.execute(
            "DELETE FROM pending_audio_uploads WHERE group_code=? AND content_hash=? AND object_key=?",
            (group_code, content_hash, object_key),
        )
        return False
    return True



def _consume_pending_upload(conn: sqlite3.Connection, group_code: str, content_hash: str, object_key: str) -> bool:
    if not _pending_upload_exists(conn, group_code, content_hash, object_key):
        return False
    conn.execute(
        "DELETE FROM pending_audio_uploads WHERE group_code=? AND content_hash=?",
        (group_code, content_hash),
    )
    return True



def _audio_mapping_by_hash(conn: sqlite3.Connection, group_code: str, content_hash: str):
    return conn.execute(
        """
        SELECT ga.object_key, ao.size_bytes, ao.mime_type
        FROM group_audio_hash ga
        LEFT JOIN audio_objects ao ON ao.object_key = ga.object_key
        WHERE ga.group_code = ? AND ga.content_hash = ?
        LIMIT 1
        """,
        (group_code, content_hash),
    ).fetchone()



def _audio_mapping_by_object(conn: sqlite3.Connection, group_code: str, object_key: str):
    return conn.execute(
        """
        SELECT ga.object_key, ga.content_hash, ao.size_bytes, ao.mime_type
        FROM group_audio_hash ga
        LEFT JOIN audio_objects ao ON ao.object_key = ga.object_key
        WHERE ga.group_code = ? AND ga.object_key = ?
        LIMIT 1
        """,
        (group_code, object_key),
    ).fetchone()



def _register_audio_mapping(
    conn: sqlite3.Connection,
    group_code: str,
    object_key: str,
    content_hash: str,
    size_bytes: Optional[int],
    mime_type: Optional[str],
    uploaded_by: Optional[str],
):
    now = int(time.time())
    conn.execute(
        """
        INSERT INTO audio_objects(object_key, content_hash, size_bytes, mime_type, created_at, updated_at, uploaded_by)
        VALUES(?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(object_key) DO UPDATE SET
            content_hash=excluded.content_hash,
            size_bytes=COALESCE(excluded.size_bytes, audio_objects.size_bytes),
            mime_type=COALESCE(excluded.mime_type, audio_objects.mime_type),
            updated_at=excluded.updated_at,
            uploaded_by=COALESCE(excluded.uploaded_by, audio_objects.uploaded_by)
        """,
        (object_key, content_hash, size_bytes, mime_type, now, now, uploaded_by),
    )
    conn.execute(
        """
        INSERT INTO group_audio_hash(group_code, content_hash, object_key, created_at, updated_at)
        VALUES(?, ?, ?, ?, ?)
        ON CONFLICT(group_code, content_hash) DO UPDATE SET
            object_key=excluded.object_key,
            updated_at=excluded.updated_at
        """,
        (group_code, content_hash, object_key, now, now),
    )



def _decorate_snapshot_audio_with_signed_urls(group_code: str, snapshot: dict[str, Any], base_url: str) -> dict[str, Any]:
    if not _is_storage_enabled():
        return snapshot

    audio_items = snapshot.get("audio")
    if not isinstance(audio_items, list) or not audio_items:
        return snapshot

    with _db() as conn:
        for item in audio_items:
            if not isinstance(item, dict):
                continue
            object_key = str(item.get("objectKey") or "").strip()
            content_hash = str(item.get("contentHash") or "").strip().lower()

            if not object_key and content_hash:
                row = _audio_mapping_by_hash(conn, group_code, content_hash)
                if row is not None:
                    object_key = str(row["object_key"])
                    item["objectKey"] = object_key
                    if not item.get("sizeBytes") and row["size_bytes"] is not None:
                        item["sizeBytes"] = int(row["size_bytes"])
                    if not item.get("mimeType") and row["mime_type"]:
                        item["mimeType"] = str(row["mime_type"])

            if object_key:
                try:
                    item["remoteUrl"] = (
                        _build_local_download_url(base_url, object_key)
                        if _is_local_storage_enabled()
                        else _signed_get_url(object_key)
                    )
                except Exception:
                    pass

    return snapshot



def _init_db() -> None:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with _db() as conn:
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS groups (
                group_code TEXT PRIMARY KEY,
                snapshot_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                last_pushed_by TEXT NOT NULL DEFAULT ''
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS group_members (
                group_code TEXT NOT NULL,
                member_name TEXT NOT NULL,
                last_seen_at INTEGER NOT NULL,
                PRIMARY KEY (group_code, member_name),
                FOREIGN KEY (group_code) REFERENCES groups(group_code) ON DELETE CASCADE
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS audio_objects (
                object_key TEXT PRIMARY KEY,
                content_hash TEXT NOT NULL,
                size_bytes INTEGER,
                mime_type TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                uploaded_by TEXT
            )
            """
        )
        conn.execute(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS idx_audio_objects_content_hash
            ON audio_objects(content_hash)
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS group_audio_hash (
                group_code TEXT NOT NULL,
                content_hash TEXT NOT NULL,
                object_key TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (group_code, content_hash)
            )
            """
        )
        conn.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_group_audio_hash_object
            ON group_audio_hash(group_code, object_key)
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS pending_audio_uploads (
                group_code TEXT NOT NULL,
                content_hash TEXT NOT NULL,
                object_key TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (group_code, content_hash, object_key)
            )
            """
        )
        conn.execute(
            """
            CREATE INDEX IF NOT EXISTS idx_pending_audio_uploads_expiry
            ON pending_audio_uploads(expires_at)
            """
        )
        conn.commit()



def _migrate_store_json_if_needed() -> None:
    if not MIGRATE_STORE_ON_START or not STORE_JSON_PATH.exists():
        return

    with _db() as conn:
        existing = conn.execute("SELECT COUNT(*) AS c FROM groups").fetchone()["c"]
        if existing > 0:
            return

    try:
        raw = json.loads(STORE_JSON_PATH.read_text(encoding="utf-8"))
    except Exception:
        return

    if not isinstance(raw, dict):
        return

    now = int(time.time())
    with _db() as conn:
        for group_code, value in raw.items():
            if not isinstance(group_code, str):
                continue
            snapshot: dict[str, Any]
            updated_at = now
            last_pushed_by = ""
            members: dict[str, int] = {}

            if isinstance(value, dict) and "snapshot" in value and isinstance(value["snapshot"], dict):
                snapshot = dict(value["snapshot"])
                updated_at = int(value.get("updatedAt", now) or now)
                last_pushed_by = str(value.get("lastPushedBy", "") or "")
                raw_members = value.get("members")
                if isinstance(raw_members, dict):
                    members = {str(k): int(v) for k, v in raw_members.items() if isinstance(k, str)}
            elif isinstance(value, dict):
                snapshot = dict(value)
                last_pushed_by = str(snapshot.get("pushedBy", "") or "")
            else:
                snapshot = _default_snapshot()

            conn.execute(
                """
                INSERT INTO groups(group_code, snapshot_json, updated_at, last_pushed_by)
                VALUES(?, ?, ?, ?)
                ON CONFLICT(group_code) DO UPDATE SET
                    snapshot_json=excluded.snapshot_json,
                    updated_at=excluded.updated_at,
                    last_pushed_by=excluded.last_pushed_by
                """,
                (group_code.strip(), json.dumps(snapshot, ensure_ascii=False), updated_at, last_pushed_by),
            )
            for name, ts in members.items():
                conn.execute(
                    """
                    INSERT INTO group_members(group_code, member_name, last_seen_at)
                    VALUES(?, ?, ?)
                    ON CONFLICT(group_code, member_name) DO UPDATE SET
                        last_seen_at=excluded.last_seen_at
                    """,
                    (group_code.strip(), name, int(ts)),
                )
        conn.commit()


@app.on_event("startup")
def startup_event() -> None:
    _init_db()
    _migrate_store_json_if_needed()


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "ok": True,
        "db": str(DB_PATH),
        "authMode": AUTH_MODE,
        "bearerConfigured": bool(GLOBAL_AUTH_TOKEN or GROUP_TOKENS),
        "jwtConfigured": bool(JWT_SECRET),
        "objectStorageMode": OBJECT_STORAGE_MODE,
        "objectStorageEnabled": _is_storage_enabled(),
        "s3BucketConfigured": bool(S3_BUCKET),
        "localAudioDir": str(LOCAL_AUDIO_DIR) if _is_local_storage_enabled() else "",
    }


@app.post("/sync/push")
def sync_push(req: SyncPushRequest, authorization: Optional[str] = Header(default=None)) -> dict[str, Any]:
    group = _validate_group_code(req.groupCode)

    _enforce_access(group, authorization)

    snapshot = req.snapshot or _default_snapshot()
    now = int(time.time())
    pushed_by = str(snapshot.get("pushedBy", "") or "").strip()

    with _db() as conn:
        conn.execute(
            """
            INSERT INTO groups(group_code, snapshot_json, updated_at, last_pushed_by)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(group_code) DO UPDATE SET
                snapshot_json=excluded.snapshot_json,
                updated_at=excluded.updated_at,
                last_pushed_by=excluded.last_pushed_by
            """,
            (group, json.dumps(snapshot, ensure_ascii=False), now, pushed_by),
        )

        if pushed_by:
            conn.execute(
                """
                INSERT INTO group_members(group_code, member_name, last_seen_at)
                VALUES(?, ?, ?)
                ON CONFLICT(group_code, member_name) DO UPDATE SET
                    last_seen_at=excluded.last_seen_at
                """,
                (group, pushed_by, now),
            )
        conn.commit()

    return {"ok": True, "serverUpdatedAt": now}


@app.post("/sync/pull")
def sync_pull(req: SyncPullRequest, request: Request, authorization: Optional[str] = Header(default=None)) -> dict[str, Any]:
    group = _validate_group_code(req.groupCode)

    _enforce_access(group, authorization)

    with _db() as conn:
        row = conn.execute(
            "SELECT snapshot_json, updated_at, last_pushed_by FROM groups WHERE group_code=?",
            (group,),
        ).fetchone()

        if row is None:
            snapshot = _default_snapshot()
            updated_at = 0
            last_pushed_by = ""
            members_rows = []
        else:
            try:
                snapshot = json.loads(row["snapshot_json"])
            except Exception:
                snapshot = _default_snapshot()
            updated_at = int(row["updated_at"] or 0)
            last_pushed_by = str(row["last_pushed_by"] or "")
            members_rows = conn.execute(
                "SELECT member_name, last_seen_at FROM group_members WHERE group_code=? ORDER BY lower(member_name)",
                (group,),
            ).fetchall()

    if not isinstance(snapshot, dict):
        snapshot = _default_snapshot()

    snapshot = _decorate_snapshot_audio_with_signed_urls(group, snapshot, str(request.base_url))

    response = dict(snapshot)
    response.update(_meta_payload(last_pushed_by, updated_at, members_rows))
    return response


@app.post("/sync/meta")
def sync_meta(req: SyncPullRequest, authorization: Optional[str] = Header(default=None)) -> dict[str, Any]:
    group = _validate_group_code(req.groupCode)

    _enforce_access(group, authorization)

    with _db() as conn:
        row = conn.execute(
            "SELECT updated_at, last_pushed_by FROM groups WHERE group_code=?",
            (group,),
        ).fetchone()

        if row is None:
            updated_at = 0
            last_pushed_by = ""
            members_rows = []
        else:
            updated_at = int(row["updated_at"] or 0)
            last_pushed_by = str(row["last_pushed_by"] or "")
            members_rows = conn.execute(
                "SELECT member_name, last_seen_at FROM group_members WHERE group_code=? ORDER BY lower(member_name)",
                (group,),
            ).fetchall()

    return _meta_payload(last_pushed_by, updated_at, members_rows)


@app.post("/audio/exists")
def audio_exists(req: AudioExistsRequest, request: Request, authorization: Optional[str] = Header(default=None)) -> dict[str, Any]:
    group = _validate_group_code(req.groupCode)
    content_hash = _validate_content_hash(req.contentHash)

    _enforce_access(group, authorization)

    with _db() as conn:
        row = _audio_mapping_by_hash(conn, group, content_hash)

    if row is None:
        return {"exists": False}

    object_key = str(row["object_key"])
    remote_url = (
        _build_local_download_url(str(request.base_url), object_key)
        if _is_local_storage_enabled()
        else (_signed_get_url(object_key) if _is_storage_enabled() else None)
    )
    return {
        "exists": True,
        "objectKey": object_key,
        "remoteUrl": remote_url,
        "sizeBytes": int(row["size_bytes"]) if row["size_bytes"] is not None else None,
        "mimeType": str(row["mime_type"]) if row["mime_type"] else None,
    }


@app.post("/audio/upload-url")
def audio_upload_url(req: AudioUploadUrlRequest, request: Request, authorization: Optional[str] = Header(default=None)) -> dict[str, Any]:
    group = _validate_group_code(req.groupCode)
    content_hash = _validate_content_hash(req.contentHash)

    _enforce_access(group, authorization)

    if req.sizeBytes is not None and int(req.sizeBytes) > MAX_AUDIO_FILE_BYTES:
        raise HTTPException(413, f"audio file too large (max {MAX_AUDIO_FILE_BYTES} bytes)")

    with _db() as conn:
        existing = _audio_mapping_by_hash(conn, group, content_hash)

        if existing is not None:
            object_key = str(existing["object_key"])
            return {
                "exists": True,
                "objectKey": object_key,
                "remoteUrl": (
                    _build_local_download_url(str(request.base_url), object_key)
                    if _is_local_storage_enabled()
                    else (_signed_get_url(object_key) if _is_storage_enabled() else None)
                ),
                "expiresAt": int(time.time()) + OBJECT_URL_TTL_SECONDS,
                "headers": {},
            }

        if not _is_storage_enabled():
            raise HTTPException(503, "object storage is disabled")

        object_key = _build_object_key(group, content_hash, req.fileName, req.mimeType)
        _register_pending_upload(conn, group, content_hash, object_key)
        upload_url = (
            _build_local_upload_url(str(request.base_url), group, content_hash, object_key)
            if _is_local_storage_enabled()
            else _signed_put_url(object_key, req.mimeType, req.sizeBytes)
        )
        conn.commit()

    return {
        "exists": False,
        "objectKey": object_key,
        "uploadUrl": upload_url,
        "headers": {},
        "expiresAt": int(time.time()) + OBJECT_URL_TTL_SECONDS,
    }


@app.put("/audio/upload-local")
async def audio_upload_local(
    request: Request,
    groupCode: str,
    contentHash: str,
    objectKey: str,
) -> dict[str, Any]:
    if not _is_local_storage_enabled():
        raise HTTPException(404, "local audio upload is disabled")

    group = _validate_group_code(groupCode)
    content_hash = _validate_content_hash(contentHash)
    object_key = _validate_object_key(objectKey, group)

    body = await request.body()
    if not body:
        raise HTTPException(400, "empty audio body")
    if len(body) > MAX_AUDIO_FILE_BYTES:
        raise HTTPException(413, f"audio file too large (max {MAX_AUDIO_FILE_BYTES} bytes)")

    with _db() as conn:
        if not _pending_upload_exists(conn, group, content_hash, object_key):
            raise HTTPException(400, "upload token is missing or expired")

    file_path = _local_audio_path(object_key)
    file_path.parent.mkdir(parents=True, exist_ok=True)
    file_path.write_bytes(body)
    return {"ok": True, "bytes": len(body)}


@app.post("/audio/confirm")
def audio_confirm(req: AudioConfirmRequest, request: Request, authorization: Optional[str] = Header(default=None)) -> dict[str, Any]:
    group = _validate_group_code(req.groupCode)
    content_hash = _validate_content_hash(req.contentHash)
    object_key = _validate_object_key(req.objectKey, group)

    _enforce_access(group, authorization)

    if req.sizeBytes is not None and int(req.sizeBytes) > MAX_AUDIO_FILE_BYTES:
        raise HTTPException(413, f"audio file too large (max {MAX_AUDIO_FILE_BYTES} bytes)")

    if _is_local_storage_enabled():
        if not _local_audio_path(object_key).exists():
            raise HTTPException(400, "audio object was not uploaded")
    elif _is_storage_enabled() and not _s3_object_exists(object_key):
        raise HTTPException(400, "audio object was not uploaded")

    with _db() as conn:
        if not _consume_pending_upload(conn, group, content_hash, object_key):
            raise HTTPException(400, "upload confirmation token is missing or expired")

        _register_audio_mapping(
            conn=conn,
            group_code=group,
            object_key=object_key,
            content_hash=content_hash,
            size_bytes=req.sizeBytes,
            mime_type=req.mimeType,
            uploaded_by=(req.uploadedBy or "").strip() or None,
        )
        conn.commit()

    remote_url = (
        _build_local_download_url(str(request.base_url), object_key)
        if _is_local_storage_enabled()
        else (_signed_get_url(object_key) if _is_storage_enabled() else None)
    )
    return {"ok": True, "objectKey": object_key, "remoteUrl": remote_url}


@app.post("/audio/download-url")
def audio_download_url(req: AudioDownloadUrlRequest, request: Request, authorization: Optional[str] = Header(default=None)) -> dict[str, Any]:
    group = _validate_group_code(req.groupCode)
    object_key = _validate_object_key(req.objectKey, group)

    _enforce_access(group, authorization)

    with _db() as conn:
        row = _audio_mapping_by_object(conn, group, object_key)

    if row is None:
        raise HTTPException(404, "audio object is not linked to this group")

    if not _is_storage_enabled():
        raise HTTPException(503, "object storage is disabled")

    download_url = (
        _build_local_download_url(str(request.base_url), object_key)
        if _is_local_storage_enabled()
        else _signed_get_url(object_key)
    )
    return {
        "objectKey": object_key,
        "downloadUrl": download_url,
        "expiresAt": int(time.time()) + OBJECT_URL_TTL_SECONDS,
    }


@app.get("/audio/file/{object_key:path}")
def audio_file(object_key: str, exp: int, sig: str):
    if not _is_local_storage_enabled():
        raise HTTPException(404, "local audio files are disabled")
    now = int(time.time())
    if exp < now:
        raise HTTPException(403, "audio url expired")
    if not hmac.compare_digest(sig, _build_local_signature(object_key, exp)):
        raise HTTPException(403, "invalid audio signature")
    file_path = _local_audio_path(object_key)
    if not file_path.exists():
        raise HTTPException(404, "audio file not found")
    media_type = None
    try:
        import mimetypes
        media_type = mimetypes.guess_type(file_path.name)[0]
    except Exception:
        media_type = None
    return FileResponse(path=file_path, media_type=media_type or "application/octet-stream", filename=file_path.name)


if __name__ == "__main__":
    import uvicorn

    host = os.getenv("SYNC_HOST", "0.0.0.0")
    port = int(os.getenv("SYNC_PORT", "8787"))
    uvicorn.run("fastapi_server:app", host=host, port=port, reload=False)
