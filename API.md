# API Documentation

## Authentication

Every endpoint under `/api/**`, except the ones explicitly marked "Public" below, requires
either:

- an `X-API-Key: <your-api-key>` header (integrations). The key is returned once, at
  registration or key-rotation time - it is never shown again, only re-issued. It has the
  owner's full access; or
- a dashboard login session (the `SESSION` cookie from `POST /api/auth/login`). Cookie-based
  requests that change something (`POST`/`PUT`/`DELETE`) must also send the `X-XSRF-TOKEN`
  header with the value of the `XSRF-TOKEN` cookie; call `GET /api/auth/csrf` once to get it.

Dashboard users are `OWNER` or `STAFF`. Staff can do everything with orders, payments checks,
catalog, customers and the inbox; only the owner (or the API key) can change shop settings,
Meta/QPay connections, the notification webhook, the API key and staff logins - staff get `403`.

`/api/admin/**` endpoints instead require HTTP Basic auth with the operator credentials
(`ADMIN_USERNAME` / `ADMIN_PASSWORD`).

## Dashboard sign-in
- `GET /api/auth/csrf` - **Public**. Sets the `XSRF-TOKEN` cookie.
- `POST /api/auth/signup` `{"businessName", "name", "email", "password"}` - **Public** (unless
  `SIGNUP_ENABLED=false`). Creates a shop and its owner and signs them in. `201` with `/me`.
- `POST /api/auth/login` `{"email", "password"}` - **Public**. `401` wrong email or password
  (same answer for unknown emails), `403` user deactivated or shop suspended, `429` after 10
  wrong passwords in a row (locked 15 minutes).
- `POST /api/auth/logout`
- `GET /api/auth/me` - `{"user": {id, email, name, role, ...}, "business": {...}}`
- `POST /api/auth/password` `{"currentPassword", "newPassword"}` - Signs out other sessions.
- `GET /api/auth/links/{token}` - **Public**. What an invite / reset link is for:
  `{email, name, businessName, purpose: INVITE|RESET}`; `401` if used or expired.
- `POST /api/auth/links/accept` `{"token", "name", "password"}` - **Public**. Sets the password
  and signs in. Links work once; invites last 72h, resets 24h.

Passwords are 8-72 characters, stored with BCrypt. There is no email sending: invite and
reset links are returned by the API and handed over by the owner (or the admin, for owners).

## Staff logins (owner only)
All under `/api/businesses/{businessId}/staff`.
- `GET` - List users (`invitePending` until they set a password)
- `POST` `{"email", "name", "role": "OWNER"|"STAFF"}` - Invite; returns `{user, link: {url, expiresAt}}`
- `POST /{userId}/password-link` - New invite/reset link for someone who forgot their password
- `PUT /{userId}` `{"role", "active"}` - Change role or (de)activate; deactivating signs them out
- `DELETE /{userId}`

You can't demote, deactivate or delete yourself, or leave the shop without an active owner.

Errors are JSON `{"error": "...", "status": N}`. Malformed input is `400`, a value already taken
(e.g. another shop's email) `409`, and unexpected failures `500` with a generic message (details
are only in the server log).

## Businesses
- `POST /api/businesses/register` - Register a new business (**Public**). Returns the business
  plus a one-time `apiKey` - store it now, it cannot be retrieved again.
- `GET /api/businesses/{id}` - Get business by ID (must be your own `id`)
- `PUT /api/businesses/{id}` - Update business (must be your own `id`)
- `POST /api/businesses/{id}/notifications/webhook-url` `{"webhookUrl"}` - Set (or clear, with
  an empty value) the notification webhook URL (owner only). Must be a public `https://` URL;
  private, loopback and cloud-metadata addresses are refused (`400`).
- `POST /api/businesses/{id}/api-key` - Issue a new API key (owner only). Returned once; the old
  key stops working.

## Admin (HTTP Basic, ROLE_ADMIN)
- `GET /api/admin/businesses` - List all businesses
- `POST /api/admin/businesses/{id}/owner-invite` `{"email", "name"}` - Dashboard login link for
  the shop's owner (creates the owner if needed; for an existing owner it's a password reset)
- `POST /api/admin/businesses/{id}/suspend` - Suspend a business (e.g. non-payment); its API key
  stops working and its incoming webhook messages are dropped
- `POST /api/admin/businesses/{id}/activate` - Reactivate a suspended business
- `POST /api/admin/businesses/{id}/rotate-api-key` - Issue a new API key for a business (e.g. if
  the old one leaked); returns the new key once
- `GET /api/admin/businesses/{id}/meta-connect-url` - Generate a Facebook/Instagram connect link
  for a business, to send to the shop owner (same response as `/api/auth/meta/authorize`)
- `DELETE /api/admin/businesses/{id}` - Permanently delete a business

