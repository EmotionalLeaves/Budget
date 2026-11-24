# Sprout

Sprout is a private budgeting app for Android that reads your bank accounts (read-only) and keeps all your data on your phone.

## ➜ [Download the latest version](https://github.com/EmotionalLeaves/Budget/releases/latest)

---

## Install on your phone

1. Open the link above in your phone's browser (sign in to GitHub if it asks; this repo is private).
2. Under **Assets**, tap the file that ends in **.apk**.
3. If asked, allow your browser to **install unknown apps**.
4. If Play Protect warns you, tap **More details** → **Install anyway**.
5. Open **Sprout**.

## Update

1. Download the newer .apk the same way.
2. Install it. Your data stays.
3. **Never uninstall first.** Uninstalling deletes your data.

Only install Sprout from this repo's Releases page.

## Connect your bank with SimpleFIN

1. Go to **simplefin.org** and open **SimpleFIN Bridge**.
2. Create an account. It's a small paid subscription; check the site for the current price.
3. Connect your bank(s) inside Bridge.
4. Create a new **app connection / setup token** and copy it.
5. Open Sprout, tap **I have a setup token**, tap **Paste**, then **Connect**.

Each setup token works only once.

**If a bank later "needs attention":** Sprout shows a warning on Home and in Settings. Tap **Fix in SimpleFIN Bridge**, sign in to that bank again there, and Sprout picks it up on the next sync.

## No SimpleFIN? Use CSV

CSV import is coming in version 0.8. You can already skip the bank connection: tap **Skip — I'll import a CSV**.

## Privacy

- Read-only: Sprout can never move money.
- Everything stays on this phone. No analytics, no servers, no ads.
- The only place Sprout connects to is SimpleFIN.
- Your bank connection is stored encrypted with a key that never leaves your phone.

## Troubleshooting

- **"This setup token was already used or doesn't exist"** — Create a new token in SimpleFIN Bridge. If you didn't use the old one yourself, disable the old connection in Bridge.
- **"Couldn't reach SimpleFIN"** while connecting — Check your internet and try again. If it still fails, create a new token (the old one may have been used).
- **"Your SimpleFIN access was turned off"** — Paste a new setup token: Settings → Replace setup token. Your data is kept.
- **"Your SimpleFIN Bridge subscription needs attention"** — Open Bridge and check your subscription. Sprout tries again on the next sync.
- **A bank "needs attention"** — Tap Fix in SimpleFIN Bridge and sign in to that bank again.
- **An account shows a warning** — SimpleFIN couldn't update that one account. Other data is fine; Sprout retries later.
- **"Offline – showing data from …"** — Sprout shows what it already has and syncs when you're back online.
- **"Paused to stay under SimpleFIN's daily limit"** — Wait a few hours. Sprout syncs again automatically.
- **"Saved connection can't be read on this phone"** — Paste a new setup token in Settings.

## Assumptions

Decisions made while building, one per line:

- The repo already existed as private, so it stays private: downloads need a GitHub sign-in, and GitHub Actions minutes are limited (2,000/month free) instead of unlimited.
- SimpleFIN Bridge developer notes (checked 2026-11-24): ≤24 requests/day, ≤90 days of history per request, overlap windows ~5 days, sync at a random minute. Sprout caps itself at 16 requests per rolling 24 hours, overlaps 10 days, and starts its 6-hour schedule at a random minute.
- Code is split into a pure Kotlin `:core` module (domain logic + SimpleFIN client, no Android imports possible) and the `:app` module; packages follow data/… and domain/… as specced.
- Dependency injection is manual (one `AppContainer`), to keep the app small and simple.
- Networking is plain OkHttp; JSON is parsed as a kotlinx.serialization tree so one odd field can't lose a whole sync.
- Network timeouts: connect 15 s, read 60 s. Redirects are never followed, so credentials only go to the access URL's own host.
- A pasted, already-decoded claim URL is accepted as well as a Base64 token.
- Category source NONE marks fallback categories (Income/Uncategorized); BUILT_IN marks built-in rule matches.
- Extra columns beyond the spec: `transactions.occurredAt` (the date used for display and months), `accounts.lastErrorCode/lastErrorMessage` (per-account warnings), `sync_log.requestCount` (daily-limit tracking).
- A pending transaction without transacted_at keeps the time it was first seen, so it doesn't move each sync.
- Pending rows are never deleted for an account the server reported an error for (its data may be incomplete).
- Shared SimpleFIN test fixtures live in `core/src/test/resources/simplefin/` and are reused by app tests.
- Room schema JSONs are added to debug assets only (for Robolectric migration tests); release APKs don't contain them.
- Debug builds use the app id suffix `.debug` so they can never replace the real app.
- The lint baseline holds one warning: the adaptive icon must stay in `mipmap-anydpi-v26` because AAPT can't find it otherwise.
- Outgoing amounts show a minus sign as well as the default color, so color is never the only signal.
- The transaction list shows the newest 3,000 rows until the paged list ships (on the backlog).
- Signing-key secrets are deliberately not mapped in CI: the committed key is always used.

---

## For developers

- Build: `./gradlew :core:test testDebugUnitTest lintDebug assembleRelease`
- Modules: `:core` (pure Kotlin: SimpleFIN client and parser, categorization, sync merge, money) and `:app` (Room, WorkManager, Compose UI).
- versionCode is `git rev-list --count HEAD`; versionName is `VERSION_NAME` in `version.properties`.
- CI (`.github/workflows/android.yml`) runs guard checks, tests, lint and an R8 release build, verifies the signing certificate, then publishes a GitHub Release.
- See SECURITY.md, PROGRESS.md, BACKLOG.md and CHANGELOG.md.
