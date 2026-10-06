# Meta App Review – submission pack

Everything to paste into the Meta app dashboard (developers.facebook.com → your app) to request
Advanced Access for Messenger and Instagram. Replace `https://YOUR-DOMAIN` with the production
`BASE_URL`.

## 1. Before you submit

- [ ] App deployed on a stable HTTPS domain (not a `trycloudflare.com` quick tunnel; reviewers test over several days).
- [ ] App renamed to **ChatShop** in the Meta dashboard (App settings → Basic → Display name), matching the policy.
- [ ] `.env` on the server has `LEGAL_OPERATOR_NAME=ChatShop` and `LEGAL_CONTACT_EMAIL` set; open `/privacy` and check both show.
- [ ] **Business verification** done for the Business portfolio that owns the app (Business settings → Security centre).
- [ ] Instagram use case set up with the **"API setup with Facebook login"** variant, so `instagram_basic` and
      `instagram_manage_messages` appear under Permissions (otherwise connect says "Invalid Scopes").
- [ ] `META_OAUTH_SCOPES=pages_show_list,pages_messaging,pages_manage_metadata,business_management,instagram_basic,instagram_manage_messages`
- [ ] Each permission called successfully at least once in the last 30 days (Meta greys out "Request" until then):
      connect a Page + Instagram account and exchange a few messages on both.
- [ ] Reviewer dashboard login created (see §5).

## 2. App settings → Basic

| Field | Value |
|---|---|
| App icon | 1024×1024 PNG, no company name (Meta rejects icons that imitate Facebook/Instagram logos) |
| Category | Business and pages |
| Privacy policy URL | `https://YOUR-DOMAIN/privacy?lang=en` |
| Terms of service URL | `https://YOUR-DOMAIN/terms?lang=en` |
| User data deletion | "Data deletion callback URL": `https://YOUR-DOMAIN/webhook/meta/data-deletion` |
| App domains | `YOUR-DOMAIN` |

Facebook Login for Business → Settings:

| Field | Value |
|---|---|
| Valid OAuth redirect URIs | `https://YOUR-DOMAIN/api/auth/meta/callback` |
| Deauthorize callback URL | `https://YOUR-DOMAIN/webhook/meta/deauthorize` |

Webhooks (Messenger and Instagram products): callback `https://YOUR-DOMAIN/webhook`, verify token =
`WEBHOOK_VERIFY_TOKEN`. Fields: `messages`, `messaging_postbacks`, `message_echoes`.

## 3. Permission justifications

Paste one per permission in App Review → Permissions and features → Request advanced access.

**pages_messaging**
> Our app is a sales chatbot for small online shops. A shop connects its Facebook Page in our web
> dashboard; when a customer messages the Page, our app replies on the shop's behalf with the shop's
> product catalogue (product cards with photo, price and an "Order" button), takes the order (quantity,
> name, phone, delivery address) and sends an order confirmation and an optional payment link. Shop
> staff can also read the conversation and reply from our dashboard. We only reply to messages the
> customer sent first, within the 24-hour messaging window, and never send promotional messages.

**pages_show_list**
> When a shop owner connects Facebook in our dashboard, we list the Pages they granted so we can link
> the correct Page to their shop and route that Page's incoming messages to it. We store only the Page
> ID and its access token (encrypted).

**pages_manage_metadata**
> Required to subscribe the shop's Page to our app's webhook (POST /{page-id}/subscribed_apps) right
> after the shop connects it, so that we receive the Page's incoming messages and postbacks. Without it
> the chatbot cannot receive customer messages.

**business_management**
> Most of our shops manage their Page through a Meta Business portfolio. Without business_management,
> Pages owned by a Business portfolio are not returned during Facebook Login, so the shop cannot connect
> its Page. We use it only to read the Pages the person chose to share during login; we do not create,
> change or read any other business assets.

**instagram_basic**
> Used to read the Instagram professional account linked to the shop's Facebook Page (its ID) during
> connection, so messages sent to the shop on Instagram are routed to the right shop in our app.

**instagram_manage_messages**
> Same as pages_messaging, for Instagram Direct: when a customer messages the shop's Instagram
> professional account, our app replies with the shop's products, takes the order and sends a
> confirmation, and shop staff can read and answer the conversation from our dashboard. Replies are
> only sent in response to the customer's messages, within the 24-hour window.

## 4. Screencasts

One recording can cover several permissions; upload it on each. Record in English UI where possible
(or add English captions – the dashboard is in Mongolian), 1080p, no sound needed.

**Video A – connect (pages_show_list, pages_manage_metadata, business_management, instagram_basic)**
1. Open `https://YOUR-DOMAIN/login`, sign in as the shop owner.
2. Settings (Тохиргоо) → Facebook & Instagram → click **Facebook-ээр холбох** (Connect with Facebook).
3. Facebook Login dialog: show the permission list, choose the Business portfolio, the Page and the
   Instagram account, and continue.
4. Back in the dashboard, show the "connected" message with the Page and Instagram account.

**Video B – Messenger (pages_messaging)**
1. Split screen: phone/browser as a customer on Messenger, dashboard on the other side.
2. Customer sends "Hi" to the Page → bot replies with the greeting and menu.
3. Customer opens products → product cards appear → taps **Order** → enters quantity, name, phone,
   address → bot sends the order confirmation with the order number.
4. Dashboard → Orders: the new order appears. Chats: the conversation appears; staff types a reply
   and the customer receives it in Messenger.

**Video C – Instagram (instagram_manage_messages)**: same as Video B, in Instagram Direct.

## 5. Notes for the reviewer (paste into "Testing instructions")

```
Dashboard: https://YOUR-DOMAIN/login
Email: reviewer@YOUR-DOMAIN   Password: <create one, see below>

1. Sign in. Go to Settings (Тохиргоо) → "Facebook & Instagram" and click
   "Facebook-ээр холбох" (Connect with Facebook).
2. Log in with your Facebook test user, grant the permissions and pick a Page you manage
   (and its linked Instagram professional account).
3. From another Facebook/Instagram account, send "Hi" to that Page / Instagram account.
   The bot replies with a menu. Choose "Products", tap "Order" on a product and follow the
   steps (quantity, name, phone, address). You receive an order confirmation.
4. In the dashboard: Orders shows the order; Chats shows the conversation, where you can
   type a reply that is delivered to the customer.

The dashboard is in Mongolian; the steps above give the English meaning of each button.
Privacy policy: https://YOUR-DOMAIN/privacy?lang=en
```

Create the reviewer login: sign up a shop at `/signup` with the reviewer email, or invite the
reviewer as staff into a demo shop with a few products and photos already added.

## 6. After approval

- Switch the app to **Live** mode (top of the app dashboard). In dev mode only people with an app
  role can use it ("app not active").
- Meta may also ask for **Access verification** (for apps used by businesses other than your own)
  under App Review → Verification; answer it the same way as §3.
