# Cloud migration

No cloud deployment is included. The runtime maps to managed container hosting for Next.js, API, worker, and scheduler; managed PostgreSQL; managed Redis; SQS/Service Bus or managed RabbitMQ; S3-compatible storage; a real email service; and Meta WhatsApp Cloud API. Keep Flyway migrations and event contracts unchanged. Add provider adapters for storage, queue, email, and WhatsApp before migration. Validate delivery, dead letter handling, token signing, TLS, and backup restore in the target environment.
