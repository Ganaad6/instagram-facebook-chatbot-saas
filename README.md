# Instagram/Facebook Chatbot SaaS Platform

A multi-tenant platform that lets shops sell through Messenger and Instagram: a chatbot shows
the shop's catalog, takes orders (name, phone, address), sends a QPay payment link, and hands
the conversation to staff when a customer asks for a person. Shop owners and staff run
everything from a web dashboard (Mongolian UI).

## Features
- **Chatbot** for Facebook Messenger and Instagram DMs: category → product → quantity →
  confirmation → contact details → order, with product photos and restart keywords
- **QPay payments**: each shop connects its own QPay merchant account; orders get a payment
  link, payments are confirmed with QPay and the customer is thanked in chat
- **Human handoff**: customers can ask for a person; staff reply from the dashboard or their
  normal Meta inbox and the bot stays quiet meanwhile
- **Dashboard** for owners and staff: overview, orders, catalog with photos, chats, settings,
  staff logins - works on phones
- Multi-tenant with self-serve signup, owner/staff roles, and an admin API to suspend/activate
  shops (manual billing)
- Per-shop API keys and notification webhooks (`NEW_ORDER`, `PAYMENT_RECEIVED`,
  `HANDOFF_REQUESTED`) for integrations

## Contents
- [Deploying to production](#deploying-to-production)
- [Meta app setup](#meta-app-setup-one-time)
- [Running the platform](#running-the-platform) - onboarding shops, admin runbook, backups, upgrades
- [Configuration reference](#configuration-reference)
- [Security model](#security-model)
- [How the chatbot behaves](#how-the-chatbot-behaves) - handoff, QPay
- [Development](#development)
- [API.md](API.md) - full endpoint documentation

## Deploying to production

The stack is `docker-compose.yml`: PostgreSQL, the app (API + dashboard in one container) and
[Caddy](https://caddyserver.com) in front for HTTPS with automatic Let's Encrypt certificates.
It runs on any Linux server with Docker - a small VPS (2 GB RAM) is plenty to start.

1. **Server and DNS.** Install Docker with Compose v2. Point your domain's DNS A record (e.g.
   `shop.example.mn`) at the server and open ports 80 and 443.
2. **Configure.** Clone the repository and create `.env`:
   ```bash
   cp .env.example .env
   ```
   Set `DOMAIN` and `BASE_URL` (`https://` + domain), and replace every placeholder
   (`set_...`, `your_...`) with a unique value - `openssl rand -base64 24` for passwords and
   secrets, `openssl rand -hex 16` for the 32-character `ENCRYPTION_SECRET_KEY`. The `prod`
   profile (the compose default) **refuses to start** while any secret is missing, left at a
   default, or copied from the template, and lists what to fix.
3. **Start.**
   ```bash
   docker compose up -d --build
   docker compose logs -f app      # wait for "Started ChatbotSaasApplication"
   ```
   Database migrations run automatically on every start. Open `https://your-domain` - you
   should see the dashboard's login page.
4. **Set up the Meta app** (next section) so shops can connect their Facebook Page.
5. **Onboard your first shop** - sign up on the dashboard, or see [Onboarding shops](#onboarding-shops).

**Try the whole stack locally first** (needs Docker and free ports 80/443):
```bash
scripts/smoke-test.sh          # builds, starts on https://localhost with throwaway secrets,
                               # checks sign-up, catalog, photos, webhooks, headers; tears down
scripts/smoke-test.sh --keep   # same, but leaves it running to click around
```

### Meta app setup (one time)

1. In the Meta developer dashboard, create an app and add the **Messenger** and **Instagram**
   products (Instagram messaging via the Messenger Platform, i.e. Facebook Login - not
   "Instagram Login"). Put its id and secret in `META_APP_ID` / `META_APP_SECRET`.
2. Add `${BASE_URL}/api/auth/meta/callback` as a valid OAuth redirect URI under Facebook Login.
3. Configure webhooks for both the **Page** and **Instagram** objects: callback URL
   `${BASE_URL}/webhook`, verify token `WEBHOOK_VERIFY_TOKEN`, fields `messages`,
   `messaging_postbacks` and `message_echoes` (echoes are how the app notices staff replying
   from the Meta inbox). Each Page a shop connects is subscribed to the app automatically.
4. Request **Advanced Access** via App Review for `pages_show_list`, `pages_messaging`,
   `pages_manage_metadata`, `instagram_basic` and `instagram_manage_messages`, plus the
   **Human Agent** feature if staff will reply more than 24 hours after a customer's last
   message. Until approved, only people with a role on the app can connect a Page or chat with
   the bot - enough for testing with your own accounts.

A shop's Instagram account must be a professional account linked to its Facebook Page, and the
shop must enable **Allow access to messages** in the Instagram app's privacy settings.

## Running the platform

### Onboarding shops

**Self-serve (default):** a shop owner signs up at `https://your-domain/signup`, then in
**Тохиргоо → Холболт** connects their Facebook Page (and linked Instagram) and optionally their
QPay merchant account, and adds categories and products under **Бараа**. The overview page
shows a checklist of these steps.

**Admin-onboarded** (`SIGNUP_ENABLED=false`, or shops you set up for customers): register the
shop through the API, then send the owner a one-time link to set their password:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -H 'Content-Type: application/json' \
  -d '{"email":"owner@shop.mn","name":"Owner name"}' \
  https://your-domain/api/admin/businesses/{id}/owner-invite
```

Owners invite their own staff from **Тохиргоо → Хэрэглэгчид**. There is no email sending: invite
and password-reset links are shown to whoever creates them, to pass on (chat, SMS...). A staff
member who forgot their password gets a new link from the owner; an owner locked out gets one
from you with the same `owner-invite` call.

### Admin runbook (manual billing)

Billing is handled outside the app; the admin API is the on/off switch per shop:

```bash
A="-u $ADMIN_USERNAME:$ADMIN_PASSWORD"
curl $A https://your-domain/api/admin/businesses                              # list shops
curl $A -X POST https://your-domain/api/admin/businesses/{id}/suspend         # e.g. non-payment
curl $A -X POST https://your-domain/api/admin/businesses/{id}/activate        # once paid
curl $A https://your-domain/api/admin/businesses/{id}/meta-connect-url        # Meta connect link
curl $A -X POST https://your-domain/api/admin/businesses/{id}/rotate-api-key  # leaked API key
```

A suspended shop's dashboard logins and API key stop working at once, and the bot ignores its
customers' messages until it is reactivated.

### Backups

Everything - orders, chats, the catalog and product photos - is in PostgreSQL:

```bash
# Nightly dump (e.g. from cron on the host); keep copies off the server
docker compose exec -T postgres pg_dump -U postgres -Fc chatbot_saas > backup-$(date +%F).dump
# Restore into an empty database
docker compose exec -T postgres pg_restore -U postgres -d chatbot_saas --clean < backup.dump
```

Also keep `.env` somewhere safe: without the same `ENCRYPTION_SECRET_KEY` the stored Meta Page
tokens and QPay passwords can't be decrypted (shops would have to reconnect).

### Upgrading

```bash
git pull
docker compose up -d --build
```

New database migrations are applied on startup. Dashboard sessions are stored in the database,
so signed-in users stay signed in across the restart.

### Operations notes

- Logs: `docker compose logs -f app`. Default level `INFO` (`LOG_LEVEL`); `DEBUG` includes
  customer IDs and message metadata. Unexpected errors are logged with a stack trace and
  answered with a generic message.
- `/actuator/health` is the only public actuator endpoint and backs the container healthcheck.
- On shutdown the app stops accepting requests and gives in-flight chat messages up to 30s to
  finish, so redeploys don't cut conversations off mid-reply.
- Rate limits per client IP, per minute: registration 10, sign-in 20, webhook 600, admin 30;
  plus a 15-minute lock after 10 wrong passwords for one account. They are in-memory, so they
  assume a single app instance.
- Postgres is published on `127.0.0.1:5432` only and the app on `127.0.0.1:8080`; the public
  only reaches Caddy.

### Database roles

The Postgres container creates the roles on first start (`docker/postgres/init`): the
`postgres` superuser (init only) and `chatbot_app` (owns the schema, used by the app).

**Upgrading an existing database volume** created before these roles existed: the init script
only runs on an empty volume, so run the one-time upgrade script, then set `APP_DB_PASSWORD`
and `POSTGRES_SUPERUSER_PASSWORD` in `.env` (the superuser password is whatever the volume was
created with) and restart:

```bash
docker compose stop app
docker compose exec -T postgres psql -U postgres -d chatbot_saas -v ON_ERROR_STOP=1 \
  -v app_password='<APP_DB_PASSWORD>' -v directus_password='<any value>' \
  < docker/postgres/upgrade-existing-volume.sql
docker compose up -d
```

## Configuration reference

All settings are environment variables (see `.env.example` for a commented template).

| Variable | Purpose |
|---|---|
| `DOMAIN` | Domain Caddy serves and gets a certificate for (`localhost` for local tries) |
| `BASE_URL` | Public URL (`https://...`): OAuth redirect, invite links, QPay callbacks, photo URLs |
| `SPRING_PROFILES_ACTIVE` | `prod` (compose default): startup secrets check, HTTPS-only cookies |
| `POSTGRES_SUPERUSER_PASSWORD`, `APP_DB_PASSWORD` | Database roles (compose) |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Database connection (outside compose) |
| `ENCRYPTION_SECRET_KEY` | AES-GCM key (16/24/32 bytes) for Meta tokens and QPay passwords at rest - never change it |
| `OAUTH_STATE_SECRET` | Signs Meta connect links and QPay callback URLs; must differ from the encryption key |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | HTTP Basic credentials for `/api/admin/**` |
| `META_APP_ID`, `META_APP_SECRET` | Meta app credentials (the secret also verifies webhook signatures) |
| `WEBHOOK_VERIFY_TOKEN` | Meta's webhook subscription handshake |
| `META_GRAPH_API_VERSION` | Graph API version (default `v23.0`) - bump before Meta retires it |
| `OAUTH_STATE_TTL_MINUTES` | How long a Meta connect link stays valid (default 60) |
| `META_OAUTH_SCOPES` | Permissions the Facebook connect asks for (default: Pages + Instagram messaging). Messenger only: `pages_show_list,pages_messaging,pages_manage_metadata` |
| `QPAY_API_URL` | QPay merchant API. Production `https://merchant.qpay.mn/v2`; the sandbox is refused under `prod` |
| `QPAY_RECONCILE_WINDOW_HOURS` | Unpaid invoices younger than this are re-checked every 5 min (default 3) |
| `SIGNUP_ENABLED` | Self-serve shop sign-up (dashboard and `POST /api/businesses/register`), default `true` |
| `SESSION_TIMEOUT` | Idle time before a dashboard session expires (default `12h`) |
| `AUTH_INVITE_TTL_HOURS`, `AUTH_RESET_TTL_HOURS` | Invite / password-reset link lifetime (72 / 24) |
| `CHATBOT_CONVERSATION_TIMEOUT_HOURS` | Idle hours before an unfinished order conversation is abandoned (default 24) |
| `CHATBOT_HANDOFF_TIMEOUT_HOURS` | Hours the bot stays paused after a handoff request or staff reply (default 12) |
| `CORS_ALLOWED_ORIGINS` | Other browser apps allowed to call the API (never `*` in production) |
| `TZ` | Time zone for timestamps and "today" (default `Asia/Ulaanbaatar`) |
| `LOG_LEVEL`, `FORWARD_HEADERS_STRATEGY`, `APP_PORT` | Operations (see `.env.example`) |

## Security model

- **Dashboard users** sign in with email + password (BCrypt). Sessions are HttpOnly, Secure,
  SameSite=Lax cookies stored in PostgreSQL (Spring Session) and re-checked on every request,
  so deactivating a user, suspending a shop or changing a password ends sessions immediately.
  Cookie-authenticated writes require a CSRF token (cookie-to-header). `OWNER`s manage
  settings, connections and staff; `STAFF` handle orders, catalog and chats.
- **Integrations** use a per-shop API key (`X-API-Key`, SHA-256 hashed at rest, shown once).
- **Tenant isolation:** every shop-scoped endpoint ties the requested shop to the caller.
- **Admin** endpoints use HTTP Basic auth (`ADMIN_USERNAME` / `ADMIN_PASSWORD`) - HTTPS only.
- **Public endpoints** protect themselves: the Meta webhook by HMAC signature, the Meta
  callback by a signed, expiring `state`, the QPay callback by an HMAC token plus confirming
  every payment with QPay, product photos by random ids.
- **Secrets at rest:** Meta Page tokens and QPay passwords are AES-GCM encrypted.
- **Shop-supplied URLs:** notification webhooks must be public `https://` addresses; private,
  loopback and metadata addresses are refused when saved and again when connecting.
- **Headers:** a strict Content-Security-Policy (the dashboard loads only its own files),
  `X-Frame-Options: DENY`, HSTS, `nosniff`, a strict referrer policy.

## How the chatbot behaves

- Customers can type **цэс**, **эхлэх**, **дахин**, **menu**, **start** or **restart** at any point
  to go back to the category menu.
- Messages from one customer are processed strictly in order (row lock on the customer), and
  Meta webhook redeliveries are recognized by message ID and skipped.
- If the shop deactivates a product mid-conversation, the customer is told it's sold out and
  sent back to the menu instead of the order being placed.
- Products are sent as cards that scroll sideways (photo, name, price, description and a
  **Захиалах** button), numbered so typing the number also works. A card's button works even
  from an older message. If Meta refuses the cards, the photos and a numbered list are sent.
- Choices (menus, quantity, yes/no) come with quick-reply buttons on both Messenger and
  Instagram; Instagram shows them only in its phone app, so the numbers are always in the text.
  If Meta refuses a message with buttons, it is resent as plain text.
- The phone question offers Meta's "share my number" button.
- A customer who ordered before is offered their last name, phone and address
  (**1** use them, **2** enter new ones).
- The first menu of a conversation opens with the shop's greeting, and the order confirmation
  carries the shop's delivery note - both set in **Тохиргоо → Бот** (500 characters each).
- Input the bot can't use (a question instead of a number) gets a short hint first. On the
  second miss in a row the bot suggests **оператор** (a person) or **цэс** and shows the choices
  again; after that it only repeats the suggestion instead of the menu.
- The order confirmation carries the order number, the QPay link if any, and how to reach a
  person. A customer who writes again while an order from the last 7 days is still new or
  confirmed gets that order's status (and unpaid QPay link) once, not the menu; the bot then
  stays quiet until they type **цэс** or **оператор**.
- Phone numbers are accepted as `9911 2233`, `9911-2233` or `+976 99112233` and stored as 8 digits.

### Human handoff

A shop's staff can take a conversation over from the bot, per customer:

- **Customer asks for a person** by typing **оператор**, **ажилтан**, **хүн**, **human**,
  **agent** or **operator**. The bot acknowledges and goes quiet for that customer; the
  dashboard's **Чат** tab shows a waiting badge, and the notification webhook gets a
  `HANDOFF_REQUESTED` event.
- **Staff reply from the dashboard** (**Чат**) or **from the shop's normal Meta inbox** (Meta
  Business Suite / the Page inbox / the Instagram app). The app sees the echo of an inbox
  reply, records it in the transcript and pauses the bot so it doesn't talk over them.
- The bot takes over again when staff press **Ботод шилжүүлэх** (`resume-bot`), when the
  customer types a menu keyword, or after `CHATBOT_HANDOFF_TIMEOUT_HOURS` without staff activity.

### QPay payments

Each shop is paid into **its own** QPay merchant account; the platform holds no QPay account of
its own and never touches the money.

1. The shop gets a merchant account from QPay (username, password and invoice code) and
   enters it in **Тохиргоо → Холболт** (or `PUT /api/businesses/{id}/payments/qpay`). The
   credentials are verified with QPay before being stored; the password is encrypted at rest.
2. When the bot places an order it creates a QPay invoice for the order total and ends the
   confirmation with the QPay link (QR code + a button per bank app). If QPay is down the order
   is still saved and the customer gets the usual "we'll contact you" message.
3. QPay calls `${BASE_URL}/webhook/qpay/{orderId}?token=...` when paid. The app never trusts
   the callback itself - it asks QPay (`payment/check`) and records the payment only if the
   paid amount covers the order total, then thanks the customer in chat and sends the shop a
   `PAYMENT_RECEIVED` notification. Missed callbacks are caught by a reconcile every 5 minutes
   (for `QPAY_RECONCILE_WINDOW_HOURS`), or on demand with **Төлбөр шалгах** on the order.

The payment status (`NOT_REQUESTED`/`PENDING`/`PAID`) is separate from the order's fulfilment
status, which stays the shop's to change. Cancelling an unpaid order withdraws its invoice;
refunds of paid orders are done by the shop in QPay.

**Testing without a merchant account:** QPay's sandbox (`https://merchant-sandbox.qpay.mn/v2`,
the default outside the `prod` profile) accepts the public test merchant `TEST_MERCHANT` /
`123456` with invoice code `TEST_INVOICE`. Sandbox links can't take real payments, and
`BASE_URL` must be publicly reachable (e.g. via a tunnel) for its callback to arrive.

## Development

Requirements: JDK 17, Maven 3.9, Node 22, and PostgreSQL 14+ for running the app locally.

```bash
createdb chatbot_saas
mvn spring-boot:run                  # API on http://localhost:8080 (sandbox QPay, dev secrets)
cd frontend && npm install && npm run dev   # dashboard on http://localhost:5173, proxied to :8080
```

`npm run build` puts the dashboard in `frontend/dist`, which `mvn package` bundles into the jar
(the Dockerfile does both). Without it the jar still works, just without the dashboard pages.

**Tests:** `mvn verify` runs the backend suite - integration tests use a real embedded
PostgreSQL 15 (zonky, no Docker needed) with the same migrations as production. Use JDK 17: on
much newer JDKs (e.g. 26) Mockito can't mock classes yet. `cd frontend && npm test` runs the
dashboard's unit tests. CI (`.github/workflows/ci.yml`) runs both and builds the dashboard into
the jar on every push/PR to `main`.

### Directus (legacy)

Before the dashboard existed, shops edited their catalog in [Directus](https://directus.io).
It is no longer needed. Deployments that still want it can run it with
`docker compose --profile directus up -d` after setting the `DIRECTUS_*` variables in `.env`
(its database role only gets `SELECT/INSERT/UPDATE` on `products` and `categories`, see
`src/main/resources/db/postgres/R__directus_grants.sql`). Set `DIRECTUS_PUBLIC_URL` if products
still have photos uploaded through Directus, so their URLs keep resolving; photos uploaded in
the dashboard are stored in the database and served at `/media/{id}`.
