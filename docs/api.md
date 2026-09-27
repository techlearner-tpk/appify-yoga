# API

Swagger UI: `http://localhost:8080/swagger-ui`. JSON OpenAPI: `/v3/api-docs`. Auth endpoints are public; other endpoints require an access token unless noted.

| Area | Endpoints |
| --- | --- |
| Identity | `POST /api/auth/register`, `/login`, `/refresh`, `/logout` |
| User | `GET,PUT /api/me` |
| Programs | `GET /api/programs`, `/api/programs/enrolled`; `POST /api/programs/{id}/enroll` |
| Classes | `GET /api/sessions`, `/api/sessions/{id}`, `/api/today` |
| Attendance | `POST /api/attendance/start`, `/heartbeat`, `/complete`; `GET /api/attendance/{sessionId}` |
| Habits | `POST /api/habits/{id}/complete` |
| Progress | `GET /api/progress`, `/api/challenges`; `POST /api/challenges/{id}/enroll` |
| Admin | `GET /api/admin/dashboard`, `/users`, `/programs`, `/schedules`, `/sessions`, `/instructors`, `/attendance`, `/notifications`, `/fake-whatsapp`, `/audit`, `/outbox`, `/health`; `POST /api/admin/programs`, `/schedules`, `/sessions`, `/instructors` |
| Instructor | `GET /api/instructor/sessions` (assigned classes only; admins can see all) |
| Content | `GET /api/content`, `/api/content/{id}/image`; `POST /api/admin/content` with multipart title, body, optional image |

Heartbeat and complete requests require `sessionId` (UUID) and a unique `requestId`; retry the same request with the same ID. The configured attendance percentage is evaluated against the scheduled duration using server observed seconds, capped at 120 seconds per heartbeat gap.
