# CashSense — Play Store submission pack

Everything Play asks for, written out and ready to paste. Assets sit next to this file.

---

## ⚠️ Before anything else: back up the upload key

`cashsense-upload.jks` and `keystore.properties` are in the project root and are **git-ignored on
purpose**. Play identifies an app by the key it was signed with. Once the first release is live,
that key is the only thing on earth that can publish an update to it — lose it and the listing is
frozen forever, and the app has to be republished under a new package name with every install and
review lost.

Copy both files somewhere durable **now** (password manager, encrypted drive, anywhere that is not
just this laptop). They are not in the repository and never will be.

- Key alias: `cashsense`
- Certificate SHA-1: `EC:A6:9B:69:4A:EE:3B:18:FC:67:21:65:F5:18:21:8A:E8:54:AE:54`
- Valid until: 29 December 2053

(Enrol in **Play App Signing** during setup — Google then holds the signing key and this one becomes
the *upload* key, which Google can reset if it is ever lost. Strongly recommended, and default.)

---

## Store listing

**App name** (30 char limit)

```
CashSense: Balance as Cash
```

**Short description** (80 char limit)

```
See your bank balance as rupee notes that shrink as you spend. Works offline.
```

**Full description** (4000 char limit)

```
Paying by UPI is frictionless — and that is exactly the problem. Cash left your hand and you
felt it go. A UPI payment leaves nothing behind but a number that was already abstract.

CashSense puts the feeling back. It shows your balance as the actual notes and coins it is made
of: ten ₹500 notes, a ₹200, a ₹50. Spend, and you watch a note lift out of the wallet and fly
away. Get paid, and one drops in. Nothing else changes about how you pay — CashSense just makes
the money visible again.

HOW IT WORKS

Tell CashSense your current balance once. After that it reads the payment notifications your
bank and UPI apps already send you, and keeps the wallet in step automatically. No statements to
import, no spreadsheets, nothing to remember to do.

WHAT MAKES IT DIFFERENT

• Your balance as physical currency, in real Indian denominations, not a bar chart
• Notes fly out when you spend and drop in when you are paid — including everything that
  happened while the app was closed, replayed the moment you open it
• Payments detected automatically from bank and UPI notifications
• The same payment announced by your bank's SMS, its email and your UPI app is recognised as one
  payment, not three, by matching the bank's own UPI reference number
• Add or remove money by hand whenever you want
• Pay anyone by UPI or QR code from inside the app
• Full history, and anything read wrongly can be removed with a long press

PRIVATE BY CONSTRUCTION, NOT BY PROMISE

CashSense has no internet permission. Not "we choose not to send your data" — the app is
technically incapable of sending anything anywhere. There is no account, no sign-up, no cloud, no
analytics, no ads, and no tracking of any kind. Your transactions live in a database on your phone
and nowhere else. Uninstall the app and they are gone with it.

CashSense never asks for and never receives your banking credentials, card numbers, PINs or OTPs.
It has no connection to your bank whatsoever. It only reads notifications your bank has already
sent to your own phone.

ABOUT NOTIFICATION ACCESS

To detect payments, CashSense uses Android's notification access. Android does not let any app
subscribe to a chosen few apps' notifications, so this permission covers all of them — which is
why CashSense filters immediately: anything not from a payment app, a bank app, or your SMS and
email apps is discarded without being examined. Of what remains, only messages describing a
completed payment are used. You can revoke the permission at any time, and the app keeps working
with manual entry.

CashSense is not affiliated with the Reserve Bank of India or with any bank. The note designs are
stylised illustrations, not reproductions of currency.
```

**Category:** Finance
**Tags:** Budgeting, Expense tracker, Personal finance
**Contact email:** anup1033@gmail.com — *visible on the public listing*
**Privacy policy URL:** `https://anoop1033.github.io/CashSense/privacy-policy.html`
(needs GitHub Pages switched on — see the checklist below)

---

## Graphics

