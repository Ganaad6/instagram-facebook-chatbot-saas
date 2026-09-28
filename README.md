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

# Get a Facebook/Instagram connect link to send to the shop owner (valid for
# OAUTH_STATE_TTL_MINUTES, default 60). They open it, log in and pick their Page; the app then
# stores the Page token, subscribes the Page to the webhook and fills in the Page/Instagram IDs.
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" https://your-host/api/admin/businesses/{id}/meta-connect-url

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
   The app trusts `X-Forwarded-For` only from private-network proxies (for rate limiting by real
   client IP); if your proxy connects from a public IP, set
   `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to a regex matching it.

### Database roles

The Postgres container creates three roles on first start (`docker/postgres/init`): the
`postgres` superuser (init only), `chatbot_app` (owns the schema, used by the app) and `directus`
(catalog tables only). Postgres is published on `127.0.0.1:5432` only.

**Upgrading an existing database volume** created before these roles existed: the init script
only runs on an empty volume, so run the one-time upgrade script, then set
`APP_DB_PASSWORD` / `DIRECTUS_DB_PASSWORD` / `POSTGRES_SUPERUSER_PASSWORD` in `.env` (the
superuser password is whatever the volume was created with) and restart:

```bash
docker compose stop app directus
docker compose exec -T postgres psql -U postgres -d chatbot_saas -v ON_ERROR_STOP=1 \
  -v app_password='<APP_DB_PASSWORD>' -v directus_password='<DIRECTUS_DB_PASSWORD>' \
  < docker/postgres/upgrade-existing-volume.sql
