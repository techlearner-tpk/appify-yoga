# Daily sessions and media operations

## Member flow

Each program has its own timezone and configured daily class times. Yoga Everyday starts with six times. The first configured time is the source class; later times use that day's shared media asset after it is ready. The member sees only a class time and join availability. Choosing a time can be changed; the first accepted join locks the member to one class per program and local date. A database unique constraint and row lock enforce this across concurrent devices.

The API checks active program enrollment, an unexpired trial or membership, enabled account, class window, daily participation, and media readiness. The default window opens 5 minutes before start and closes 5 minutes after the configured end. Access ends sooner if the local date has ended. Playback tokens are random, stored as hashes, bound to the authenticated member and slot, rechecked on each request, and extended for up to 15 minutes on use. Starting playback in another device revokes the older access session and records an audit event. Reconnecting to the same class is allowed during its window.

The page shows the embedded player inside the app. A later slot starts at its scheduled offset into the day's recording. Our player requests a server generated playback configuration only after joining. Attendance heartbeats require that playback token; retries with the same request ID are idempotent. A member's normal session and Today API responses contain no provider ID, URL, or source/replay classification.

## Admin setup

1. Sign in as admin and open **Admin portal → Class times**. Select a program, set its timezone and duration, and edit, reorder, enable, or remove slots. Choose one enabled source slot. The scheduler materializes the next 14 days; edits affect tomorrow onward. The daily limit is fixed at one joined class per program.
2. Under **Assets and cleanup**, find the program and local date. Attach the source video's YouTube Live URL or ID. A provided test video should be marked **archive ready** only when it can be used for all slots. For a new live source, leave that unchecked; the scheduler changes it to processing after the source ends and checks provider readiness. If no OAuth credentials are configured, an admin can mark a processed archive ready manually.
   An assigned instructor can press **Start source class** in Instructor view when the source window opens. YouTube broadcasting itself is started in the channel's streaming setup; this action advances the app's source state. The scheduler also advances the state when the scheduled start arrives.
3. Mark **This account owns the provider asset** only for videos in the configured channel that the app is permitted to delete. Enable program cleanup and set a delay and retry count. Supplied public test videos remain unowned by default and will not be deleted.
4. Refresh the asset list to inspect media, access expiry, cleanup state, attempts, and errors. A failed deletion can be retried from the admin portal. The outbox and RabbitMQ carry deletion work; retries use a delayed database schedule and can recover after a worker restart.

Use **Create a live session** for an immediate local test. This admin action is a manual class for today; it attaches the supplied link to today's asset and assumes it is ready. It is separate from the recurring six time schedule.

Before opening the generated class link, sign in as the member, open **Programs**, and enroll in the exact program selected by the admin. The demo member is initially enrolled only in Yoga Everyday; creating a new test program does not enroll existing members. An unenrolled member opening a valid class link sees **Join this program to attend**. A class that does not exist still returns **Session not found**.

## Provider and credentials

The first provider adapter is YouTube. Manual embedding needs an embeddable public or unlisted video URL or ID. Automatic readiness checks and deletion require the owning channel's OAuth client ID, client secret, and refresh token in `YOUTUBE_OAUTH_CLIENT_ID`, `YOUTUBE_OAUTH_CLIENT_SECRET`, and `YOUTUBE_OAUTH_REFRESH_TOKEN`. The refresh token must be authorized for the channel and a video management scope such as `youtube.force-ssl`; store it only in the ignored `.env` or a secret manager. No API key is used for channel deletion. The live test URL supplied for this project is `https://www.youtube.com/live/GfvVuG5mXsA` and must be treated as **unowned** unless it is your channel's video.

The domain uses `VideoProvider`; schedules, entitlement, attendance, playback sessions, and cleanup requests use that contract. A new provider adapter can replace the YouTube implementation without changing daily participation rules.

## Security limit

YouTube Unlisted embeds are not DRM. A technical user can inspect browser network traffic and discover the underlying video ID. The app prevents normal member API disclosure and casual link sharing, but cannot stop playback through a discovered YouTube URL or enforce server controlled seeking on a YouTube embed. The `start` and player control parameters are best effort. For stronger content protection, use a provider with signed playback URLs and DRM support.

## Operational checks

`GET /api/admin/daily-assets` shows readiness and deletion state. `GET /api/admin/outbox` shows publishing. Prometheus exposes join and asset cleanup counters. An expired asset revokes app playback and join links at local midnight. Owned assets with cleanup enabled are queued after the configured delay; permanent failures stay visible as `FAILED` for manual retry. No deletion is attempted for an unowned asset.
