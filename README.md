# RescueFarm

RescueFarm is an Android marketplace for rescuing agricultural products that are near expiry, overstocked, or otherwise need to be sold quickly. Customers and guests can discover products and rescue campaigns; approved sellers manage products, batches, campaigns and orders; administrators review accounts and content and monitor platform operations.

The product requirements and domain decisions are documented in [`final.md`](final.md). This README describes the current repository and how to build and operate it; it is not a substitute for that specification.

## Main capabilities

- **Guest and Customer:** browse products, categories, banners, campaigns and their community discussions; use a local guest cart or a signed-in Customer cart; checkout, follow orders, manage profile and addresses, favorite products, comment/react/report, and review eligible delivered purchases.
- **Seller:** apply for seller access; after approval, manage a storefront, products and expiring product batches, rescue campaigns and mobile rescue locations, promotions and orders/shipments. Sellers can use a read-only customer-facing view to inspect published content.
- **Admin:** review Seller applications, moderate products/campaigns/reviews, handle reports and user status, manage catalog content, inspect orders, view rescue analytics, and configure shipping fees.
- **Backend:** Firebase Authentication and Cloud Firestore are used for identity and application data. The Express service in `functions/` runs on Render and provides trusted checkout/quote, notification, review-summary, and Admin dashboard endpoints. The Cloudflare Worker in `cloudinary-worker/` signs Cloudinary media operations.
- **Offline support:** Room caches selected application data and WorkManager handles eligible background synchronization. Checkout and other critical inventory actions require the online backend.

## Technology

| Area | Implementation |
| --- | --- |
| Android | Java, XML Views, AndroidX, Material Components |
| App architecture | MVVM, Repository Pattern, LiveData |
| Local persistence | Room / SQLite, SharedPreferences for Guest session data |
| Authentication and online data | Firebase Authentication, Cloud Firestore |
| Push notifications | Firebase Cloud Messaging (sent by the trusted backend) |
| Images | Cloudinary via the `cloudinary-worker` Cloudflare Worker; Glide for loading |
| Location | Fused Location Provider, Android Geocoder, Haversine distance calculation |
| Background work | WorkManager |
| HTTP backend | Node.js 20+, Express, Firebase Admin SDK, hosted on Render |
| Firestore configuration | `firestore.rules` and `firestore.indexes.json` |

Firebase Cloud Functions, Firebase Storage, VNPAY, Google Maps SDK, live driver tracking, chat, AI, livestream, and auctions are not part of this repository's critical implementation path. Render Free services may sleep while idle; see [`functions/README.md`](functions/README.md) for operational limitations.

## Repository map

```text
app/                    Android application source, resources, and Room schemas
cloudinary-worker/      Cloudflare Worker for authenticated Cloudinary operations
docs/                   Deployment and feature/audit notes
functions/              Express + Firebase Admin backend deployed to Render
tests/firestore/        Firestore Emulator and backend regression tests
Function Diagram/       Actor-oriented functional diagrams and notes
Layout/                 UI reference layouts and design exports
OOP/                    OOP design documentation and diagrams
Khắc Tuấn/, Trương Đạt/ Team-owned class/function design notes and diagrams
Packages/               Workflow output location; APKs are published as Actions artifacts/releases
firestore.rules         Firestore authorization and data validation rules
firestore.indexes.json  Firestore composite indexes
final.md                Product, domain, use-case, and business-rule specification
```

## Requirements

- Android Studio with an Android SDK that includes API 37, and a JDK supported by the project's Gradle/Android Gradle Plugin (JDK 21 is used by CI).
- Node.js 24.12+ and Java 21+ to run the Firestore Emulator regression suite (see [`tests/firestore/README.md`](tests/firestore/README.md)).
- Firebase project configuration for an installable app connected to your environment.
- Optional deployment configuration for Render and Cloudinary if you need checkout, trusted notifications, and image upload in a live environment.

## Build the Android app

1. Clone the repository and open its root folder in Android Studio.
2. Add the Firebase Android configuration at `app/google-services.json`. This file is intentionally ignored by Git; obtain it from the Firebase project rather than committing it.
3. Set environment-specific Gradle properties in the user's `%USERPROFILE%\.gradle\gradle.properties` (or pass them as `-P` options):

   ```properties
   RENDER_BACKEND_URL=https://your-service.onrender.com
   CLOUDINARY_CLOUD_NAME=your-cloud-name
   CLOUDINARY_SIGNING_URL=https://your-worker.workers.dev
   ```

   These values configure client endpoints; never put a Firebase service-account key or Cloudinary API secret in Android, Gradle properties, or Git.
