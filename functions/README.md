# RescueFarm HTTP backend (Render)

This folder replaces Firebase Cloud Functions with an Express API. The Firebase project can stay on Spark; deploy Firestore rules/indexes separately with `firebase deploy --only firestore`.

## Deploy on Render Free

1. Push this repository to a private GitHub repository. `serviceAccountKey.json` and `.env` are ignored; never commit credentials.
2. Create a Render Web Service from the repository, or use `render.yaml`. Set Root Directory to `functions`, Build Command to `npm install`, Start Command to `npm start`, and plan to Free.
3. In Render Environment, add `FIREBASE_SERVICE_ACCOUNT_JSON` with the service-account JSON value. Create a dedicated service account with only the Firestore read/write and Firebase Cloud Messaging permissions required by these routes. Do not expose this value to Android.
4. After deployment, check `https://<service>.onrender.com/health`.
5. Build Android with `RENDER_BACKEND_URL=https://<service>.onrender.com` in the local Gradle properties (for example `%USERPROFILE%\.gradle\gradle.properties`) or pass `-PRENDER_BACKEND_URL=...` to Gradle. Do not commit a deployment-specific URL if you want to keep environments configurable.
6. Deploy only Firestore configuration from the repository root: `firebase deploy --only "firestore"`.

## Routes

- `POST /seller/payment-qr`: active Seller only. Generates from the owner's saved bank details using VietQR API credentials on Render, uploads PNG through the Cloudinary signing Worker and saves the QR URL automatically. Configure `VIETQR_CLIENT_ID`, `VIETQR_API_KEY`, `CLOUDINARY_SIGNING_URL` and `CLOUDINARY_CLOUD_NAME`. Deploy the updated Worker first. See `docs/checkout-shipping-and-bank-setup.md` for setup and the VietQR Generate API retirement date.

- `POST /sellers/names`: public for Customer and Guest carts. Accepts up to 50 `sellerIds` and returns only the `fullName` of active, approved Sellers in `sellerNames`. Deploy this route before distributing the Android cart name update; no Firestore rule changes or data migration are required.
- `POST /quoteCustomerCheckout`: Firebase ID token required; Customer only. Re-reads product, batch, promotion, campaign, seller and shipping configuration. This is an estimate, not an inventory reservation.
- `POST /checkoutCustomer`: Firebase ID token required; Customer only. The server transaction revalidates prices, stock, the approved Seller, campaign and shipping, then writes Order/items/Payment/optional Shipment, reserves batch/campaign stock and removes bought cart quantities. Client Firestore order/items/payment/shipment creation and checkout reservation writes are denied.
- `POST /quoteGuestCheckout`: public, rate limited. Uses the same quote implementation.
- `POST /checkoutGuest`: public, rate limited. Uses the same atomic transaction for Guest, with a random persisted guestId and private 32-byte guestToken. A requestId is a UUID; replays must have the same owner, token and order inputs. Guest is not a Firebase Auth User. The app consumes Guest cart quantities once in a Room transaction after success, using a local receipt to make retries safe.
- `POST /lookupGuestOrder`: public, limited to 10 attempts per IP per 10-minute window. Requires the full server-generated order code and normalized receiver phone. Only Guest orders are returned. Correct lookup issues an order-scoped access token that expires after 24 hours.
- `POST /guestOrderDetails`: requires the private original Guest checkout token or the scoped lookup token; returns order/items/payment/shipment. Android polls every 15 seconds only while Order Detail is open. The endpoint never grants direct Firestore reads of Guest orders or private access collections.
- `POST /recordPostView`: legacy compatibility endpoint for historical PUBLISHED posts.
- `POST /recordCampaignView`: public, rate limited; increments only ACTIVE campaigns, atomically once per device/campaign/day. Campaigns own story content and engagement; clients cannot write viewCount directly.
- `POST /recalculate-rating`: Firebase ID token required; Customer only. Accepts an owned `reviewId`, or `productId/orderId/orderItemId` after deletion. The endpoint checks ownership and delivered-order eligibility before recalculating the published review average/count.
- `POST /send-notification`: Firebase ID token required. Order events are limited to an order's Customer/Seller participants and derive title/body from the current Firestore state. Seller promotion events fan out to customers who favorited that product; Admin campaign activation events fan out to customers who favorited products in its target batches. Other notification types require an Admin account and explicit Customer `userId`. It writes the inbox record and sends FCM to registered device tokens.
- `POST /notify-admins`: Firebase ID token required; accepts only verified Customer/Seller events: a report owned by the caller, a pending Seller application owned by the caller, or a pending Post/Campaign owned by the Seller. It fans out an idempotent inbox item and FCM to active Admin devices. Checkout also creates Admin order alerts after the order transaction commits.

## Free-tier behavior and event delivery

Render Free web services sleep after 15 minutes without incoming traffic. The first request after sleep is slow, and the server cannot promise always-on Firestore event processing. New-order Seller inbox records are created atomically with checkout; Seller and Admin FCM pushes are attempted after commit. Customer status notifications are requested by Order Detail after it observes an order/payment transition. Promotion notifications are requested after a Seller saves/activates a promotion; Campaign notifications are requested after Admin activates a campaign. Report, Seller application, and Post/Campaign submission alerts are requested after their Firestore writes succeed. If those apps are offline or the Render URL is not configured, those event requests can be missed. This is not equivalent to always-on Cloud Firestore triggers.

Firestore remains subject to Spark daily read/write/storage quotas. FCM itself is free, but sending must happen from this trusted service, never from a credential embedded in the APK.

## Coordinated upgrade and private data

Deploy the updated backend first and wait for its startup migration to finish. It moves any legacy bank fields from `sellerPublicProfiles` into owner-only `sellerPaymentProfiles` and records completion in `systemMigrations/sellerPaymentProfilesV1`; there is no manual data-edit step. Then publish the matching Firestore rules and distribute the new Android build. Older APKs that create orders directly will be blocked by the new rules. Configure appConfig/shipping and Seller GPS/bank details as described in docs/checkout-shipping-and-bank-setup.md before testing delivery/transfers.

The backend owns checkoutReceipts, guestOrderCodes, guestOrderGrants, postViewReceipts, campaignViewReceipts and requestLimits; the default Firestore deny rule keeps all of them private. These are technical receipts/access records, not new business domain classes. IPs, lookup phones and access secrets are stored as hashes. The limiter uses Express's socket IP by default, which may group users behind the same reverse proxy; do not trust arbitrary forwarded headers to bypass the limit. Expired request/view/grant receipts can be removed by a trusted maintenance task if volume requires it. Preserve checkoutReceipts and guestOrderCodes for idempotency and historical order access.

The Android backup configuration excludes Guest access preferences from cloud backups. A user on another device can still look up a Guest order with its code and phone.

## Regression tests

Install functions dependencies with npm ci. From tests/firestore, run npm ci and npm test. The suite starts an isolated demo-rescuefarm Firestore Emulator, then checks rules and the actual HTTP handlers sequentially. No production credentials are required. The commerce tests refuse to run without a localhost emulator address.
