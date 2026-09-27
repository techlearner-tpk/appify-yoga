#!/usr/bin/env python3
"""Check the MinIO storage adapter through the authenticated API."""
import base64
import json
import os
import uuid
from urllib.request import Request, urlopen

base = os.getenv("API_URL", "http://localhost:8080")
password = os.getenv("DEMO_PASSWORD", "DemoPass123!")
login = Request(base + "/api/auth/login", data=json.dumps({"email": "admin@example.test", "password": password}).encode(), headers={"Content-Type": "application/json"}, method="POST")
with urlopen(login, timeout=15) as response:
    token = json.load(response)["accessToken"]
boundary = "appify" + uuid.uuid4().hex
png = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL/nwAAAABJRU5ErkJggg==")
body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nStorage smoke\r\n"
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"body\"\r\n\r\nMinIO content is available.\r\n"
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"image\"; filename=\"smoke.png\"\r\nContent-Type: image/png\r\n\r\n").encode() + png + f"\r\n--{boundary}--\r\n".encode()
headers = {"Authorization": "Bearer " + token, "Content-Type": "multipart/form-data; boundary=" + boundary}
request = Request(base + "/api/admin/content", data=body, headers=headers, method="POST")
with urlopen(request, timeout=30) as response:
    article_id = json.load(response)["id"]
request = Request(base + f"/api/content/{article_id}/image", headers={"Authorization": "Bearer " + token})
with urlopen(request, timeout=30) as response:
    assert response.headers.get_content_type() == "image/png"
    assert response.read() == png
print("Content flow passed: admin upload → MinIO → authenticated image read")
