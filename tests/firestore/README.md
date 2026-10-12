# Firestore checkout regression tests

Uses the official Firestore Emulator and `@firebase/rules-unit-testing` with the
isolated `demo-rescuefarm` project. No production documents are read or written.
The test environment loads the repository's `firestore.rules`.

Requirements: Node.js supported by Firebase CLI and Java 21 or newer on PATH.

From this directory:

```powershell
npm ci
npm test
```

If the default Java installation is unavailable on Windows, use Android Studio's
bundled runtime (adjust the location if needed):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
npm test
```

Tests cover the missing-order idempotency read, private existing orders,
restricted promotion reads, DELIVERY/COD transactions without an applicable
promotion, repeat checkout without double reservation, and cross-customer
creation rejection. These checks validate emulator behavior, not deployment
status or live Android UI behavior.

To publish the reviewed rules, run from the repository root with an authorized
Firebase account:

```powershell
.\tests\firestore\node_modules\.bin\firebase.cmd login
.\tests\firestore\node_modules\.bin\firebase.cmd deploy --only firestore:rules --project nhom7-rescuefarm
```
