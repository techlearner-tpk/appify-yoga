# Domain model

Flyway migrations under `backend/src/main/resources/db/migration` define the source of truth. UUIDs identify users, programs, series, sessions, attendance, challenges, achievements, notifications, referrals, and events. Foreign keys, unique constraints, and version columns protect key invariants.

```mermaid
erDiagram
  APP_USER ||--o{ PROGRAM_ENROLLMENT : joins
  PROGRAM ||--o{ PROGRAM_ENROLLMENT : contains
  PROGRAM ||--o{ CLASS_SERIES : schedules
  CLASS_SERIES ||--o{ SESSION : materializes
  SESSION ||--o{ ATTENDANCE : tracks
  APP_USER ||--o{ ATTENDANCE : attends
  APP_USER ||--|| STREAK : owns
  APP_USER ||--o{ QUALIFIED_DAY : earns
  APP_USER ||--o{ CHALLENGE_ENROLLMENT : enters
  CHALLENGE ||--o{ CHALLENGE_ENROLLMENT : has
  APP_USER ||--o{ USER_ACHIEVEMENT : unlocks
  ACHIEVEMENT ||--o{ USER_ACHIEVEMENT : awards
  APP_USER ||--o{ NOTIFICATION : receives
```

An attendance row is unique per user and session. A heartbeat request ID is globally unique. A qualified day is unique per user and local date, so two classes on one day count once toward a streak. Achievements and enrollments also have unique user pairs. Event IDs are unique per consumer.

`program_session_policy` stores timezone, source slot, access window, and cleanup policy. `daily_session_asset` is unique per program and local date; all six slots reference one asset. `membership_entitlement` and active enrollment participate in authorization. `user_daily_participation` is unique per user, program, and local date; selecting a slot changes only `selected_session_slot_id`, while the first accepted join fills `joined_session_slot_id` under a row lock. `playback_session` and `session_join_link` contain only token hashes, scope, and expiry. Provider identifiers reside in admin asset data, not normal member session resources.
