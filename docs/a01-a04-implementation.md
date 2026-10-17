# A01–A04: Seller statistics and campaign allocations

## Implemented contracts

- **A01:** `sellerStatisticsFragment` now opens a real Fragment + ViewModel. Periods: 7/30/90 days and all time. The API paginates every Seller order/campaign instead of relying on the 50-row UI cache. Charts show revenue by actual delivery date and counts in the selected period. Products/reasons and rescued quantities stay separated by unit. Revenue excludes shipping. Completion rate is the average of individual campaign percentages for campaigns overlapping the selected period; its cumulative time basis is stated in the UI. Loading, error, retry, empty data, refresh and stale-response protection are implemented.
- **A02:** submitting a draft no longer writes `activeCampaignId`. Approval links batches atomically. Rejection, stopping, completion and expiration unlink only batches whose current ID still equals the closing campaign. Closing never releases stock reserved by existing orders. Old orders retain their original campaign and update only its allocations, even when the batch belongs to a newer campaign.
- **A03:** Android calls the trusted `POST /campaigns/moderate` API. Approval re-reads the campaign, Seller, products, batches and conflicting campaigns in one transaction. It rejects expired campaigns/batches, invalid totals, insufficient stock, inactive/unapproved Sellers, wrong ownership and live conflicts. Direct client approval is denied by Rules. Existing report moderation may still stop an active campaign and unlink its batches atomically.
- **A04:** `reservedByBatch`, `rescuedByBatch`, `allocationVersion: 1` live in the Campaign document. Orders persist `campaignBatchReservations`. Checkout checks `target[batch] - reserved[batch] - rescued[batch]` and updates both batch and campaign counters in one transaction. Cancellation returns the same batch allocation; delivery moves reserved to rescued. Duplicate requests/transitions do not mutate quantities twice. Status transitions now use `POST /orders/transition`; client writes cannot bypass these counters.

No bottom navigation UI changes are included.

## Data and compatibility

Room version 8 adds cached allocation maps and a version flag, with migration 7 → 8. The campaign domain model and cache mapper preserve these fields. Campaign detail validates the selected batch's remaining allocation before adding to cart; checkout always validates current backend data again.

Legacy Campaigns without `allocationVersion: 1` are reconstructed from authoritative order reservations, including delivered orders. Migration succeeds only when the reconstructed per-batch totals agree with the old aggregate counters and targets. Ambiguous or already over-allocated records return HTTP 409 with an explanation and log `Campaign reconciliation requires attention`. They require a manual inventory/order reconciliation; the backend does not invent allocations or reset existing orders.

The backend repairs legacy pending/closed links at startup, expires active campaigns every minute while the process runs, and repairs linked expired campaigns before quote/checkout. The Seller submit path also tolerates obsolete links after re-reading the linked campaign. If a free Render instance sleeps, the timer pauses; quote/checkout resumes validation and cleanup on the next request. This uses the existing Node backend, with no Firebase scheduled Cloud Function or paid scheduler requirement. Hosting and Firestore quotas still apply.

## Rollout order

1. Keep a backup/export of existing inventory, Campaigns and orders. Run the tests below on a workstation/CI with functioning Java networking.
2. Deploy `firestore.indexes.json` and wait for the new `campaigns(status ASC, endDate ASC)` index to finish building: `firebase deploy --only firestore:indexes --project nhom7-rescuefarm`.
3. Deploy/restart the existing Render service with the updated `functions/` code and existing Firebase Admin environment settings. Endpoints added: `/seller/statistics`, `/campaigns/moderate`, `/orders/transition`. Verify health and startup reconciliation logs; resolve flagged legacy inconsistencies before processing affected orders.
4. Build the Android app with the same `RENDER_BACKEND_URL` used for checkout and install/distribute the new APK.
5. Deploy `firestore.rules`: `firebase deploy --only firestore:rules --project nhom7-rescuefarm`. Coordinate this cutover with the APK: old APKs that directly approve campaigns or transition orders will be denied after these Rules are deployed.

These production deployment commands are instructions; implementation work did not execute them or alter the live database.

## Checks

- `python scripts/check_navigation.py`: checks that every Fragment destination in navigation XML exists; included in CI.
- `npm --prefix functions run check`: backend syntax.
- `npm --prefix functions run test:unit`: deterministic transaction harness + full-pagination/statistics checks. The harness serializes transactions and verifies reads-before-writes; it does not replace actual Firestore concurrency tests.
- `cd tests/firestore; npm test`: isolated Firestore Emulator project `demo-rescuefarm`, real transactions, concurrent allocation checkout, rollback, cancel, Guest lifecycle, direct-client denial and notification privacy. Test guards reject production targets.
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: Android tests/build, including batch allocation/cache round trips. CI now runs the tests before packaging.

On this Windows workstation, Gradle and the Firestore Emulator cannot start because Java fails `Unable to establish loopback connection` / `Invalid argument: connect` (also reproduced with Android Studio's JBR). Their local runs are **not passed**. The Node harness and navigation check run independently; CI must confirm the Android build and real Emulator suite.

Additional local checks succeeded using the installed JBR directly: all 299 main Java sources type-check against cached Android dependencies; Room's actual annotation processor generates schema 8 and database implementations; 15 focused JUnit tests cover the campaign domain, allocation/cache round trip and Statistics ViewModel. These checks do not package an APK or execute the Android UI.

## Acceptance on APK + Emulator

1. Seller drawer → Statistics → change every period → refresh → Back. Verify empty/error states and that more than 50 orders count correctly.
2. Submit a campaign: its batch can still be sold ordinarily. Reduce stock or block the Seller before approval: approval must fail with no partial ACTIVE/link write.
3. Allocate A=10, B=90 with A having 50 in stock. Two simultaneous 10-unit checkouts for A must yield one success and one conflict. Cancel the winner: A becomes available again. Deliver it: A remains exhausted within this campaign while B is still available.
4. Complete the target before the batch is sold out: the residual stock becomes ordinary sellable stock. Repeat for reject/stop/expire.
5. Stop/expire an old campaign with a pending order; approve a new campaign on the residual stock; finish/cancel the old order. The new campaign link and counters must remain unchanged.
6. Try direct Firestore ACTIVE/order status writes from a client: denied. Try invalid Seller/batch/product/time/ownership inputs through the moderation API: no partial writes.
