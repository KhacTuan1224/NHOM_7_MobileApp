# Customer code changes

The app keeps the 25 domain models specified in `final.md`. `SellerStorefront`
is a data projection of `sellerPublicProfiles`, not an additional domain entity.

Implemented:

- Review existence checks work for first submissions. Review and product summary
  changes are atomic for create, edit, withdrawal, and admin moderation.
- Withdrawal retains the review record with `DELETED` status. Only published
  reviews contribute to rating sum, count and average.
- Checkout removes only purchased quantities from the customer cart in the same
  transaction that reserves inventory. A replay does not consume the cart twice.
- Each seller configures a fixed `shippingFee` in their public profile. Pickup
  and on-site rescue cost zero shipping. Delivery no longer requires the global
  `appConfig/shipping` document or GPS coordinates to price shipping.
- Seller profile includes a button for fixed shipping and an optional QR image.
  Settings use `sellerPublicProfiles`; existing avatar, coordinates and private
  bank settings are preserved/synchronized. Ordinary profile saves merge rather
  than erase shipping and QR fields.
- Bank transfer retains bank details and optional QR URL in the payment record.
  Older payments without a seller QR retain the existing generated QR fallback.
- Campaign sharing uses the Android share sheet.
- Order creation and status changes create private in-app notifications in the
  same transaction. Notifications use deterministic IDs and the list listens
  for changes. Rules reject notifications forged outside a real transition.
- Checkout, review, public seller profile, and Home no longer call Firestore
  directly; data access goes through repositories and ViewModels.

External work deliberately deferred:

- Publish the changed `firestore.rules` before testing the new app against live
  Firebase. Old rules will reject the new fields/atomic operations.
- Deploy the updated Cloudinary Worker to enable the new `paymentQr` upload
  type. The app can also accept an existing HTTPS QR image URL.
- Push notification delivery needs a trusted server/FCM sender and credentials.
  This change generates in-app notifications; it does not send FCM from Android.
- Existing public seller profiles need an explicit fixed `shippingFee`, which
  can be set to zero in the app. Missing fees disable delivery rather than imply
  free shipping.

No external deployment or account configuration is performed by these changes.
