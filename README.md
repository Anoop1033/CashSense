# CashSense

An Android app that shows your account balance as Indian currency notes and coins,
and visually "removes" them as you spend via UPI — so digital payments carry the
same felt sense of depletion that cash withdrawals used to.

## How it works

1. **Onboarding** — enter your current balance once. It's broken down into notes/coins
   using a greedy denomination algorithm (₹500, 200, 100, 50, 20, 10, 5, 2, 1 — the
   ₹2000 note is excluded since RBI withdrew it from circulation in 2023).
2. **Automatic detection** — `UpiNotificationListenerService` reads notification text
   from whichever apps post it (GPay, PhonePe, Paytm, your bank's app, etc.) and runs
   it through `TransactionTextParser`, which only fires on text containing both a
   rupee amount and an explicit debit/credit keyword (and vetoes promotional
   notifications like cashback offers). No bank/UPI credentials, SMS access, or
   real balance API are involved — there isn't a general one in India outside the
   RBI-regulated Account Aggregator framework.
3. **Confirmation, not auto-debit** — every detected transaction lands as a pending
   card on the Wallet screen. You confirm (editing the amount/direction if the parse
   was wrong), dismiss it if it wasn't a transaction, or ignore it. Only confirmed
   transactions touch the balance. You can also add income/expenses manually.
4. **Visual wallet** — the Wallet screen redraws the note/coin stacks after every
   change and animates whichever denominations changed (scale bounce + green/red
   flash) so a spend is felt, not just read as a number.

## Project layout

```
app/src/main/java/com/cashsense/app/
  domain/     DenominationBreakdown (greedy algorithm), TransactionTextParser — pure, unit-tested
  data/       Room entities/DAO/DB, DataStore prefs, WalletRepository
  service/    UpiNotificationListenerService
  ui/         onboarding/, home/ (wallet visual + view model), history/, settings/, navigation/, theme/
app/src/test/ JUnit tests for the two pure domain classes
```

## Running it

This was built without access to Android Studio or the Android SDK in the
environment that generated it, so **it has not been compiled or run** — I reviewed
every file by hand for API correctness, but you should treat first build as the
real test. To run it:

1. Install [Android Studio](https://developer.android.com/studio) (it bundles a
   compatible JDK and lets you install the Android SDK on first launch).
2. Open the `CashSense` folder as a project. Android Studio will fetch Gradle 8.7
   (per `gradle/wrapper/gradle-wrapper.properties`) and sync automatically — the
   wrapper jar itself wasn't generated, so if command-line `gradlew` is needed,
   run `gradle wrapper` once (with any local Gradle install) to create it.
3. Run on a device or emulator with **API 26+**.
4. In the app: finish onboarding, then go to **Settings → Grant notification
   access** and enable CashSense. Without this permission, only manual add
   money/expense works — automatic UPI detection needs it.
5. Sanity-check the core algorithm anytime with `./gradlew testDebugUnitTest`
   (needs the Android SDK installed, but not a device/emulator).

## Known limitations / next steps

- Notification-text parsing is heuristic. It's deliberately conservative (requires
  an explicit amount + debit/credit keyword, vetoes promo language) and every match
  still requires your confirmation, but wording varies across banks/apps — expect
  to refine `TransactionTextParser`'s keyword lists against your own notifications.
- No cloud sync/backup yet — data is local (Room + DataStore) and reset with the
  app's data or the in-app "Reset wallet" action.
- No launcher icon artwork beyond a simple placeholder vector — swap
  `app/src/main/res/drawable/ic_launcher_foreground.xml` for real branding.
- A "real" balance sync (rather than manual entry + detected deltas) would require
  integrating RBI's Account Aggregator framework — a substantially larger, licensed
  undertaking, out of scope here.
