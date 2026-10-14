# Bootstrap the first Admin account

The Android app must never let a person choose the `ADMIN` role during signup. Create and verify a normal Customer account first, then run the guarded bootstrap script from a trusted workstation. The script promotes only an existing, active, email-verified `CUSTOMER`; it does not create users, unblock accounts, or change any other role. It records the operator, reason, project, and target in `adminBootstrapAudit`.

Keep the service-account JSON outside the repository. Do not commit it or put it in Android resources. From PowerShell, set the project and operator, load the key from a protected local file, and invoke the exact project-scoped command:

```powershell
Set-Location D:\NHOM_7_MobileApp\functions
$env:FIREBASE_PROJECT_ID = "your-firebase-project-id"
$env:ADMIN_BOOTSTRAP_OPERATOR = "your-name"
$env:FIREBASE_SERVICE_ACCOUNT_JSON = Get-Content -LiteralPath "C:\secure\service-account.json" -Raw
node .\scripts\bootstrap-admin.js --uid "AUTH_UID" --reason "Initial administrator for the RescueFarm project" --confirm
```

Use `--email` instead of `--uid` if needed, but never provide both. The script checks that the explicit project ID matches the service account, requires `--confirm`, and requires a reason of at least 10 characters. Confirm the target UID, verified email, project ID, and current `CUSTOMER` role in Firebase Console before running it. The script is idempotent for a user already marked `ADMIN` and does not rewrite their profile in that case.

The audit collection is readable by active Admins after promotion; direct client writes are denied by Firestore Rules. Revoke or rotate the service-account credential according to the team's secret-handling process after use.