| Asset | File | Status |
|---|---|---|
| App icon 512×512 | `play-icon-512.png` | ready (opaque, no alpha) |
| Feature graphic 1024×500 | `feature-graphic.png` | ready |
| Phone screenshots | `screenshot-1-wallet.png`, `-2-history.png`, `-3-settings.png` | ready (1080×2280) |

Play wants a minimum of 2 phone screenshots; 4–8 is better. Worth adding: the QR-scan/pay screen
and a mid-animation shot of a note flying out.

---

## Data safety form

The app transmits nothing, so the honest answers are also the simplest ones.

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | N/A — no data leaves the device |
| Do you provide a way for users to request that their data is deleted? | **Yes** — "Reset wallet" in Settings, and uninstalling removes everything |

Play defines "collection" as data leaving the device. CashSense reads notifications and writes
transactions **only on-device**, and holds no `INTERNET` permission, so "No data collected" is
accurate. Say so plainly in the review notes as well, since a finance app declaring no collection
does attract a second look.

---

## Sensitive permission declaration (notification access)

Play Console will ask why the app needs `BIND_NOTIFICATION_LISTENER_SERVICE`. Paste:

```
CashSense shows a user's bank balance as physical currency notes that change as they spend.
Reading payment notifications is the core function of the app — without it there is no way to
know a payment happened, and the app's entire purpose disappears.

The app reads notifications only to extract three things from a completed payment: the amount,
whether it was money in or out, and the bank's UPI reference number (used to recognise that the
same payment announced by SMS, email and a UPI app is one payment rather than three).

Notifications from any package that is not a payment app, a bank app, or a carrier of bank alerts
(the user's SMS and email apps) are discarded without being processed. Notification content is
never logged and never transmitted: the app declares no INTERNET permission and is technically
incapable of sending data off the device. All data stays in a private on-device database and is
deleted when the user resets the wallet or uninstalls.

There is no alternative API. Android exposes no way to observe UPI or bank payments, and India's
Account Aggregator framework is restricted to RBI-regulated entities.
```

---

## Content rating

Answer "No" to everything about violence, sexuality, drugs, gambling and user-generated content.
Target audience: **18+**. Do not opt into the Families programme.

Expected rating: Everyone / PEGI 3.

---

## Submission checklist

1. **Create the developer account** — <https://play.google.com/console>, $25 one-time. Identity
   verification takes a few days; start it first, everything else can be done while it runs.
2. **Switch on GitHub Pages** so the privacy policy resolves — repo Settings → Pages → Source:
   *Deploy from a branch* → branch `master`, folder `/docs` → Save. Wait a minute, then confirm
   <https://anoop1033.github.io/CashSense/privacy-policy.html> loads.
3. **Back up the keystore** (see the top of this file). Do not skip this.
4. **Create the app** in Play Console — name, default language English (India), type App, free.
5. **Fill the listing** with the copy above, upload the graphics, add the 512×512 icon.
6. **Complete the declarations** — Data safety, content rating, target audience, ads (none),
   news (no), government apps (no), financial features (see note below), and the notification
   access justification.
7. **Upload the AAB** — `app/build/outputs/bundle/release/app-release.aab`, built by
   `./gradlew bundleRelease`.
8. **Run the closed test** — a personal developer account must run a closed test with **12 testers
   opted in for 14 continuous days** before production access is granted. This is the long pole:
   start it the day the account is verified. Recruit the 12 first; the clock only runs while at
   least 12 are opted in.
9. **Apply for production access**, then submit for review. First review commonly takes several
   days and can take longer for a finance app using notification access.

### One to think about at step 6

Play's **Financial features** declaration asks whether the app provides personal loans, banking
services, and so on. CashSense does none of those — it is a personal expense tracker — so the
answer is no to each. Note that it does launch UPI payment intents; that is handing off to the
user's own UPI app, not processing payments, and does not make CashSense a payments provider.
Be straightforward about it in the review notes rather than leaving it to be discovered.
