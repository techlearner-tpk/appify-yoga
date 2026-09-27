# Local development

Run `docker compose up --build`. Use `docker compose ps` and `docker compose logs -f backend-api background-worker scheduler` to inspect startup. Database migrations run automatically before the API starts. The scheduler creates 14 days of sessions after startup.

`make stop` retains PostgreSQL, Redis, RabbitMQ, MinIO, and Grafana volumes. `make clean` deletes them. To reset all local data, use `docker compose down -v` and start again. Environment overrides are in `.env.example`; copy it to `.env` before startup and never commit real passwords or tokens.

For manual API calls, log in at `POST /api/auth/login` with a demo account and send the returned short lived access token as `Authorization: Bearer ...`. The web app keeps access and refresh tokens in HTTP only cookies on the local Next.js server. `POST /api/auth/refresh` rotates a refresh token.

No cloud service, Meta account, YouTube API key, or payment account is needed for local development. A `YOUTUBE_VIDEO_ID` makes the class player embed the supplied stream. The fake provider needs no external credentials.

For an immediate YouTube test, sign in as admin and create a live session with a valid YouTube Live URL, a program, and an instructor. A member enrolled in that program can then join it. Changing `YOUTUBE_VIDEO_ID` in `.env` affects newly materialized sessions after restarting the API and scheduler; existing session rows retain their saved video. Instructor access is granted from the admin portal after the person registers as a member. They need to sign out and back in after promotion so their access token has the new role.
