# BeAqua APK readiness audit

Date: 2026-09-20

## Verdict

The app can be built for controlled testing. It is **not ready for public distribution with real customer data or payments**. Compiling successfully does not establish that authentication, database access, scheduled work, or all device flows are safe and reliable.

This audit reviewed the current uncommitted workspace, including the cart and recurring-delivery changes. It did not deploy anything or change live database records.

## Verification

- Debug APK build and release APK build pass after the fixes below.
- All 33 existing JVM unit tests pass. These cover recurrence dates, finalization cutoffs, ETA settings, expiry, order IDs, password hashing, membership and KYC logic; they do not exercise real checkout transactions or account access control.
- Android lint: all three original errors fixed. Remaining warnings include hardcoded text, translation formatting, missing accessibility descriptions, old APIs and complex layouts. Full results: `app/build/reports/lint-results-debug.html`.
- Release output: `app/build/outputs/apk/release/app-release-unsigned.apk`. The Gradle release build has no signing configuration; this is not the APK to distribute directly.
- Debug output: `app/build/outputs/apk/debug/app-debug.apk`, for controlled testing only.
- No emulator or phone was connected (`adb devices -l` returned an empty list). No installed-app walkthrough, crash reproduction, camera/location/notification permission test, or multi-device test was possible.
- Live Firebase rules, indexes, quotas, authentication configuration and network failure behavior were not verified. No 20–50-user load test was performed.

## Fixed during this audit

1. **Release resource compilation failure.** The water-jug asset was WebP data with a `.png` filename. Renamed it to `.webp`, preserving its Android resource name.
2. **Duplicate checkout race.** Checkout now reads the cart documents inside the order transaction and checks ownership, contents and positive quantities before writing orders. A concurrent checkout that deletes the cart causes the other transaction to retry and fail rather than create another order. Needs a two-client integration test.
3. **Stale checkout prices and fees.** Product ownership, current prices and station fees are validated inside the transaction. Changed prices/fees require reviewing the cart. Product validation also updates the displayed product prices.
4. **Changing cart during validation.** Added guards against starting another check or removing items during checkout, plus snapshot comparison for removals already in flight. This avoids indexing a changed list against older query results.
5. **Stale rush-order state.** Reloading a station with rush ordering disabled now clears the previous rush flag/fee.
6. **Customer signup race.** Creating a customer now uses a transaction that refuses an existing username, instead of overwriting it after a separate availability check.
7. **Leaked order listeners.** Customer and station-owner order listeners are retained and removed on activity destruction; the customer callback also handles a null snapshot.
8. **KYC gesture navigation.** Replaced the old back-button override with AndroidX's back dispatcher, fixing two lint errors.
9. **Android 7 style compatibility.** Replaced the API-26-only vertical-padding style item with top and bottom padding, fixing the third lint error.
10. **Resumed recurring schedule displayed an old date.** The list now reloads the saved schedule after resuming, including any date advanced by the transaction.

## Remaining findings, in priority order

### Critical: account authentication and administrator authorization

- `LoginActivity.kt` compares a customer-entered password directly with `User.password` downloaded from Firestore. `SignupActivity.kt` stores the customer password directly, and `UserProfileActivity.kt` loads it into the password field.
- `AdminCredentialStore.kt` contains a shared default administrator password and stores password changes only on the local device. A reinstall or another installation has independent administrator credentials. This is not a centrally authenticated administrator role.
- The active login path does not establish a Firebase Authentication identity. Database writes are addressed by usernames supplied by the client. The deployed rules were not inspected, so this audit does **not** claim that the live database is publicly writable; it does establish that the app has no verified per-user Firebase authorization flow.
- Resolve with centrally authenticated users and server-enforced customer/station/admin permissions, including ownership checks on orders, carts, messages and KYC documents. Migrating credentials is a separate change requiring compatibility planning; merely hashing customer passwords on the phone would not fix authorization.

### High: recurring deliveries and reminders depend on phones

