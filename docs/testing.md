# Testing

- Backend unit and ArchUnit rules: `cd backend && mvn test` with Java 21, or `make test` with Docker.
- Frontend unit and production build: `cd frontend && npm ci && npm test && npm run build`.
- Compose smoke and API flows: `make integration-test`. One flow waits about one minute for real attendance time. A second checks slot switching, simultaneous joins, member bound join and playback tokens, tokenless attendance rejection, past date access, and member response metadata.
- Browser flow: `make e2e-test`. It builds a Playwright container and checks role-aware navigation, admin instructor onboarding, neutral six-slot choices, and the supplied video through the in-app member player; host Node and browser binaries are not required. Admin form checks verify that program creation, instructor onboarding, live session creation, and article publishing reset their fields after saving without showing an error.
- Configurable load: `VUS=100 EMAIL_PREFIX=load make load-test`. The prefix makes the script register separate user accounts; use suitable test infrastructure for higher concurrency profiles.

The current tests cover attendance rule boundaries, scheduled offset, package direction, UI states, concurrent daily join, cross-member access, and the full local attendance flow. The Compose scripts check readiness, registration through delivered fake WhatsApp, and MinIO image storage. Actual YouTube processing and deletion require the owning channel's OAuth credentials and are not exercised by the local fake-data suite. A measured production-scale P50/P95/P99 run remains before launch. k6 reports latency percentiles, throughput, and errors.

Local smoke result on 2026-09-27: 5 k6 virtual users for 20 seconds, 224 requests, 0 errors, P95 122 ms. This verifies the script and local path; it is not a 100-user capacity measurement.
