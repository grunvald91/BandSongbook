#!/usr/bin/env python3
import base64
import hashlib
import hmac
import json
import os
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any

STORE_PATH = Path(__file__).with_name("store.json")

AUTH_MODE = os.getenv("SYNC_AUTH_MODE", "auto").strip().lower()  # off | bearer | jwt | auto
GLOBAL_AUTH_TOKEN = os.getenv("SYNC_AUTH_TOKEN", "").strip()
JWT_SECRET = os.getenv("SYNC_JWT_SECRET", "").strip()


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


def load_store() -> dict[str, Any]:
    if STORE_PATH.exists():
        return json.loads(STORE_PATH.read_text(encoding="utf-8"))
    return {}


def save_store(data: dict[str, Any]) -> None:
    STORE_PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def _extract_bearer_token(headers) -> str:
    auth = headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return ""
    return auth[len("Bearer ") :].strip()


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
    if AUTH_MODE in {"off"}:
        return False
    if AUTH_MODE in {"bearer", "jwt"}:
        return True
    # auto mode
    return bool(GLOBAL_AUTH_TOKEN or GROUP_TOKENS or JWT_SECRET)


def verify_access(headers, group_code: str) -> tuple[bool, str]:
    if not _is_auth_enabled():
        return True, ""

    token = _extract_bearer_token(headers)

    if AUTH_MODE == "bearer":
        ok = _verify_bearer(token, group_code)
        return (ok, "" if ok else "invalid bearer token")

    if AUTH_MODE == "jwt":
        ok = _verify_jwt_hs256(token, group_code)
        return (ok, "" if ok else "invalid jwt token")

    # auto mode: accept either configured bearer OR configured jwt
    bearer_ok = _verify_bearer(token, group_code)
    jwt_ok = _verify_jwt_hs256(token, group_code)
    return (bearer_ok or jwt_ok, "invalid token")


def _default_snapshot() -> dict[str, Any]:
    return {
        "songs": [],
        "audio": [],
        "setlists": [],
        "setlistItems": [],
        "pushedBy": "",
    }


def _extract_snapshot(group_record: Any) -> dict[str, Any]:
    # backward compatibility: old shape was directly snapshot object
    if isinstance(group_record, dict) and "snapshot" in group_record and isinstance(group_record["snapshot"], dict):
        return dict(group_record["snapshot"])
    if isinstance(group_record, dict):
        return dict(group_record)
    return _default_snapshot()


def _extract_group_meta(group_record: Any) -> dict[str, Any]:
    if not isinstance(group_record, dict):
        return {"lastPushedBy": "", "serverUpdatedAt": 0, "members": {}}

    members = group_record.get("members")
    if not isinstance(members, dict):
        members = {}

    return {
        "lastPushedBy": str(group_record.get("lastPushedBy", "") or ""),
        "serverUpdatedAt": int(group_record.get("updatedAt", 0) or 0),
        "members": {str(k): int(v) for k, v in members.items() if isinstance(k, str)},
    }


def _meta_payload(meta: dict[str, Any]) -> dict[str, Any]:
    members_map = meta.get("members") or {}
    return {
        "lastPushedBy": str(meta.get("lastPushedBy", "") or ""),
        "serverUpdatedAt": int(meta.get("serverUpdatedAt", 0) or 0),
        "members": [
            {"name": name, "lastSeenAt": int(ts)}
            for name, ts in sorted(members_map.items(), key=lambda x: x[0].lower())
        ],
    }


def _inject_meta_into_snapshot(snapshot: dict[str, Any], meta: dict[str, Any]) -> dict[str, Any]:
    out = dict(snapshot)
    out.update(_meta_payload(meta))
    return out


class Handler(BaseHTTPRequestHandler):
    def _json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.send_header("Access-Control-Allow-Methods", "POST, OPTIONS")
        self.end_headers()

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        try:
            body = self.rfile.read(length).decode("utf-8") if length else "{}"
            data = json.loads(body)
        except Exception:
            return self._json(400, {"error": "invalid json"})

        if self.path == "/sync/push":
            group = str(data.get("groupCode", "")).strip()
            snapshot = data.get("snapshot")
            if not group or snapshot is None:
                return self._json(400, {"error": "groupCode and snapshot required"})

            allowed, reason = verify_access(self.headers, group)
            if not allowed:
                return self._json(401, {"error": reason})

            now = int(time.time())
            pushed_by = snapshot.get("pushedBy", "") if isinstance(snapshot, dict) else ""

            store = load_store()
            existing = store.get(group, {})
            meta = _extract_group_meta(existing)
            members = dict(meta.get("members") or {})
            if isinstance(pushed_by, str) and pushed_by.strip():
                members[pushed_by.strip()] = now

            store[group] = {
                "snapshot": snapshot,
                "updatedAt": now,
                "lastPushedBy": pushed_by.strip() if isinstance(pushed_by, str) else "",
                "members": members,
            }
            save_store(store)
            return self._json(200, {"ok": True, "serverUpdatedAt": now})

        if self.path in {"/sync/pull", "/sync/meta"}:
            group = str(data.get("groupCode", "")).strip()
            if not group:
                return self._json(400, {"error": "groupCode required"})

            allowed, reason = verify_access(self.headers, group)
            if not allowed:
                return self._json(401, {"error": reason})

            store = load_store()
            record = store.get(group, _default_snapshot())
            meta = _extract_group_meta(record)

            if self.path == "/sync/meta":
                return self._json(200, _meta_payload(meta))

            snapshot = _extract_snapshot(record)
            return self._json(200, _inject_meta_into_snapshot(snapshot, meta))

        return self._json(404, {"error": "not found"})


if __name__ == "__main__":
    host = os.getenv("SYNC_HOST", "0.0.0.0")
    port = int(os.getenv("SYNC_PORT", "8787"))
    print(f"Sync server listening on http://{host}:{port}")
    print(
        f"Auth mode={AUTH_MODE}, bearer={'yes' if (GLOBAL_AUTH_TOKEN or GROUP_TOKENS) else 'no'}, jwt={'yes' if JWT_SECRET else 'no'}"
    )
    HTTPServer((host, port), Handler).serve_forever()
