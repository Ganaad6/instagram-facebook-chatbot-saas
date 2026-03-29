# API Documentation

## Businesses
- `POST /api/businesses/register` - Register a new business
- `GET /api/businesses/{id}` - Get business by ID
- `PUT /api/businesses/{id}` - Update business
- `DELETE /api/businesses/{id}` - Delete business

## OAuth
- `GET /api/auth/meta/authorize` - Get Meta OAuth URL
- `GET /api/auth/meta/callback` - OAuth callback
- `POST /api/auth/meta/refresh` - Refresh token

## Webhook
- `GET /webhook` - Webhook verification
- `POST /webhook` - Receive webhook events

## Flows
- `POST /api/flows` - Create flow
- `GET /api/flows?businessId=` - Get flows for business
- `PUT /api/flows/{id}` - Update flow
- `DELETE /api/flows/{id}` - Delete flow
- `POST /api/flows/{id}/steps` - Add step
- `PUT /api/flows/steps/{id}` - Update step
- `DELETE /api/flows/steps/{id}` - Delete step
- `PUT /api/flows/{id}/activate` - Activate flow

## Conversations
- `GET /api/conversations?businessId=` - List conversations
- `GET /api/conversations/{id}` - Get conversation
- `GET /api/conversations/{id}/data` - Get conversation data

## Customers
- `GET /api/customers?businessId=` - List customers
