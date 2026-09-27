# Appify Wellness

A local first wellness platform with a Next.js PWA, Spring Boot API, PostgreSQL, RabbitMQ worker, scheduler, and a simulated WhatsApp inbox.

## Start

Start Docker Desktop (or a compatible Docker Engine), then run:

```sh
git clone https://github.com/techlearner-tpk/appify-yoga.git
cd appify-yoga
cp .env.example .env  # optional: set passwords and YOUTUBE_VIDEO_ID before startup
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

The demo user is already enrolled in Yoga Everyday. The API creates a short live demo session on startup. Regular Yoga slots are materialized 14 days ahead by the scheduler. A real YouTube Live ID can be set through `YOUTUBE_VIDEO_ID` for newly created demo and scheduled sessions; the platform does not host video. To test a stream immediately, sign in as admin and use **Create a live session** with a YouTube Live URL. Fake WhatsApp messages appear in the admin portal after reminders and qualified attendance. A fresh demo session is created each day.

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

- **YouTube Live:** public or unlisted embeddable live video ID or URL, and confirmation that embedding is permitted. No YouTube API credential is needed for manual configuration.
- **WhatsApp:** no account is needed for local simulation. For real delivery later, provide a Meta Business portfolio, WhatsApp Business Account ID, phone number ID, approved display number, access token or system user setup, webhook verification secret, and approved message templates. Keep all secrets outside Git.
- **Email:** no account is needed locally; MailHog captures mail. A production email provider and verified sending domain will be needed later.
- **Cloud:** no account is needed for this phase.

## Current scope

The repository implements the primary local demo path and the main infrastructure shape. The full production brief also calls for further hardening before paid launch: broader admin CRUD, richer reminder rules, full integration and resilience suites, and a 100 user measured load run. See [architecture](docs/architecture.md) and [testing](docs/testing.md).
