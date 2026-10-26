# Contributing to RescueFarm

Thanks for helping improve RescueFarm. Changes should follow the product and
business rules in [`final.md`](final.md), protect role boundaries, and avoid
adding credentials or real user data to the repository.

## Before you start

- Check existing issues and pull requests for related work.
- For a substantial behavior or schema change, open an issue or discuss the
  design with the maintainers before implementation.
- Keep changes focused. Update documentation, Firestore rules/indexes, and
  tests together when a change affects their behavior.
- Do not change Firebase project IDs, production endpoints, package IDs, or
  secret handling as an incidental cleanup.

## Development setup

Follow the setup instructions in [`README.md`](README.md). Never commit
`app/google-services.json`, service-account credentials, `.env` files,
keystores, Cloudinary API secrets, user exports, or generated APKs.

## Change guidelines

- Android code uses Java, XML Views, MVVM, and repository abstractions.
- Keep business validation in reusable domain/service/repository code; the UI
  should present state and collect input, not become the security boundary.
- Treat Firestore Rules and the trusted Render API as security boundaries.
  Client-side validation is not sufficient for inventory, payment, ownership,
  moderation, or role checks.
- Preserve idempotency and transactional inventory behavior for checkout and
  order lifecycle changes.
- Keep Guest access separate from authenticated Customer data.
- Follow the existing image ownership scheme through the Cloudinary Worker;
  never expose Cloudinary signing credentials in the app.
- Update `firestore.indexes.json` when a Firestore query requires a composite
  index, and explain any required deployment step.
- Keep interfaces accessible, provide loading/empty/error states, and add
  Vietnamese strings to Android resources rather than hard-coding user-facing
  text where practical.

## Validation

Run the checks relevant to your change and include their results in the pull request:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
npm --prefix functions run check
python scripts/check_navigation.py
```

Automated test suites were removed during repository cleanup. These commands check build/lint, JavaScript syntax and navigation; they do not replace behavioral regression tests. See [cleanup details](docs/repository-cleanup.md). Validate changed flows in an isolated environment.

## Pull request process

1. Create a topic branch from `main`, for example `feature/shipping-settings`
   or `fix/order-status-filter`.
2. Make the smallest complete change and update relevant docs/tests.
3. Run appropriate checks and review your diff for secrets, generated files,
   and unrelated changes.
4. Open a pull request with the problem, implementation, validation results,
   screenshots for visible UI changes, and any Firebase/Render/Cloudflare
   deployment steps.
5. Respond to review feedback respectfully and keep the PR up to date.

By submitting a contribution, you agree that it is provided under the
repository's [MIT License](LICENSE). Please follow the
[Code of Conduct](CODE_OF_CONDUCT.md).
