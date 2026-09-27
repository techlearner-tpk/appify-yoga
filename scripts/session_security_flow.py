#!/usr/bin/env python3
"""Exercise member authorization, one daily join, and token isolation."""
import json
import os
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
from urllib.error import HTTPError
from urllib.request import Request, urlopen

BASE = os.getenv("API_URL", "http://localhost:8080")
PASSWORD = os.getenv("DEMO_PASSWORD", "DemoPass123!")


def call(path, method="GET", body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = Request(BASE + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            return response.status, json.load(response)
    except HTTPError as error:
        return error.code, json.loads(error.read().decode() or "{}")


def require(status, actual):
    assert actual[0] == status, actual
    return actual[1]


admin = require(200, call("/api/auth/login", "POST", {"email": "admin@example.test", "password": PASSWORD}))["accessToken"]
yoga = next(p for p in require(200, call("/api/programs")) if p["name"] == "Yoga Everyday")
users = []
for index in range(2):
    email = f"security-{uuid.uuid4().hex[:12]}@example.test"
    token = require(200, call("/api/auth/register", "POST", {"email": email, "password": PASSWORD, "displayName": f"Security {index}"}))["accessToken"]
    require(200, call(f"/api/programs/{yoga['id']}/enroll", "POST", {}, token))
    users.append(token)

now = datetime.now(timezone.utc)
slots = []
for _ in range(2):
    created = require(200, call("/api/admin/sessions", "POST", {"programId": yoga["id"], "startsAt": (now - timedelta(seconds=3)).isoformat(), "durationMinutes": 10, "youtubeVideoId": ""}, admin))
    slots.append(created["id"])

asset = next(a for a in require(200, call("/api/admin/daily-assets", token=admin)) if a["program_id"] == yoga["id"] and a["local_date"] == datetime.now(timezone(timedelta(hours=5, minutes=30))).date().isoformat())
if not asset["provider_asset_id"]:
    require(200, call(f"/api/admin/daily-assets/{asset['id']}/provider", "PUT", {"providerType": "YOUTUBE", "videoUrl": "GfvVuG5mXsA", "providerOwned": False, "recordingReady": True}, admin))

member = users[0]
safe = require(200, call(f"/api/sessions/{slots[0]}", token=member))
assert "provider" not in json.dumps(safe).lower() and "youtube" not in json.dumps(safe).lower(), safe
require(200, call(f"/api/v1/session-slots/{slots[0]}/select", "POST", token=member))
require(200, call(f"/api/v1/session-slots/{slots[1]}/select", "POST", token=member))
link = require(200, call(f"/api/v1/join-links/{slots[1]}", "POST", token=member))["token"]
require(404, call(f"/api/v1/join-links/{link}", token=users[1]))
require(200, call(f"/api/v1/join-links/{link}", token=member))

with ThreadPoolExecutor(max_workers=2) as pool:
    outcomes = list(pool.map(lambda slot: call(f"/api/v1/session-slots/{slot}/join", "POST", token=member), slots))
assert sorted(status for status, _ in outcomes) == [200, 409], outcomes
winner = next(body for status, body in outcomes if status == 200)
loser_slot = slots[0] if winner["sessionSlotId"] == slots[1] else slots[1]
assert require(409, call(f"/api/v1/session-slots/{loser_slot}/join", "POST", token=member))["outcome"] == "ALREADY_ATTENDED_TODAY"
playback_token = winner["playbackToken"]
require(404, call(f"/api/v1/playback/{playback_token}", token=users[1]))
require(200, call(f"/api/v1/playback/{playback_token}", token=member))
require(403, call("/api/attendance/heartbeat", "POST", {"sessionId": winner["sessionSlotId"], "requestId": str(uuid.uuid4())}, member))
previous = require(200, call("/api/admin/sessions", "POST", {"programId": yoga["id"], "startsAt": (now - timedelta(days=1)).isoformat(), "durationMinutes": 10, "youtubeVideoId": ""}, admin))["id"]
assert require(409, call(f"/api/v1/session-slots/{previous}/join", "POST", token=users[1]))["outcome"] == "SESSION_EXPIRED"
print("Session security passed: safe member data, switch before join, atomic daily lock, member-bound links and playback, no tokenless attendance, previous-day denial")
