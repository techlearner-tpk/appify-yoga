# Observability

Spring Boot Actuator exposes liveness, readiness, and Prometheus metrics. Prometheus scrapes the API every 15 seconds; Grafana has a preconfigured Prometheus datasource. Logs are JSON. The API attaches a request ID and user ID to log context, and attendance events carry a correlation ID in the outbox.

The admin dashboard shows users, attendance, pending queue depth, and failed fake messages. The admin API also exposes recent outbox entries and notifications. Add production alerts for failed messages, outbox lag, queue depth, 5xx rate, latency, JVM memory, and database pool saturation.
