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

- `POST /quoteCustomerCheckout`: Firebase ID token required; Customer only. Re-reads product, batch, promotion, campaign, seller and shipping configuration and returns a quote. Android checkout still performs its Firestore transaction and verifies the expected subtotal/shipping fee before creating an order.
- `POST /recalculate-rating`: Firebase ID token required; Customer only. Accepts an owned `reviewId`, or `productId/orderId/orderItemId` after deletion. The endpoint checks ownership and delivered-order eligibility before recalculating the published review average/count.
- `POST /send-notification`: Firebase ID token required. Order events are limited to an order's Customer/Seller participants and derive title/body from the current Firestore state. Seller promotion events fan out to customers who favorited that product; Admin campaign activation events fan out to customers who favorited products in its target batches. Other notification types require an Admin account and explicit Customer `userId`. It writes the inbox record and sends FCM to registered device tokens.

## Free-tier behavior and event delivery

Render Free web services sleep after 15 minutes without incoming traffic. The first request after sleep is slow, and the server cannot promise always-on Firestore event processing. Order notifications are requested by Order Detail after it observes an order/payment transition. Promotion notifications are requested after a Seller saves/activates a promotion; Campaign notifications are requested after Admin activates a campaign. If those apps are offline or the Render URL is not configured, the event request is missed. This is not equivalent to always-on Cloud Firestore triggers.

Firestore remains subject to Spark daily read/write/storage quotas. FCM itself is free, but sending must happen from this trusted service, never from a credential embedded in the APK.
