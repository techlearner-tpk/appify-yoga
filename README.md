# Appify Wellness

A local first wellness platform with a Next.js PWA, Spring Boot API, PostgreSQL, RabbitMQ worker, scheduler, and a simulated WhatsApp inbox.

## Start

Start Docker Desktop (or a compatible Docker Engine), then run:

```sh
git clone https://github.com/techlearner-tpk/appify-yoga.git
cd appify-yoga
cp .env.example .env  # set local passwords and optional video credentials
docker compose up --build -d
docker compose ps
```

The first build downloads images and Maven/npm packages. After the services become healthy, open http://localhost:3000. Use `docker compose logs -f backend-api frontend` if startup takes longer than expected. Git and Docker are needed for these steps; the `make` commands below also require Make.

| Service | URL | Local credentials |
| --- | --- | --- |
| Web | http://localhost:3000 | `member@example.test` / `DemoPass123!` |
| API | http://localhost:8080 | Bearer token from login |
| Swagger | http://localhost:8080/swagger-ui | None |
| Grafana | http://localhost:3001 | `admin` / `local-grafana-change-me` |
| Prometheus | http://localhost:9090 | None |
| RabbitMQ | http://localhost:15672 | `wellness` / `local-rabbit-change-me` |
| MailHog | http://localhost:8025 | None |
| MinIO | http://localhost:9001 | `wellness` / `local-minio-change-me` |

Other demo accounts: `instructor@example.test` and `admin@example.test`, both using `DemoPass123!`. Change all credentials for any shared environment. Copy `.env.example` to `.env` to configure passwords, a YouTube video ID, or the attendance threshold. `.env` is ignored by Git.

The demo user is already enrolled in Yoga Everyday. The API creates a short demo class on startup. The scheduler materializes six Yoga times per day, 14 days ahead. Set `YOUTUBE_VIDEO_ID` for local demo playback, or attach a daily video in **Admin portal → Assets and cleanup**. To test immediately, create a class from the admin portal, then open its app link as an enrolled member. The member sees a normal class and can join only one class per program and local day. See [daily sessions](docs/daily-sessions.md) for schedule, playback, and cleanup operations.

### Instructor onboarding

Instructors first register through the normal member sign-up. An admin then opens **Admin portal → Onboard an instructor** and enters the registered email. The instructor signs out and back in to receive the new role, and the **Instructor view** link appears. The admin can assign that instructor when creating a live session. Public sign-up never grants staff access. The built-in `instructor@example.test` account is already onboarded for local testing; no configuration change is needed for it.

## Commands

```sh
make start             # compose up --build -d
make stop              # retain data
make test              # backend and frontend unit tests
make integration-test  # full stack smoke checks
make e2e-test          # Playwright browser flow
make load-test         # configurable k6 profile
make clean             # delete local volumes and their data
```

For Windows PowerShell, use `docker compose up --build -d`, `docker compose down`, `docker compose down -v`, and `docker compose exec` for service commands. See [local development](docs/local-development.md) for details.

## Account details needed later

- **YouTube Live:** an embeddable video ID or URL for manual playback. For automatic archive readiness and deletion, provide an OAuth client ID, client secret, refresh token authorized for the owning channel, and confirmation that the app may delete owned videos. Do not send secrets in chat or commit them.
- **WhatsApp:** no account is needed for local simulation. For real delivery later, provide a Meta Business portfolio, WhatsApp Business Account ID, phone number ID, approved display number, access token or system user setup, webhook verification secret, and approved message templates. Keep all secrets outside Git.
- **Email:** no account is needed locally; MailHog captures mail. A production email provider and verified sending domain will be needed later.
- **Cloud:** no account is needed for this phase.

## Current scope

The repository implements the primary local demo path and the main infrastructure shape. The full production brief also calls for further hardening before paid launch: broader admin CRUD, richer reminder rules, full integration and resilience suites, and a 100 user measured load run. See [architecture](docs/architecture.md) and [testing](docs/testing.md).

## Stage 1 video access

Admin can configure Zoom or YouTube per program/session. See [setup, security limits, attendance behavior, and testing](docs/stage1-video-access.md).
