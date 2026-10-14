# Firestore checkout regression tests

Uses the official Firestore Emulator and `@firebase/rules-unit-testing` with the
isolated `demo-rescuefarm` project. No production documents are read or written.
The test environment loads the repository's `firestore.rules`.

Requirements: Node.js 24.12 or newer and Java 21 or newer on PATH. The pinned
Firebase SDK/rules-test packages require Node 24.12 or newer.

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

Tests cover missing-order reads, private orders, promotion fallbacks, fixed
shipping fees, atomic cart cleanup, replay without double reservation, review
creation/edit/withdrawal and summary updates, admin review moderation, public
seller settings, and notifications tied to order transitions. These checks
validate emulator behavior, not deployment status or live Android UI behavior.

To publish the reviewed rules, run from the repository root with an authorized
Firebase account:

```powershell
.\tests\firestore\node_modules\.bin\firebase.cmd login
.\tests\firestore\node_modules\.bin\firebase.cmd deploy --only firestore:rules --project nhom7-rescuefarm
```
