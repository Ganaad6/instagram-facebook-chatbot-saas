# API Documentation

## Authentication

Every endpoint under `/api/**`, except the ones explicitly marked "Public" below, requires an
`X-API-Key: <your-api-key>` header. The key is returned once, at registration or key-rotation
time - it is never shown again, only re-issued.

`/api/admin/**` endpoints instead require HTTP Basic auth with the operator credentials
(`ADMIN_USERNAME` / `ADMIN_PASSWORD`).

## Businesses
- `POST /api/businesses/register` - Register a new business (**Public**). Returns the business
  plus a one-time `apiKey` - store it now, it cannot be retrieved again.
- `GET /api/businesses/{id}` - Get business by ID (must be your own `id`)
- `PUT /api/businesses/{id}` - Update business (must be your own `id`)
- `POST /api/businesses/{id}/notifications/webhook-url` - Set the order-notification webhook URL

## Admin (HTTP Basic, ROLE_ADMIN)
- `GET /api/admin/businesses` - List all businesses
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
