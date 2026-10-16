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

Run the checks relevant to your change and include their results in the pull
request. The isolated Firestore and backend regression suite is documented in
[`tests/firestore/README.md`](tests/firestore/README.md). From
`tests/firestore`, it can be run with:

```powershell
npm ci
npm --prefix ..\..\functions ci
npm test
```

For an Android change, build the app with `.gradlew.bat :app:assembleDebug`.
For the Render service, run `npm run check` from `functions`. Do not point
tests at a production Firebase project.

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
