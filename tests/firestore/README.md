# Firestore checkout regression tests

Uses the official Firestore Emulator and `@firebase/rules-unit-testing` with the
isolated `demo-rescuefarm` project. No production documents are read or written.
The test environment loads the repository's `firestore.rules`.

Requirements: Node.js 24.12 or newer and Java 21 or newer on PATH. The pinned
Firebase SDK/rules-test packages require Node 24.12 or newer.

From this directory:

```powershell
npm ci
npm --prefix ../../functions ci
npm test
```

If the default Java installation is unavailable on Windows, use Android Studio's
bundled runtime (adjust the location if needed):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
npm test
```

Tests cover denied direct client checkout, atomic server checkout and cart cleanup,
concurrent buyers, idempotent replay, rounded fractional quantities, all Guest
fulfillment/payment paths, lookup/access-token isolation and rate limits,
Seller completion/cancellation of Guest orders, archival of historical batches,
owned comment edits/soft deletion, deduplicated public post views, reviews and
private notifications. Seller profile regression checks cover atomic profile/payment
saves before and after approval, preservation of moderation metadata, shipping fees
and QR configuration, private bank details, and denied edits by other or disabled
accounts. Owners cannot forge approval or add, change, or remove moderation fields.
Tests run sequentially because they share one isolated
emulator project. These checks
validate emulator behavior, not deployment status or live Android UI behavior.

To publish the reviewed rules, run from the repository root with an authorized
Firebase account:

```powershell
.\tests\firestore\node_modules\.bin\firebase.cmd login
.\tests\firestore\node_modules\.bin\firebase.cmd deploy --only firestore:rules --project nhom7-rescuefarm
```