## OAuth
- `GET /api/auth/meta/authorize?businessId=` - Returns `{"authorizationUrl", "expiresInMinutes"}`
  as JSON (not a redirect - a browser can't send `X-API-Key`). Open the URL in a browser to
  connect the business's Facebook Page and its linked Instagram professional account.
- `GET /api/auth/meta/callback?code=&state=` - OAuth callback (**Public** - trust comes from the
  signed `state`, not an API key, since Meta redirects the browser here directly). Exchanges the
  code for a long-lived token, picks the Page (the business's `facebookPageId` if set, otherwise
  the only Page shared), subscribes it to the webhook, stores its non-expiring Page token and
  fills in `facebookPageId` / `instagramAccountId`. Returns an HTML result page.

Page tokens don't expire, so there is no refresh endpoint. If the owner revokes access or
changes their Facebook password, send them a new connect link.

## Webhook (**Public** - HMAC-signature verified instead of API key)
- `GET /webhook` - Webhook verification
- `POST /webhook` - Receive webhook events
- `GET|POST /webhook/qpay/{orderId}?token=` - QPay payment callback. Always `200 SUCCESS`; the
  payment is only recorded after the app confirms it with QPay. Forged tokens are ignored.

## Product photos
- `POST /api/businesses/{businessId}/media` - multipart field `file`: a JPEG, PNG, WebP or GIF
  up to 5 MB (the type is detected from the file itself). Returns `{"id", "url"}`; pass the
  `id` as `imageFileId` when creating/updating a product. `PUT .../products/{id}`
  `{"removeImage": true}` removes a product's photo.
- `GET /media/{id}` - **Public** (Meta fetches photos from here). Uploads no product uses are
  deleted after a day.

## Products / Categories / Orders / Analytics
- `/api/businesses/{businessId}/products/**`
- `/api/businesses/{businessId}/categories/**`
- `/api/businesses/{businessId}/orders/**`
- `/api/businesses/{businessId}/analytics/**`

All require `businessId` in the path to match the authenticated API key's business.

Orders carry a snapshot of what was bought: `productName`, `unitPrice`, `quantity` and
`totalAmount` are fixed at order time, so later product edits don't change past orders
(`productPrice` is kept as an alias of `unitPrice`). The CSV export
(`/orders/export`) includes the same columns. The analytics summary includes `totalRevenue`
(sum of non-cancelled orders) and `totalQuantity` per top product.

`GET /orders` takes optional `status` and `customerId` filters (plus `page`, `size`), newest
first; `customerId` lists one chat customer's orders.

`PUT /api/businesses/{id}` (owner) also takes the bot's `welcomeMessage` and `deliveryNote`
(up to 500 characters each; blank clears, a missing field is left unchanged).

The order-notification webhook sends `{"event": "NEW_ORDER", "orderId", "businessId",
"product", "quantity", "unitPrice", "totalAmount", "customerName", "phone", "address", "status"}`.

## Payments (QPay)
All under `/api/businesses/{businessId}`, API key required.
- `PUT /payments/qpay` `{"username", "password", "invoiceCode"}` - Connect or replace the shop's
  QPay merchant account. `400` if QPay rejects the credentials. `GET /api/businesses/{id}`
  shows `qpayConnected`; the password is never returned.
- `DELETE /payments/qpay` - Disconnect. New orders stop getting invoices; already-sent ones can
  still be paid and are still recorded.
- `POST /orders/{orderId}/payment/check` - Ask QPay now; returns the updated order.

Orders include `paymentStatus` (`NOT_REQUESTED`, `PENDING` = invoice sent and unpaid, `PAID`),
`paymentUrl` and `paidAt`; the CSV export adds `paymentStatus` and `paidAt` columns.
`PUT /orders/{id}/status` with `CANCELLED` withdraws an unpaid invoice. The notification webhook
receives `{"event": "PAYMENT_RECEIVED", "businessId", "orderId", "amount", "provider": "QPAY",
"paymentId"}` when an order is paid.

## Conversations
- `GET /api/conversations?businessId=` - List conversations
- `GET /api/conversations/{id}` - Get conversation (must belong to your business)
- `GET /api/conversations/{id}/messages` - Full transcript, oldest first: customer messages
  (`INBOUND`) and delivered replies (`OUTBOUND`); `senderType` tells bot and staff replies apart

## Human handoff (staff inbox)
All under `/api/businesses/{businessId}`, API key required.
- `GET /inbox` - Customers who asked for a person (oldest request first), then others the bot is
  currently paused for. Each includes `botPausedUntil` and `handoffRequestedAt`.
- `GET /customers/{customerId}/messages?limit=100` - The customer's latest messages across all
  conversations (max 500), oldest first. `senderType` is `CUSTOMER`, `BOT` or `AGENT`.
- `POST /customers/{customerId}/messages` `{"text": "..."}` - Reply as a staff member (max 2000
  chars). Pauses the bot for this customer. Within 24h of the customer's last message this is a
  normal reply; between 24h and 7 days it's sent with Meta's `HUMAN_AGENT` tag (needs the Human
  Agent permission from App Review); after 7 days → `409`. `502` if Meta rejects the message.
- `POST /customers/{customerId}/pause-bot?hours=` - Silence the bot for this customer (default
  `CHATBOT_HANDOFF_TIMEOUT_HOURS`, 1-168).
- `POST /customers/{customerId}/resume-bot` - Hand back to the bot; the unfinished bot
  conversation is abandoned so the customer's next message starts fresh.

A customer of another business returns `404`. The order-notification webhook also receives
`{"event": "HANDOFF_REQUESTED", "businessId", "customerId", "platform", "message"}` when a
customer asks for a person.

## Customers
- `GET /api/customers?businessId=` - List customers
