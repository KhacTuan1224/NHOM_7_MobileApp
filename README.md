> Read in other languages: [Tiếng Việt](README.vi.md)

# RescueFarm - Agricultural Rescue Android Application

<p align="center"><img width="160" alt="RescueFarm logo" src="app/src/main/res/drawable/logo.png" /></p>

## Project Description

RescueFarm is an Android e-commerce application connecting sellers with people who want to buy agricultural products that need timely consumption. It combines product discovery, batch-based inventory, rescue campaigns, ordering and community interactions in one mobile application.

The system serves **Guest, Customer, Seller and Admin** roles. Sellers publish products and manage their batches; customers discover suitable goods and place orders; administrators review accounts and content through the Android app. A rescue fund supports donations and stock-backed charitable deliveries.

This README describes the repository implementation and configuration, not a guarantee that every component is deployed in a particular environment. See [business requirements](final.md), [architecture reference](docs/diagram.png) and the [technology inventory with source evidence](docs/technology-stack.md).

[![Architecture diagram of laithanhdat-glitch/nhom_7_mobileapp](https://gitdiagram.com/laithanhdat-glitch/nhom_7_mobileapp/diagram.png)](https://gitdiagram.com/laithanhdat-glitch/nhom_7_mobileapp?utm_source=readme&utm_medium=picture)


## Key Features

### Client Features

- **Product Catalog** - Browse categories, product images, rescue prices and eligible batches.
- **Product Detail** - View descriptions, image galleries, seller information and batch choices. Expiry and available stock belong to a batch, not the product as a whole.
- **Search and Discovery** - Search loaded content by keyword and filter by category, price, location and sorting options; load more to extend results.
- **Shopping Cart** - Add products by batch, update quantities and remove items. Guest carts use Room; signed-in Customer carts use Firestore.
- **Checkout** - Revalidate price, shipping, batch expiry, stock and campaign allocations on the backend; use request IDs to prevent duplicate orders/reservations.
- **Payment Methods** - COD and configured bank-transfer QR flows, with SePay webhook verification for enabled sellers.
- **Order Management** - Follow order history and status, cancel where allowed and confirm receipt with ownership checks.
- **Favorites and Reviews** - Save products and submit reviews for eligible purchases.
- **Rescue Campaigns** - Read campaign details, progress and linked products; react, report and comment with text and up to three photos. Anonymous presentation uses a fixed name/avatar.
- **Rescue Map** - Open the Home map preview, inspect fixed/mobile points and follow fresh positions shared by a Seller's phone.
- **Flash Sale and Mystery Boxes** - Discover stock-backed pickup offers and manage claims. These offers use a separate claim flow and payment at pickup.
- **Rescue Fund** - Donate using a tracking code/QR and view fund activity according to role permissions.
- **Authentication** - Register, sign in with email/password or Google, reset passwords and continue as a Guest. Password login includes reCAPTCHA; Google sign-in and registration do not require that app-level CAPTCHA step.
- **Profile and Addresses** - Edit profile/avatar, manage delivery addresses and retain Home location selection in the appropriate local session scope.
- **Responsive and Offline Support** - Portrait/landscape layouts and selected Room-cached content. Checkout, payment and inventory mutations require online checks; cached URLs do not prove offline image availability.

### Seller Features

- **Seller Application** - Apply for Seller access and wait for Admin approval.
- **Storefront and Products** - Manage owned product information, images and storefront details.
- **Batch Inventory** - Manage prices, quantities and expiry dates without discarding transaction history.
- **Campaign Management** - Create campaigns tied to real batches and submit content for review.
- **Mobile Rescue Points** - Start/stop foreground GPS sharing for an eligible mobile campaign. Updates are throttled; viewers receive server events rather than one Firestore query per second.
- **Orders and Fulfillment** - Process owned orders and shipments through allowed state transitions.
- **Rescue Offers** - Manage Flash Sale/Mystery Box offers, preparation and pickup claims.
- **Charitable Deliveries** - Accept allocated fund deliveries and submit proof within the original 24-hour deadline.
- **Payment Profile** - Maintain private bank details and the configured QR/SePay connection.

### Admin Features

- **Mobile Dashboard** - Inspect operational summaries, orders and rescue statistics.
- **Account Management** - Review Seller applications and manage account status.
- **Content Moderation** - Review products, campaigns, reviews and reports with moderation metadata.
- **Catalog and Banners** - Manage categories, banners and their campaign links.
- **Order Oversight** - Inspect orders and reconciliation information through authorized screens/APIs.
- **Shipping Configuration** - Manage the shared shipping-fee configuration.
- **Rescue Offer Management** - Oversee stock-backed rescue offers.
- **Fund Configuration and Verification** - Configure budgets/recipient information and verify delivery evidence.
- **Fund Payout Records** - Record the correct amount, reference and proof after transferring money outside the app. SePay verifies incoming donations; it does not automatically pay sellers out.

Admin functions are implemented in the Android application. There is no separate web-admin installation or default Admin password.

## Technologies Used

<img alt="fpt-shop-banner" src="https://github.com/LaiThanhDat-glitch/NHOM_7_MobileApp/blob/main/images/technology.png" />

| Layer | Technology |
|---|---|
| Frontend / Android | Java, XML Views, Android SDK, AndroidX, Material Components, ConstraintLayout, RecyclerView, ViewPager2, Navigation Component |
| App Architecture | MVVM, Repository Pattern, ViewModel, LiveData, SavedStateHandle |
| Embedded Web Content | Android WebView, HTML, CSS, JavaScript; Leaflet 1.9.4 and reCAPTCHA widget integration |
| Backend | Node.js, Express 4, Firebase Admin SDK, REST/HTTPS APIs, Server-Sent Events, Node Crypto and native Fetch |
| Database | Cloud Firestore; Room over SQLite for selected local data |
| Caching and Local State | Room entities/DAOs, SharedPreferences, Firestore SDK cache, bounded in-process Map/TTL caches and shared listeners |
| Authentication and Security | Firebase Authentication, Google Credential Manager/Google ID, App Check with Play Integrity, Firestore Rules, reCAPTCHA v2, server authorization, signed media requests and webhook verification |
| Images | Cloudinary REST API/CDN, Cloudflare Worker signing/upload coordination, Glide |
| Location and Maps | Google Play services Fused Location Provider, Android Geocoder, Haversine distance, Leaflet, OpenStreetMap tiles, external map intents |
| Payment | VietQR generation/QR images, SePay webhooks, COD and pickup cash flows |
| Notifications and Background Work | Firebase Cloud Messaging, WorkManager, foreground location service, backend reconciliation/outbox workers |
| DevOps and Testing | Git, GitHub, GitHub Actions, Render, Cloudflare Workers/Wrangler, Gradle Kotlin DSL, JUnit 4, AndroidX Test, Espresso, Room Testing, Node Test Runner, Firebase Emulator Suite and Rules Unit Testing |
| Dataset and Tooling | Historical `daohoangson/dvhcvn` JSON snapshot bundled as offline TSV; Python maintenance/audit scripts and PowerShell helpers |
| Environment Configuration | Gradle properties / BuildConfig for public client settings; Node `process.env`, Render variables, Worker vars/secrets and GitHub Actions variables/secrets |

Application source is Java; `.kts` files configure Gradle rather than implementing a Kotlin UI. The backend is hosted on Render, not Firebase Cloud Functions. Cloudinary integration uses REST/native Fetch rather than an installed Cloudinary SDK. Full source mapping and versions are in [technology-stack.md](docs/technology-stack.md).

## Installation & Setup

### Prerequisites

- **Android Studio and SDK** - API 37 for compilation; the application supports Android API 29+.
- **Java** - JDK 21 is selected in CI; use a JDK compatible with the repository's Gradle/Android Gradle Plugin configuration.
- **Node.js and npm** - Node 24.12+ for the complete Emulator test suite. The backend itself declares Node >=20.
- **Firebase CLI and a Firebase project** - Authentication, Firestore, Rules/indexes and appropriate App Check configuration.
- **Service configuration** - Render for the HTTP backend, Cloudflare Worker/Cloudinary for media, and provider credentials for optional payment/CAPTCHA integrations.

### Step 1: Clone the Repository

```bash
git clone https://github.com/datweb07/NHOM_7_MobileApp.git
cd NHOM_7_MobileApp
```

### Step 2: Install Dependencies

```bash
npm --prefix functions ci
npm --prefix tests/firestore ci
```

Open the repository in Android Studio and sync Gradle. Android dependencies are declared in [app/build.gradle.kts](app/build.gradle.kts) and [gradle/libs.versions.toml](gradle/libs.versions.toml). Worker tests use Node's built-in test runner; Worker deployment uses the Wrangler CLI.

### Step 3: Configure Environment Variables

Add public Android settings to your user Gradle properties (`%USERPROFILE%\.gradle\gradle.properties` on Windows), or pass them with `-P`:

```properties
RENDER_BACKEND_URL=https://your-service.onrender.com
CLOUDINARY_CLOUD_NAME=your-cloud-name
CLOUDINARY_SIGNING_URL=https://your-worker.workers.dev
```

Configure server values through Render Environment or your terminal process environment. The backend reads `process.env`; it does not load a root `.env` through dotenv.

| Variable | Location and purpose |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Render/server secret containing the Admin SDK credential |
| `PORT` | Backend listening port; defaults to 3000 |
| `MEDIA_WORKER_SECRET` | Matching private value on Render and Worker |
| `NOTIFICATION_WORKER_SECRET` | Matching Render/Actions secret for worker wakeups |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_SIGNING_URL` | Backend media/QR integration where required |
| `PASSWORD_RECAPTCHA_SITE_KEY`, `PASSWORD_RECAPTCHA_SECRET_KEY`, `PASSWORD_RECAPTCHA_HOSTNAMES` | Password-login CAPTCHA configuration |
| `SEPAY_MERCHANTS_JSON` | Private Seller-to-bank/webhook configuration |
| `VIETQR_CLIENT_ID`, `VIETQR_API_KEY` | Optional backend VietQR generation |
| `RESCUE_FUND_SELLER_ID` | Fund bank-account selection when sharing an enabled Seller's connection |

Keep service accounts, signing secrets, bank configuration and real customer data outside Git/APK. Configuration examples use placeholders, not default credentials.

### Step 4: Setup Database and Firebase

1. Register the Android app with the exact package **`com.example.nhom_7_mobileapp`**.
2. Download its configuration to **`app/google-services.json`**, which is ignored by Git.
3. Enable the authentication providers you intend to use and create Firestore.
4. Review and deploy the repository's [Rules](firestore.rules) and [indexes](firestore.indexes.json) to your selected project:

   ```bash
   firebase deploy --only firestore --project YOUR_FIREBASE_PROJECT_ID
   ```

The default alias is recorded in `.firebaserc`; explicitly select your project rather than assuming that alias is your environment. Firestore uses collections/documents, not a MySQL schema import. Room creates/migrates the local SQLite database from application code.

### Step 5: Configure Cloudinary Image Storage

Set the Worker variables `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `FIREBASE_PROJECT_ID` and `MEDIA_BACKEND_URL` for your environment. From `cloudinary-worker`, store private values and deploy using Wrangler:

```bash
npx wrangler secret put CLOUDINARY_API_SECRET
npx wrangler secret put MEDIA_WORKER_SECRET
npx wrangler deploy
```

Use the same `MEDIA_WORKER_SECRET` on Render and set the resulting Worker URL in Android. Campaign media uses an authorization/upload/commit lifecycle; deploy compatible backend, Worker, Rules and APK versions. Follow [media setup](docs/a13-a17-implementation.md) and the [Worker guide](cloudinary-worker/README.md).

### Step 6: Configure Email Service

Password-reset email is sent by **Firebase Authentication**. Configure its email templates and authorized domains in your Firebase environment. This repository does not require PHPMailer, Gmail SMTP or an SMTP password. See [password-reset policy](app/src/main/java/com/rescuefarm/service/auth/PasswordResetPolicy.java).

### Step 7: Configure Google Login Service

Enable Google sign-in in Firebase Authentication, register the signing certificate fingerprints for the Android build, and download the updated `google-services.json`. The generated `default_web_client_id` is used by Credential Manager to request a Google ID token, which Firebase Auth exchanges for a session. Rebuild after configuration changes; see [GoogleCredentialService](app/src/main/java/com/rescuefarm/service/auth/GoogleCredentialService.java).

### Step 8: Configure Google reCAPTCHA v2

Create a **web v2 checkbox** key for the backend hostname. Set `PASSWORD_RECAPTCHA_SITE_KEY`, `PASSWORD_RECAPTCHA_SECRET_KEY` and `PASSWORD_RECAPTCHA_HOSTNAMES` on Render. The app loads `/auth/password-captcha` and verifies responses via `/auth/password-captcha/verify` before password sign-in.

The horizontal widget requires the backend version with `size: normal` and the matching APK. This is an app-level password-login gate, not global Identity Platform enforcement. Google sign-in and registration keep their existing flows. Follow the [complete CAPTCHA guide](docs/password-login-recaptcha.md).

### Step 9: Configure QR Payments and SePay

An approved Seller configures their private payment profile. If using automatic bank verification, connect the bank account to SePay and configure the matching Seller webhook and private merchant settings on Render. The backend verifies incoming-transfer authenticity, reference, account and amount and prevents duplicate recording.

Optional VietQR API credentials support QR generation. Do not substitute a manual payment-confirmation button for the configured SePay flow. Follow [SePay setup](docs/sepay-setup.md) and [shipping/bank configuration](docs/checkout-shipping-and-bank-setup.md).

### Step 10: Configure Shipping, Rescue Fund and Notifications

An Admin configures shipping in the mobile app. For the fund, configure its bank connection, budgets and recipient information using the [fund guide](docs/rescue-fund.md) and [shared-bank guide](docs/rescue-fund-shared-bank.md).

New fund deliveries allow 24 hours from allocation to submission of delivery proof. Verification and an external bank transfer/payout record are separate actions. GitHub Actions worker wakeups use `RENDER_BACKEND_URL` and `NOTIFICATION_WORKER_SECRET`; scheduled runs/push delivery are not guaranteed to be instantaneous.

### Step 11: Run the Backend

With the required server environment configured for the intended Firebase project:

```bash
npm --prefix functions start
```

Check `/health` on the backend. The [Render blueprint](functions/render.yaml) specifies `functions` as the root directory, `npm install` as the build command and `npm start` as the start command. See [backend operations](functions/README.md) for startup migrations, optional integrations and hosting limits.

### Step 12: Build and Access the Application

From PowerShell at the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Select the intended test device/emulator in Android Studio to run it. Guest discovery and role-based Customer/Seller/Admin screens are all inside this Android app; there is no `/admin` website to open.

### Step 13: Run Development Checks

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
npm --prefix functions run check
npm --prefix functions run test:unit
npm --prefix tests/firestore test
python scripts/check_navigation.py
```

The Firestore command starts an isolated `demo-rescuefarm` Emulator project. See [Emulator instructions](tests/firestore/README.md) and [the demo build workflow](docs/report/source/README.md) before device tests; select instrumentation classes explicitly and isolate Android's Firebase **and** HTTP backend connections.

The Android [package workflow](.github/workflows/android-package.yml) builds debug APKs, uploads a **Packages** artifact and attaches APK/checksum files for `v*` releases. It requires the `GOOGLE_SERVICES_JSON` Actions secret for usable push/manual builds and public endpoint variables. It does not deploy Render, Worker or Firestore configuration. **Current CI mismatch:** the workflow selects Node 22, but the locked Rules Unit Testing package requires >=24.12; align that runtime before relying on its Emulator step. Debug artifacts are not Play Store-signed releases.

### Default Account Credentials
#### Admin
- **Email**: admin@gmail.com
- **Password**: admin123

#### Seller
- **Email**: seller@gmail.com
- **Password**: seller123

#### Customer
- **Email**: customer@gmail.com
- **Password**: customer123

## Development Team

| Member | GitHub | Role |
|---|---|---|
| Lại Thành Đạt | [LaiThanhDat-glitch](https://github.com/LaiThanhDat-glitch) | Team leader |
| Trương Thành Đạt | [datweb07](https://github.com/datweb07) | Member |
| Phan Khắc Anh Tuấn | [KhacTuan1224](https://github.com/KhacTuan1224) | Member |
| Nguyễn Phương Chinh | [chinhngprit](https://github.com/chinhngprit) | Member |
| Nguyễn Tấn Khiêm | [nguyentankhiem1610](https://github.com/nguyentankhiem1610) | Member |

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), the [Code of Conduct](CODE_OF_CONDUCT.md) and the [security policy](SECURITY.md). Keep changes focused and preserve authorization, transaction/idempotency rules and secret handling. Include checks appropriate to the change.

## License

Project code is released under the **MIT License**; see [LICENSE](LICENSE).

### Third-Party Library Licenses

Third-party components retain their own licenses. The repository includes [Leaflet's BSD 2-Clause license](app/src/main/assets/maps/leaflet/LICENSE) and the [GPL-3.0 license for the bundled dvhcvn dataset](app/src/main/assets/address/LICENSE.dvhcvn). The address dataset is not relicensed under the project's MIT license. Other dependency/service terms remain with their respective maintainers; this README is not a complete dependency-license audit.

Report files and past audits are snapshots; check their dates/commits before using them as evidence of the current app.
