# Testing

- Backend unit and ArchUnit rules: `cd backend && mvn test` with Java 21, or `make test` with Docker.
- Frontend unit and production build: `cd frontend && npm ci && npm test && npm run build`.
- Compose smoke and primary API flow: `make integration-test`. The flow waits about one minute for real attendance time.
- Browser flow: `make e2e-test`. It builds a Playwright container; host Node and browser binaries are not required.
- Configurable load: `VUS=100 make load-test`. Use separate user accounts and test infrastructure for higher concurrency profiles.

The current tests cover attendance rule boundaries, package direction, UI states, and a demo browser navigation. The Compose scripts check readiness, registration through delivered fake WhatsApp, and MinIO image storage. The requested broader Testcontainers, messaging restart, concurrency, resilience, and measured P50/P95/P99 test suite remains to be added before production use. k6 itself reports latency percentiles, throughput, and errors.

Local smoke result on 2026-09-27: 5 k6 virtual users for 20 seconds, 224 requests, 0 errors, P95 122 ms. This verifies the script and local path; it is not a 100-user capacity measurement.