Performance follow-up (2026-09-20): home-screen maintenance now runs in a delayed background job scoped to the signed-in customer or station. Distant future schedules are skipped before transaction reads, and schedules still before cutoff no longer fetch product/customer data. The original whole-database scans described below were identified during the audit and have since been narrowed. Trusted backend scheduling and server-time validation are still outstanding.

- `BeAquaApplication.kt` schedules order maintenance on every installation. `FirebaseHelper.processDueSubscriptions()` scans active subscriptions; the order worker also scans pending orders for expiry. More installations can multiply the same reads and competing work.
- `DeliveryReminderWorker.kt` runs on a customer's phone and requires connectivity and Android background execution. Force-stopped/offline devices cannot guarantee day-before reminders.
- Cutoff checks use the client's clock. The transaction protects against concurrent document changes, but does not establish a trusted server time or prohibit older APKs from using older logic.
- Move order finalization/expiry/reminder generation to trusted scheduled backend work, with push delivery and server validation. Existing app-side transaction fixes do not replace this.

### High: concurrent order status changes are unguarded

- `FirebaseHelper.updateOrderStatus()` performs a direct update without checking the current state. A stale owner screen can overwrite an automatic cancellation, or a delayed status action can overwrite a newer state.
- Define allowed transitions and enforce them atomically with expiry/payment checks on the backend. Verify with simultaneous owner/customer/expiry operations.

### High: build credential in source control

- `settings.gradle.kts` includes a literal Mapbox secret download token. The token is intentionally not reproduced here. Move the credential to an untracked Gradle property or environment setting and rotate the exposed token. Rotation was not attempted during this audit.

### Medium: recurring-delivery edge cases

- Hours currently represent one daily schedule, not weekday-specific hours or holiday closures. `DeliveryFinalization` uses the saved opening time; manual open/closed overrides do not change its cutoff.
- Resuming updates the next delivery date, but editing an already overdue paused schedule can still be rejected until it is resumed.
- A schedule processed after a long offline period can create the oldest due delivery and then skip the intervening dates. A policy for missed deliveries needs to be agreed and tested before relying on automation.

### Medium: stale screens and short order identifiers

- Some lists use one-time reads. New notifications do not guarantee that an already-open station order list, product list or recurring-delivery list has refreshed.
- `OrderIdGenerator.kt` uses only eight hexadecimal random characters in the document ID. Order creation uses `set` without a collision check. The risk grows as one station accumulates orders. Keep full unique IDs for storage and use a shorter customer-facing reference separately, or check uniqueness transactionally.

### Medium: transport, signing and device coverage

- `network_security_config.xml` permits HTTP globally; uploaded image URLs can use HTTP. Restrict production traffic to HTTPS after validating existing stored URLs.
- Release signing and signing-key backup are not configured. Maintain the same release key for APK upgrades and increment `versionCode` for releases.
- Layout/accessibility warnings remain, including deeply nested station screens. Test small screens, large font sizes, the expanded recurring form, Android 7 compatibility and current Android gesture navigation.

## Required device/integration checks before a pilot

Use dedicated test accounts/data, not live customer orders:

1. Install a signed build, upgrade it with the same signing key, and verify retained data.
2. Customer signup/login/profile change/logout; station approval/rejection; denied cross-account database access and admin-only actions.
3. Simultaneous checkout of the same cart on two devices; one transaction must win. Verify changed prices/fees, deleted products, cart removal and network loss during checkout.
4. Cash-on-delivery and GCash submission, manual payment verification, cancellation and delivery. The GCash dialog is not a payment-provider confirmation.
5. Owner order list changes while already open; customer order status and chat changes across two devices.
6. Recurrence intervals of 1, 3, 5 and 7 days; reminder delivery with notifications denied/allowed; edit at 07:59 versus 08:00; changed station hours, pause/resume and expired notification taps.
7. Checkout recommendation: No thanks, Continue, cancel, save, rotation and repeated taps; inline checkbox collapse/expand and validation errors.
8. Maps/location permissions, image uploads, KYC uploads and large documents on a slow connection.

## References

- [Firebase authorization and security guidance](https://firebase.google.com/docs/firestore/security/overview)
- [Android APK signing requirements](https://developer.android.com/studio/publish/app-signing)
