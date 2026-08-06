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
- `DELETE /api/admin/businesses/{id}` - Permanently delete a business

## OAuth
- `GET /api/auth/meta/authorize?businessId=` - Get Meta OAuth URL (redirect)
- `GET /api/auth/meta/callback?code=&state=` - OAuth callback (**Public** - trust comes from the
  signed `state`, not an API key, since Meta redirects the browser here directly)
- `POST /api/auth/meta/refresh?businessId=` - Refresh token

## Webhook (**Public** - HMAC-signature verified instead of API key)
- `GET /webhook` - Webhook verification
- `POST /webhook` - Receive webhook events

## Flows
- `POST /api/flows` - Create flow (body includes `businessId`, must be your own)
- `GET /api/flows?businessId=` - Get flows for business
- `PUT /api/flows/{id}` - Update flow (must belong to your business)
- `DELETE /api/flows/{id}` - Delete flow
- `POST /api/flows/{id}/steps` - Add step
- `PUT /api/flows/steps/{id}` - Update step
- `DELETE /api/flows/steps/{id}` - Delete step
- `PUT /api/flows/{id}/activate` - Activate flow

## Products / Categories / Orders / Analytics
- `/api/businesses/{businessId}/products/**`
- `/api/businesses/{businessId}/categories/**`
- `/api/businesses/{businessId}/orders/**`
- `/api/businesses/{businessId}/analytics/**`

All require `businessId` in the path to match the authenticated API key's business.

## Conversations
- `GET /api/conversations?businessId=` - List conversations
- `GET /api/conversations/{id}` - Get conversation (must belong to your business)
- `GET /api/conversations/{id}/data` - Get conversation data

## Customers
- `GET /api/customers?businessId=` - List customers