4. Sync Gradle, then build/install the `app` debug variant. From PowerShell:

   ```powershell
   .\gradlew.bat :app:assembleDebug
   .\gradlew.bat :app:installDebug
   ```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The `applicationId` currently used by Android is `com.example.nhom_7_mobileapp`; Firebase must be configured for that exact package name.

The GitHub workflow creates a debug-signed APK for development/demo installation. Release signing and Play Store publishing are not configured in this repository; do not treat the workflow APK as a production Play Store release.

## Backend and Firebase setup

- **Giải cứu mở rộng:** xem [hướng dẫn setup và vận hành ba phương thức](docs/rescue-programs-setup.md): hộp bí ẩn, định kỳ và giờ chót. Module có giao diện Guest/Customer/Seller/Admin, giữ tồn kho từ lô thật và dùng scheduler thông báo hiện có.

- **Render backend:** follow [`functions/README.md`](functions/README.md). Render needs `FIREBASE_SERVICE_ACCOUNT_JSON` as a server-side secret. Never add it to Android or commit it. Check the deployed service at `/health`.
- **Cloudinary uploads:** follow [`cloudinary-worker/README.md`](cloudinary-worker/README.md). Keep `CLOUDINARY_API_SECRET` in Cloudflare Worker secrets; configure only the Cloud name and Worker URL in Android.
- **Firestore rules and indexes:** from the repository root, deploy with an authenticated Firebase CLI:

  ```powershell
  firebase deploy --only "firestore" --project nhom7-rescuefarm
  ```

- **Shipping configuration:** an active Admin can configure `appConfig/shipping` in the Admin app. Seller GPS and bank details are managed separately; see [`docs/checkout-shipping-and-bank-setup.md`](docs/checkout-shipping-and-bank-setup.md).
- **Initial Admin:** create and verify a normal account, then use the guarded, trusted-workstation process in [`docs/admin-bootstrap.md`](docs/admin-bootstrap.md). Do not enable Admin self-registration.

The default Firebase project alias in `.firebaserc` is `nhom7-rescuefarm`. Confirm the selected Firebase project before deploying any rules or indexes.

## Regression checks

The Firestore suite uses an isolated Emulator project and includes rule and HTTP-handler checks. It does not validate the live Firebase project or device UI. Follow [`tests/firestore/README.md`](tests/firestore/README.md); the standard commands are:

```powershell
npm ci
npm --prefix ..\..\functions ci
npm test
```

Run the commands from `tests/firestore`. The Android package workflow builds the debug APK; it does not deploy Firebase rules, Render, or Cloudflare Worker.

## GitHub Android packages

`.github/workflows/android-package.yml` builds the debug APK on pushes to `main`, manual runs, and pull requests. Pull requests use a temporary Firebase config only to compile and do not publish an installable package. Push/manual runs require the repository Actions secret `GOOGLE_SERVICES_JSON` containing the Firebase Android JSON. Optional Actions variables `RENDER_BACKEND_URL`, `CLOUDINARY_CLOUD_NAME`, and `CLOUDINARY_SIGNING_URL` provide the corresponding build configuration.

Successful push/manual builds create a `Packages/` output directory and upload it as a GitHub Actions artifact named **Packages**. Version tags matching `v*` also attach the APK and SHA-256 checksum to a GitHub Release. Download artifacts from the workflow run's **Artifacts** section; download tagged builds from **Releases**. APK files are intentionally not committed to Git history. This uses GitHub Actions artifacts/releases rather than the GitHub Packages registry, which is designed for supported package registries rather than generic APK files. See [GitHub workflow artifacts](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts).

## Team

| Member | GitHub | Role |
| --- | --- | --- |
| Lại Thành Đạt | [LaiThanhDat-glitch](https://github.com/LaiThanhDat-glitch) | Team leader |
| Trương Thành Đạt | [datweb07](https://github.com/datweb07) | Member |
| Nguyễn Phương Chinh | [chinhngprit](https://github.com/chinhngprit) | Member |
| Phan Khắc Anh Tuấn | [KhacTuan1224](https://github.com/KhacTuan1224) | Member |
| Nguyễn Tấn Khiêm | [nguyentankhiem1610](https://github.com/nguyentankhiem1610) | Member |

## Project policies

- [MIT License](LICENSE)
- [Contributing](CONTRIBUTING.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
- [Security policy](SECURITY.md)
