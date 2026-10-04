#!/usr/bin/env sh
set -eu
API_URL=${API_URL:-http://localhost:8080}
WEB_URL=${WEB_URL:-http://localhost:3000}
SMOKE_ATTEMPTS=${SMOKE_ATTEMPTS:-60}
SMOKE_RETRY_DELAY=${SMOKE_RETRY_DELAY:-2}
SMOKE_REQUEST_TIMEOUT=${SMOKE_REQUEST_TIMEOUT:-5}
smoke_tmp=$(mktemp -d)
trap 'rm -rf "$smoke_tmp"' EXIT

wait_for() {
  label=$1
  url=$2
  pattern=$3
  attempt=1
  while [ "$attempt" -le "$SMOKE_ATTEMPTS" ]; do
    if curl -fsS --connect-timeout "$SMOKE_REQUEST_TIMEOUT" --max-time "$SMOKE_REQUEST_TIMEOUT" \
      "$url" -o "$smoke_tmp/body" 2>"$smoke_tmp/error" && \
      { [ -z "$pattern" ] || grep -q "$pattern" "$smoke_tmp/body"; }; then
      echo "$label ready"
      return 0
    fi
    if [ "$attempt" -eq 1 ]; then echo "Waiting for $label: $url"; fi
    if [ "$attempt" -lt "$SMOKE_ATTEMPTS" ]; then sleep "$SMOKE_RETRY_DELAY"; fi
    attempt=$((attempt + 1))
  done
  echo "$label did not become ready after $SMOKE_ATTEMPTS attempts: $url" >&2
  cat "$smoke_tmp/error" >&2
  if [ -n "$pattern" ]; then echo "Expected response to match: $pattern" >&2; fi
  return 1
}

wait_for 'API' "$API_URL/actuator/health/readiness" '"status"[[:space:]]*:[[:space:]]*"UP"'
wait_for 'Web app' "$WEB_URL/" ''
wait_for 'Seeded programs' "$API_URL/api/programs" 'Yoga Everyday'
echo 'Smoke checks passed'
