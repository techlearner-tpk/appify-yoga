# Zoom classes inside the application

Members now join on `/live/{sessionId}` and remain there. The class frame runs Zoom Meeting SDK **6.5.0 client view**, loaded from Zoom's official CDN. It has its own document to isolate Zoom's React 18 and global styles from the application's React 19. Client view is Zoom's recommended option for mobile/tablet browsers. The app retains fullscreen and Finish class controls. Zoom branding and meeting controls remain visible.

## 1. Create credentials using your own free account

1. Sign in to [Zoom App Marketplace](https://marketplace.zoom.us/) using the Zoom account that will **host your test meeting**.
2. Open Develop / Build App and create a **General App**. Follow [Zoom's current credential setup guide](https://developers.zoom.us/docs/meeting-sdk/get-credentials/).
3. Under **Features → Embed**, enable **Meeting SDK**. Follow the app's local-test/activation prompts for your own account.
4. Copy the app's development **Client ID** and **Client Secret** from Basic Information. Do not use a Server-to-Server OAuth app or a Video SDK app; those are different credentials.
5. If Zoom asks for an allowed application domain, configure the testing domain it accepts; use `localhost` for local testing where supported, and your HTTPS domain for deployment. Domain restrictions and browser permissions must match the URL you actually open.

Your personal Basic/free account can be used for your own hosted meetings; its normal limits still apply (typically 40 minutes and up to 100 participants). See [Zoom's Basic plan](https://www.zoom.com/en/small-business/meetings/). The SDK does not remove plan limits.

This implementation joins as a **guest participant**, never as host. Rupa/you start the meeting in Zoom. No host ZAK, OAuth, webhooks or meeting-creation API is implemented. An app/meeting owned by different Zoom accounts may require Zoom review and additional ZAK/OBF authorization; [Zoom's account restrictions](https://developers.zoom.us/docs/meeting-sdk/get-credentials/) apply. For production, create credentials in Rupa's hosting account or plan that additional integration separately.

## 2. Configure your local backend

Add to the repository's ignored `.env` (keep existing database/encryption secrets):

```dotenv
ZOOM_MEETING_SDK_CLIENT_ID=your-development-client-id
ZOOM_MEETING_SDK_CLIENT_SECRET=your-development-client-secret
```

Never paste the secret in chat, commit it, put it in a `NEXT_PUBLIC_` variable, or enter it in a member form. It belongs only in backend secret configuration.

```bash
docker compose up --build -d --wait --wait-timeout 360
```

No new service or database migration is needed. The existing encrypted stream configuration stores the meeting details. If credentials are absent, Zoom joins fail with a clear 503 configuration message; YouTube continues to work. No external redirect fallback is used.

## 3. Configure and start a test class

1. In your Zoom account, schedule a meeting. For the first test, use a normal meeting with a passcode and waiting room; avoid requiring participant Zoom authentication or registration. Those policies may require further identity integration.
2. Copy its invitation URL and the **actual passcode** shown in meeting settings. The `pwd=` value in the invitation is encoded and is **not** the SDK passcode.
3. Open [the local admin portal](http://localhost:3000/admin), sign in as `admin@example.test` with your configured `DEMO_PASSWORD` (local default `DemoPass123!`).
4. Create a separate test program to avoid the existing daily participation lock. Create a live session for that program, choose **Zoom**, paste the meeting URL and actual passcode. The optional meeting ID must match the URL; spaces/dashes are accepted. Leave start time blank for **Start live session now**. Save future overrides before the session starts.
5. Start the meeting as host in Zoom using **the same account that owns the SDK app**. Keep that meeting running.
6. In an incognito/private browser, register/sign in as a member with active membership, enroll in the test program, open the created class and click **Enter class**. No need to wait for normal schedule slots.
7. Admit the participant from the Zoom host waiting room. Grant browser microphone/camera permission if needed. Use headphones or separate devices to avoid feedback while hosting and attending.
8. Verify audio/video inside the class frame, no new tab/native Zoom launch, fullscreen, Finish class, then re-enter. App attendance records entry only; SDK integration does not add duration-based qualification or streak credit.

Use a supported recent browser. `http://localhost` can use camera/microphone as a local secure context; a phone accessing a LAN IP over plain HTTP generally needs an HTTPS testing URL instead. Corporate firewalls/ad blockers must allow Zoom's CDN, media and signaling endpoints. If embedding/media fails, use Zoom's [browser support](https://developers.zoom.us/docs/meeting-sdk/web/browser-support/) and [iframe guidance](https://developers.zoom.us/docs/meeting-sdk/web/) to check the environment.

## Security and lifetime

The existing `/join` endpoint remains the entry point. The authenticated `POST /api/v1/playback/{opaqueToken}/sdk` rechecks the playback grant and membership/session entitlement and signs only the **configured meeting**, `role=0`, and the server-side member name. It accepts no meeting number or host role from the caller. Issuance is serialized with stream configuration changes. Responses are `no-store`; diagnostic strings redact credentials.

Only after those checks does the browser receive meeting number, actual passcode, name and participant signature. This is application-layer masking, **not DRM**. The authorized browser can inspect those values. No signing secret, raw invitation URL, host token, or provider configuration is returned in normal session/schedule/reminder APIs.

App grants expire after two minutes and cannot be prolonged by Zoom heartbeats. [Zoom requires SDK JWT lifetime of at least 1800 seconds](https://developers.zoom.us/docs/meeting-sdk/auth/); the issuer uses that minimum, with a 30-second issue-time skew. A signature already delivered to a browser cannot be revoked locally at Zoom. App membership changes block **new** issuance; they cannot forcibly remove an already-connected Zoom participant. Hosts can remove participants/end meetings using Zoom controls. After app-grant expiry, use Finish class and re-enter to reconnect; an existing connection is not cut off merely because that initial grant expired.

## Automated verification

Backend tests verify signature HMAC, configured meeting, participant role, lifetime, configuration errors, missing/mismatched passcode/meeting, expiry and entitlement guards. The provider API script checks that only the owning user can obtain SDK credentials and that member APIs, stored ciphertext and normal logs exclude raw configuration. Browser tests stub Zoom's external SDK only, while using real application authentication and backend signing. **These are not a real-media test**; follow the manual steps above with your credentials to verify actual Zoom admission/audio/video.

For automated checks only, a disposable local stack can use synthetic credentials:

```bash
export ZOOM_MEETING_SDK_CLIENT_ID=local-test-client-not-real
export ZOOM_MEETING_SDK_CLIENT_SECRET=local-test-secret-not-real-do-not-use-in-production
docker compose up --build -d --wait --wait-timeout 360
python3 scripts/provider_security_flow.py
make e2e-test
```

Those values cannot join real Zoom meetings. Unset both exports before recreating services with your real `.env` credentials. CI supplies its own clearly marked synthetic fixture. Never deploy synthetic credentials as production configuration.

### Troubleshooting

- **In-app meetings are not configured**: set both backend SDK variables and recreate backend services.
- **Invalid signature/app**: confirm Meeting SDK is enabled, use a matching Client ID/Secret pair and correct development/production credentials, and check server clock. Verify the meeting belongs to the same Zoom account.
- **Incorrect passcode**: enter the actual meeting passcode separately. Existing configurations saved before this change may need that field updated; create a new test session if the old one has already started.
- **Waiting for host/admission**: start the meeting in Zoom and admit the participant. SDK does not start the host's meeting.
- **Player cannot load**: allow `source.zoom.us`; the app downloads the pinned SDK and media assets on demand. No npm React downgrade is required.
- **Unable to reconnect**: Finish class, then Enter class to obtain a fresh grant while the session window and membership remain valid.
- **Session is not visible / create button seems inactive**: explicitly choose the same program the member enrolled in, complete the enabled provider's URL/video field, and check the validation/error message beside the create button. Instructor assignment is optional. A blank start publishes now; a future start uses **Schedule session** and appears in Today’s classes on that date. An already-open member dashboard refreshes every 30 seconds and when brought back into focus. Successful creation shows the program name and a clickable member class link. Publishing a session does not start its Zoom host meeting.

### Correct a passcode after class starts

In **Admin portal → Streaming configuration**, choose **Session override** and the session.
Use **Correct Zoom passcode** with the actual passcode from Zoom's meeting details (Personal Room for a personal meeting ID).
This ADMIN-only operation saves an encrypted session override and revokes outstanding app playback grants.
It changes only the saved passcode; meeting URL, provider, schedule, enrollment and daily participation stay intact.
Ended or cancelled sessions cannot be corrected. Provider/meeting changes still require a future session.
Members with the previous player open should choose **Finish class**, then **Enter class** to obtain a fresh authorization for the same session.
The invitation URL's encoded `pwd` value is not the Meeting SDK passcode. Do not paste passcodes into chat, logs or source control.
If Zoom has a waiting room, the host must start that same meeting and admit the member.

### Diagnose a participant stuck waiting for a running host

Confirm the meeting ID from the **running host window**, and the host's name marked **(Host)** in Participants.
Compare joining through the app in Chrome/Edge with joining the same meeting through a normal Zoom client on a second device.
This distinguishes a Zoom meeting-side problem from the SDK path; disabling Waiting Room alone does not prove that a host connection exists.

The protected player reports these connection stages from Zoom's `onJoinSpeed` / `onMeetingStatus` events:
waiting for host, waiting for admission, connected, reconnecting and disconnected.
SDK failures show only a bounded numeric connection code. Raw vendor errors, meeting details, signatures and passcodes are never serialized into these diagnostics or console logs.
A waiting screen is evidence of reaching that stage only; it does **not** verify live audio/video or a completed join.
