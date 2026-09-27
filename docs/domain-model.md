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