docker compose up -d
```

### Backups

```bash
# Nightly dump (e.g. from cron on the host); keep copies off the server
docker compose exec -T postgres pg_dump -U postgres -Fc chatbot_saas > backup-$(date +%F).dump
# Restore into an empty database
docker compose exec -T postgres pg_restore -U postgres -d chatbot_saas --clean < backup.dump
```

Product photos live in the `directus_uploads` volume - back that up too.

### Operations notes

- Logs default to `INFO` (`LOG_LEVEL`); `DEBUG` includes customer IDs and message metadata.
- On shutdown the app stops accepting requests and gives in-flight chat messages up to 30s to
  finish, so redeploys don't cut conversations off mid-reply.
- `/actuator/health` is the only public actuator endpoint and backs the container healthcheck.
- Rate limits (per client IP, per minute): registration 10, webhook 600, admin endpoints 30.

### Required environment variables

| Variable | Purpose |
|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL connection |
| `META_APP_ID`, `META_APP_SECRET` | Meta app credentials |
| `BASE_URL` | Public URL used to build the OAuth redirect URI |
| `META_GRAPH_API_VERSION` | Graph API version (default `v23.0`) - bump before Meta retires it |
| `OAUTH_STATE_TTL_MINUTES` | How long a connect link stays valid (default 60) |
| `CHATBOT_HANDOFF_TIMEOUT_HOURS` | Hours the bot stays paused for a customer after a handoff request or staff reply (default 12) |
| `CHATBOT_CONVERSATION_TIMEOUT_HOURS` | Idle hours before an unfinished order conversation is abandoned (default 24) |
| `WEBHOOK_VERIFY_TOKEN` | Verifies Meta's webhook subscription handshake |
| `ENCRYPTION_SECRET_KEY` | AES-GCM key (16/24/32 bytes) encrypting stored Meta access tokens |
| `OAUTH_STATE_SECRET` | Signs the OAuth `state` parameter - must differ from `ENCRYPTION_SECRET_KEY` |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | Credentials for the `/api/admin/**` operator endpoints |
| `CORS_ALLOWED_ORIGINS` | Allowed browser origins (never `*` in production) |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` in production to enable the startup secrets check |

### CI

`.github/workflows/ci.yml` runs `mvn -B verify` (build + full test suite) on every push/PR to
`main`.

## Chatbot behavior notes

- Customers can type **цэс**, **эхлэх**, **дахин**, **menu**, **start** or **restart** at any point
  to go back to the category menu.
- Messages from one customer are processed strictly in order (row lock on the customer), and
  Meta webhook redeliveries are recognized by message ID and skipped.
- If the shop deactivates a product mid-conversation, the customer is told it's sold out and
  sent back to the menu instead of the order being placed.

### Human handoff

A shop's staff can take a conversation over from the bot, per customer:

- **Customer asks for a person** by typing **оператор**, **ажилтан**, **хүн**, **human**,
  **agent** or **operator**. The bot acknowledges, goes quiet for that customer, and the shop's
  notification webhook gets a `HANDOFF_REQUESTED` event.
- **Staff reply from the shop's normal Meta inbox** (Meta Business Suite / the Page inbox /
  the Instagram app). The app sees the echo of that message, records it in the transcript as
  an `AGENT` message and pauses the bot so it doesn't talk over them. No extra tool needed.
- **Or staff use the API** (`/api/businesses/{id}/inbox` and `/customers/{id}/messages`,
  `/pause-bot`, `/resume-bot` - see API.md).
- The bot takes over again when staff call `resume-bot`, when the customer types a menu keyword
  (**цэс**, **menu**, ...), or after `CHATBOT_HANDOFF_TIMEOUT_HOURS` (default 12) without staff
  activity.

## Meta app setup (one time)

1. In the Meta developer dashboard, add the **Messenger** and **Instagram** products (Instagram
   messaging via the Messenger Platform, i.e. Facebook Login - not "Instagram Login").
2. Add `${BASE_URL}/api/auth/meta/callback` as a valid OAuth redirect URI under Facebook Login.
3. Configure webhooks for both the **Page** and **Instagram** objects: callback URL
   `${BASE_URL}/webhook`, verify token `WEBHOOK_VERIFY_TOKEN`, fields `messages`,
   `messaging_postbacks` and `message_echoes` (echoes are how the app notices staff replying
   from the Meta inbox). Each connected Page is subscribed to the app automatically; Pages
   connected before `message_echoes` was added need to reconnect once (send a new connect link).
4. Request **Advanced Access** via App Review for `pages_show_list`, `pages_messaging`,
   `pages_manage_metadata`, `instagram_basic` and `instagram_manage_messages`, plus the
   **Human Agent** feature if staff will reply more than 24 hours after a customer's last
   message. Until approved, only people with a role on the app can connect a Page or chat with
   the bot.

A shop's Instagram account must be a professional account linked to its Facebook Page, and the
shop must enable **Allow access to messages** in the Instagram app's privacy settings.

## Self-serve catalog management (Directus)

Shop owners can add their own products - name, price, description, and a **photo** - through
[Directus](https://directus.io), an open-source admin UI, instead of you managing their catalog
by hand via the API. `docker-compose.yml` already runs Directus as a second service pointed at
the same Postgres database the app uses, so no extra database or sync step is needed: whatever a
shop owner saves in Directus is exactly what the chatbot reads on the next message, including the
existing "in stock" toggle (the products' `isActive` field).

Directus connects as its own restricted `directus` database role: it can read, create and update
`products` and `categories` only. It cannot see `businesses` (Meta tokens, API key hashes),
customers, orders or messages, cannot delete products (switch `is_active` off instead - orders
reference them), and cannot alter the app's tables. The grants live in
`src/main/resources/db/postgres/R__directus_grants.sql`.

This is a one-time setup per deployment (not per shop) - do it once after your first
`docker compose up -d --build`:

1. Open Directus at `DIRECTUS_PUBLIC_URL` (default `http://localhost:8055`) and log in with
   `DIRECTUS_ADMIN_EMAIL` / `DIRECTUS_ADMIN_PASSWORD`.
2. **Settings → Data Model**, and add `categories` and `products` as collections **from the
   existing tables** (Directus will detect them since it's the same database). `businesses` is
   intentionally not visible to Directus.
3. On the `products` collection, find the existing `image_file_id` column and click
   **Manage Field** (not "Create Field" - that would try to add a duplicate column) and set its
   interface to **Image**. This turns it into a real drag-and-drop upload field backed by
   Directus's own file storage.
4. **Settings → Data Model → Directus Users**, add a custom field `business_id` (type Integer).
   This is what scopes each shop owner's login to only their own products.
5. Create a **Shop Owner** role with an access policy granting, on both `products` and
   `categories`:
   - **Read** and **Update**, with item permission `business_id` *equals*
     `$CURRENT_USER.business_id` (which rows they can see/edit);
   - **Create** and **Update**, with field validation `business_id` *equals*
     `$CURRENT_USER.business_id` and a field preset `business_id` = `$CURRENT_USER.business_id`
     (what they're allowed to save).

   Both halves matter: the item filter alone doesn't stop a shop owner from creating a product
   with, or editing a product to, another shop's `business_id`. Also hide `business_id` in the
   role's field permissions so it isn't editable at all.
6. **Settings → Files → (product images folder) → Permissions**, and give the **Public** role
   read access to it. Meta's servers fetch the image URL directly from the open internet with no
   auth, so the images themselves must be publicly readable (this does not expose anything else
   in Directus).
7. For each shop, create one Directus user with the **Shop Owner** role and their `business_id`
   set, and send them the Directus URL + their login. That's their entire "add my own products"
   experience - no app install, no API key needed on their end.

The chatbot resolves each product's photo as `${DIRECTUS_PUBLIC_URL}/assets/{image_file_id}` and
sends it as an image message immediately before the existing product menu, so this requires no
change to how customers interact with the bot.
