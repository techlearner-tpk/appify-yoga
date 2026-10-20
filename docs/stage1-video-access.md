# Stage 1: Zoom and YouTube access

## Run locally

1. Install Docker Desktop and Python 3; start Docker Desktop.
2. In this repository, copy `.env.example` to `.env` if you have not already configured it. Preserve existing secrets.
3. Run `docker compose up --build -d --wait --wait-timeout 360`.
4. Open http://localhost:3000 and sign in as `admin@example.test` with the configured `DEMO_PASSWORD` (local default `DemoPass123!`). Flyway applies V9 automatically. Existing programs and video assets retain their current YouTube behavior.

Zoom now uses the Meeting SDK inside the class page. Configure the backend SDK credentials and actual meeting passcode as described in [Zoom setup and free-account testing](zoom-in-app.md). Rupa still starts/hosts the meeting using the existing Zoom workflow. The previous Stage 1 external redirect has been removed. YouTube setup remains unchanged.

YouTube needs an existing embeddable live URL/video ID. OAuth credentials are not required for Stage 1 playback. Existing optional YouTube asset cleanup behavior remains separate.

## Admin setup

- **Admin portal → Create a live session**: select the program, instructor, streaming provider and enabled status. Enter a Zoom meeting URL or a YouTube Live URL/video ID. Leave start time blank for immediate local testing, or select a future start. The session gets its own provider override.
- **Streaming configuration → Program default**: set the provider used by sessions without an override, including future schedule slots. A program default cannot change while an inherited session is running.
- **Streaming configuration → Session override**: choose a session, configure its provider, and save before its start time. A disabled override blocks access even if the program default is enabled. Changes revoke existing access grants; reminder links remain bound to the same user/session and resolve the current configuration.
- Existing sessions without a program default/override continue using their daily YouTube asset. The existing daily asset admin tools remain available for that workflow.

Provider configuration endpoints are under `/api/admin/programs/{id}/stream` and `/api/admin/sessions/{id}/stream` (GET/PUT), require ADMIN, and use `Cache-Control: no-store`. Members and instructors cannot read them. Configuration is excluded from member session/schedule DTOs. Only admin edit forms fetch it; ordinary page source contains no meeting configuration.

## Member flow and API

Dashboard/reminder → `/j/{opaqueToken}` → sign in if needed → class page → **Enter class** → `POST /api/v1/session-slots/{id}/join`.

The existing single join endpoint checks active account, membership, enrollment, session window, daily participation, and enabled/ready stream configuration. It records the first join and provider internally, then issues a member-bound, session-bound access grant. Members never choose or see a provider label.

- **Zoom**: playback resolution returns only `/meeting/{opaqueAccessToken}` with type `ZOOM_SDK`. An isolated app-owned iframe loads Zoom's pinned web client. The frame calls `POST /api/v1/playback/{token}/sdk` through the authenticated proxy; the backend rechecks ownership, active membership, session/window and provider before signing a participant-only JWT for the configured meeting. Meeting credentials are delivered only to this authorized SDK response, never to generic session APIs. The signing secret stays server-side. There is no external meeting redirect or native Zoom launch. The application grant remains valid for two minutes for initial connection; Zoom's JWT has its required minimum 30-minute lifetime. The connected frame remains mounted until Finish class or the session end. To reconnect after the app grant expires, finish and re-enter the class for a new grant.

- **YouTube**: playback resolution returns the embed configuration only after authorization. The class page embeds the video with the existing origin-only referrer policy and fullscreen controls. Existing attendance heartbeat logic continues.

Reminder tokens contain 256 bits of randomness, have only a SHA-256 hash in the token table, and are user/session bound. They remain reusable until the earlier of session close or daily access expiry. An authenticated different user cannot resolve one. Reminder bodies use `WEB_ORIGIN/j/{opaqueToken}` and contain no provider URL or credentials. Token strings must be redacted by any external access logging infrastructure.

### Security limits

This is application-layer access control and masking, **not DRM**. An authorized browser must eventually receive the YouTube embed ID or Zoom meeting credentials/signature; browser developer tools and external providers can reveal them. Stage 1 cannot prevent an authorized user from copying a destination or a provider from displaying branding/share controls. Use Zoom waiting-room/host admission settings and suitable YouTube visibility/embedding settings in the owning accounts.

## Attendance

Every successful authorized join records `joined_at`, user, session, and provider internally. Zoom Stage 1 means **entry via the application's authorized join path**; it does not prove Zoom participation, watched minutes or qualified attendance. Zoom SDK grants cannot call duration-credit endpoints, and no Zoom duration/streak qualification event is fabricated. Existing YouTube heartbeat-based qualification, streak, challenge and achievement processing remains unchanged. Zoom duration reconciliation is a future integration.

## Encryption

New stream configuration (including meeting URL, passcode and notes) is encrypted with AES-256-GCM, with a new random nonce per save. Normal logs and audit entries contain only metadata; configuration/experience objects redact their diagnostic string representations. The separate stream table prevents accidental inclusion through existing session DTOs.

Set a stable `STREAM_ENCRYPTION_KEY` in production to a base64 encoded 32-byte secret, generated using `openssl rand -base64 32`. Keep it in secret management and back it up with the database; losing or changing it makes existing configuration unreadable. Local Compose runs without additional configuration by deriving a purpose-specific key from `JWT_SECRET`. With that fallback, changing `JWT_SECRET` also requires re-encrypting existing stream configuration. Do not change either encryption key in place without a migration. Legacy YouTube daily asset IDs remain in their existing media table; they are not meeting credentials.

## Verification

- Backend unit/security tests: `make test` (also runs frontend unit tests).
- Full local Compose/API flows: `make integration-test`.
- Provider/API/security flow alone: `python3 scripts/provider_security_flow.py` after Compose is healthy with SDK credentials configured (synthetic test-only credentials are sufficient for automated checks; see the Zoom guide). It creates isolated test users/programs and uses the Compose database for expiry/disabled-account fixtures. It does not contact Zoom/YouTube.
- Browser flows: `make e2e-test`. The embedded Zoom test uses real application authentication, policy and backend signature issuance, then stubs only the external vendor SDK. It verifies in-app rendering, no new window, no external join link, and leaving/rejoining. It does not establish a real Zoom media connection. The Zoom guide documents the manual live-meeting check. Tests use one worker and block service workers for reliable network interception. YouTube checks verify the embed request, referrer, player and fullscreen controls.

- CI runs backend/frontend checks, image security scanning, original attendance/security/content flows, provider security checks, and all browser flows.

## Minimal extension point

`SessionJoinProvider.prepareJoin` prepares an embedded experience after shared authorization. `ZoomSessionJoinProvider` and `YouTubeSessionJoinProvider` are registered through Spring's implementation list. A later provider adds an implementation and an admin configuration/validation choice (and extends the database's supported-type constraint); membership, daily participation, reminders, scheduling, habit tracking and the member dashboard do not need provider-specific changes. Existing media lifecycle integrations are separate from this Stage 1 join interface.

Out of scope: Zoom hosting from the app, OAuth/API/webhooks/registration, Zoom duration reconciliation, new hosting providers, DRM, automatic stream creation or failover, and new services.
