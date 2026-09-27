#!/usr/bin/env sh
set -eu
API_URL=${API_URL:-http://localhost:8080}
WEB_URL=${WEB_URL:-http://localhost:3000}
curl -fsS "$API_URL/actuator/health/readiness" | grep -q UP
curl -fsS "$WEB_URL/" >/dev/null
curl -fsS "$API_URL/api/programs" | grep -q 'Yoga Everyday'
echo 'Smoke checks passed'
