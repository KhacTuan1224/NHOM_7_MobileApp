# Security Policy

## Supported versions

Security fixes are applied to the latest code on the repository's `main`
branch. Older APKs and branches may not receive fixes; update to a build made
from the latest reviewed source.

## Reporting a vulnerability

Please do not report exploitable vulnerabilities in a public issue, pull
request, discussion, or screenshot. Use GitHub's **Report a vulnerability**
option in the repository's **Security** tab to send a private report to the
maintainers. Include the affected component, impact, reproduction steps, and
any safe proof of concept. Do not include real user data or live credentials.

If private vulnerability reporting is unavailable, contact a repository
maintainer privately through a contact method listed on the repository page
and ask for a secure channel before sending technical details.

Maintainers will acknowledge reports, investigate them, and coordinate a fix
and disclosure timeline with the reporter. Please allow time for a fix before
public disclosure.

## Credential handling

Never commit Firebase service-account JSON, `app/google-services.json`,
Cloudinary API secrets, Render secrets, signing keys, access tokens, or
production data. If a credential is exposed, revoke or rotate it immediately
at its provider, then notify maintainers privately. Removing the file in a
later commit does not revoke a leaked credential.
