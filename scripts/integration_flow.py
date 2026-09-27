#!/usr/bin/env python3
"""Exercise the primary local flow against docker compose; no external accounts needed."""
import json
import os
import time
import uuid
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = os.getenv("API_URL", "http://localhost:8080")
PASSWORD = os.getenv("DEMO_PASSWORD", "DemoPass123!")


def call(path, method="GET", body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    request = Request(BASE + path, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            return json.load(response)
    except HTTPError as error:
        raise AssertionError(f"{method} {path}: HTTP {error.code}: {error.read().decode()}") from error


admin = call("/api/auth/login", "POST", {"email": "admin@example.test", "password": PASSWORD})
admin_token = admin["accessToken"]
programs = call("/api/programs")
yoga = next(p for p in programs if p["name"] == "Yoga Everyday")
email = f"smoke-{uuid.uuid4().hex[:10]}@example.test"
member = call("/api/auth/register", "POST", {"email": email, "password": PASSWORD, "displayName": "Smoke Member"})
token = member["accessToken"]
assert call(f"/api/programs/{yoga['id']}/enroll", "POST", {}, token)["enrolled"]
challenges = call("/api/challenges")
call(f"/api/challenges/{challenges[0]['id']}/enroll", "POST", {}, token)
session = call("/api/admin/sessions", "POST", {
    "programId": yoga["id"],
    "startsAt": (datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat(),
    "durationMinutes": 1,
    "youtubeVideoId": "",
}, admin_token)
session_id = session["id"]
assert any(s["slotId"] == session_id for s in call("/api/today", token=token)["sessions"])
assert any(s["slotId"] == session_id for s in call("/api/sessions", token=token))
asset = next(a for a in call("/api/admin/daily-assets", token=admin_token) if a["program_id"] == yoga["id"] and a["local_date"] == datetime.now(timezone(timedelta(hours=5, minutes=30))).date().isoformat())
if not asset["provider_asset_id"]:
    call(f"/api/admin/daily-assets/{asset['id']}/provider", "PUT", {"providerType": "YOUTUBE", "videoUrl": "GfvVuG5mXsA", "providerOwned": False, "recordingReady": True}, admin_token)
started = call("/api/attendance/start", "POST", {"sessionId": session_id, "requestId": str(uuid.uuid4())}, token)
playback_token = started["playbackToken"]
assert call(f"/api/v1/playback/{playback_token}", token=token)["playerType"] == "EMBEDDED"
time.sleep(43)
request_id = str(uuid.uuid4())
heartbeat = call("/api/attendance/heartbeat", "POST", {"sessionId": session_id, "requestId": request_id, "playbackToken": playback_token}, token)
duplicate = call("/api/attendance/heartbeat", "POST", {"sessionId": session_id, "requestId": request_id, "playbackToken": playback_token}, token)
assert heartbeat["qualified"] and duplicate["watched_seconds"] == heartbeat["watched_seconds"]
call("/api/attendance/complete", "POST", {"sessionId": session_id, "requestId": str(uuid.uuid4()), "playbackToken": playback_token}, token)
for _ in range(20):
    progress = call("/api/progress", token=token)
    messages = call("/api/admin/notifications", token=admin_token)
    if progress["streak"]["total_qualified_days"] == 1 and any(n["email"] == email and n["status"] == "DELIVERED" for n in messages):
        break
    time.sleep(2)
else:
    raise AssertionError("Outbox, streak, achievement, or fake WhatsApp delivery did not complete")
assert any(a["name"] == "First Session" for a in progress["achievements"])
assert progress["challenges"][0]["progress_days"] == 1
print("Primary flow passed: register → enroll → protected playback → qualified attendance → streak → challenge → achievement → fake WhatsApp")
