# Scaling

The API, worker, and scheduler are separate runtime modes. API and worker instances can be replicated after moving session state to shared infrastructure. PostgreSQL is authoritative; RabbitMQ is at least once. Attendance uses a row lock, unique request IDs, and capped heartbeat credit. At much larger class sizes, move heartbeat accumulation to Redis with periodic summary flushes, shard queue consumers, and benchmark the database writes before changing the contract.

The local configuration is sized for development, not 10,000 concurrent users. Run configurable k6 profiles with distinct test identities in dedicated performance infrastructure. Track P95 latency, database pool wait, RabbitMQ depth, worker lag, and PostgreSQL write throughput.
