# Instagram/Facebook Chatbot SaaS Platform

A multi-tenant Spring Boot SaaS platform for managing Instagram and Facebook chatbot integrations.

## Features
- Multi-tenant business registration, authenticated via per-business API keys
- Meta OAuth 2.0 integration (signed, expiring OAuth `state`; access tokens encrypted at rest)
- Webhook handling for Instagram/Facebook messages (HMAC-signature verified)
- Configurable chatbot flows with validation
- Product catalog, orders, and a state-machine chatbot engine
- Conversation tracking and data collection
- Admin-only endpoints for onboarding, suspending, and reactivating tenants (manual billing)

## Requirements
- Java 17+
- PostgreSQL 14+
- Maven 3.8+
- Docker (for the production deployment path)

## Local development setup
1. Copy `.env.example` to `.env` and fill in values (at minimum set your own
   `ENCRYPTION_SECRET_KEY`, `OAUTH_STATE_SECRET`, `ADMIN_PASSWORD` - the defaults are fine for
   local dev only)
2. Create PostgreSQL database: `createdb chatbot_saas`
3. Run: `mvn spring-boot:run`

## API Documentation
See [API.md](API.md) for full endpoint documentation, including authentication requirements.

## Authentication model

- **Business-scoped endpoints** (`/api/businesses/**`, `/api/flows`, `/api/businesses/{id}/products`,
  `/api/customers`, `/api/conversations`, etc.) require an `X-API-Key` header. A business receives
  its API key exactly once, in the response to `POST /api/businesses/register` (or from an admin
  via the rotate-key endpoint below) - it cannot be retrieved again, only rotated.
- **Admin endpoints** (`/api/admin/**`) are protected by HTTP Basic auth using the
  `ADMIN_USERNAME` / `ADMIN_PASSWORD` environment variables. There is no admin UI yet; these are
  meant to be called directly (e.g. via `curl`) as part of onboarding/support.
- **The Meta webhook** (`/webhook`) and the **OAuth callback** (`/api/auth/meta/callback`) are
  intentionally public (Meta calls them directly) - they are protected by HMAC signature
  verification and a signed, expiring OAuth `state` parameter, respectively, instead of an API key.

## Admin runbook (manual billing)

Since billing is handled outside the app, use the admin endpoints to control tenant access:

```bash
# List all businesses
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" https://your-host/api/admin/businesses

# Suspend a business (e.g. non-payment) - its API key stops working and its webhook
# messages are silently dropped until reactivated
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -X POST https://your-host/api/admin/businesses/{id}/suspend

# Reactivate once paid
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -X POST https://your-host/api/admin/businesses/{id}/activate

# Rotate a business's API key (e.g. if it leaked) - returns the new key once
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -X POST https://your-host/api/admin/businesses/{id}/rotate-api-key
```

## Production deployment

The app ships with a multi-stage `Dockerfile` and a `docker-compose.yml` that runs the app
alongside PostgreSQL, so it can be deployed on any host that runs Docker (a VPS, Render, Railway,
Fly.io, AWS, etc.) without cloud lock-in.

1. Copy `.env.example` to `.env` and set **unique, non-default** values for at least:
   `ENCRYPTION_SECRET_KEY`, `WEBHOOK_VERIFY_TOKEN`, `OAUTH_STATE_SECRET`, `ADMIN_USERNAME`,
   `ADMIN_PASSWORD`, `CORS_ALLOWED_ORIGINS` (a specific origin, not `*`), and your real
   `META_APP_ID`/`META_APP_SECRET`/`BASE_URL`.
2. Set `SPRING_PROFILES_ACTIVE=prod` in `.env`. With this profile active, the app will **refuse
   to start** if any of the settings above were left at their insecure development defaults -
   this is intentional, to catch a missed env var before it reaches production.
3. Run `docker compose up -d --build`.
4. Put a reverse proxy or platform in front that terminates TLS (e.g. Caddy/nginx, or a PaaS with
   automatic HTTPS). The admin endpoints use HTTP Basic auth, which is only safe over HTTPS.

### Required environment variables

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `META_APP_ID`, `META_APP_SECRET` | Meta app credentials |
| `BASE_URL` | Public URL used to build the OAuth redirect URI |
| `WEBHOOK_VERIFY_TOKEN` | Verifies Meta's webhook subscription handshake |
| `ENCRYPTION_SECRET_KEY` | AES-GCM key (16/24/32 bytes) encrypting stored Meta access tokens |
| `OAUTH_STATE_SECRET` | Signs the OAuth `state` parameter - must differ from `ENCRYPTION_SECRET_KEY` |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Credentials for the `/api/admin/**` operator endpoints |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins (never `*` in production) |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` in production to enable the startup secrets check |

### CI

`.github/workflows/ci.yml` runs `mvn -B verify` (build + full test suite) on every push/PR to
`main`.
