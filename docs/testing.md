# Testing

- Backend unit and ArchUnit rules: `cd backend && mvn test` with Java 21, or `make test` with Docker.
- Frontend unit and production build: `cd frontend && npm ci && npm test && npm run build`.
- Compose smoke and API flows: `make integration-test`. One flow waits about one minute for real attendance time. A second checks slot switching, simultaneous joins, member bound join and playback tokens, tokenless attendance rejection, past date access, and member response metadata.
- Readiness retry regression: `python3 scripts/smoke_test.py`. A local HTTP fixture checks connection resets, temporary HTTP errors, delayed demo data, and bounded failure when readiness never arrives. `scripts/smoke.sh` checks API readiness, the web page, and seeded programs with up to 60 attempts per check; `SMOKE_ATTEMPTS`, `SMOKE_RETRY_DELAY`, and `SMOKE_REQUEST_TIMEOUT` override its defaults.

- Browser flow: `make e2e-test`. It builds a Playwright container and checks role-aware navigation, admin instructor onboarding, neutral six-slot choices, and the supplied video through the in-app member player; host Node and browser binaries are not required. Player checks verify the origin-only YouTube referrer, fullscreen entry and exit, and finishing a class from fullscreen. Admin form checks verify that program creation, instructor onboarding, live session creation, and article publishing reset their fields after saving without showing an error.
- Configurable load: `VUS=100 EMAIL_PREFIX=load make load-test`. The prefix makes the script register separate user accounts; use suitable test infrastructure for higher concurrency profiles.

CI waits up to six minutes for Compose health checks before testing and prints service status and recent logs on failure. The Trivy scanner uses the commit pinned to the verified v0.36.0 release. Vulnerability findings at HIGH or CRITICAL severity still fail the scan. Frontend health checks use IPv4 loopback to match the server binding. Backend security version overrides in `backend/pom.xml` must remain until the Spring Boot baseline includes the same fixes.

The current tests cover attendance rule boundaries, scheduled offset, package direction, UI states, concurrent daily join, cross-member access, and the full local attendance flow. The Compose scripts check readiness, registration through delivered fake WhatsApp, and MinIO image storage. Actual YouTube processing and deletion require the owning channel's OAuth credentials and are not exercised by the local fake-data suite. A measured production-scale P50/P95/P99 run remains before launch. k6 reports latency percentiles, throughput, and errors.

Local smoke result on 2026-09-27: 5 k6 virtual users for 20 seconds, 224 requests, 0 errors, P95 122 ms. This verifies the script and local path; it is not a 100-user capacity measurement.
