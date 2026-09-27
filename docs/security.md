# Security

Passwords use BCrypt. Access tokens expire after 15 minutes. Refresh tokens are random, stored only as hashes, expire after 30 days, and rotate on refresh. The web tier stores tokens in HTTP only SameSite cookies. Admin and instructor APIs require RBAC. Validation, prepared SQL parameters, a restrictive CORS origin, and a CSP on API responses reduce common risks.

Local passwords are intentionally published for demos. Change them and `JWT_SECRET` before exposing any service to other devices. Redis enforces fixed window limits on login, registration, attendance, and admin mutations. TLS, production CSRF review, a secrets manager, dependency and container scans, and penetration testing are required before a paid launch. Never log credentials or tokens. The fake WhatsApp debug screen contains message content and must stay admin only.
