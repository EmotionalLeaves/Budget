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

1. On your bank's website (in your phone browser), open the account's transactions.
2. Look for **Download** or **Export** and choose **CSV**. Save it to your phone.
3. In Sprout: **Settings → Import CSV** → **Choose a CSV file**.
4. Check the columns and preview, pick or create an account, and tap **Import**.

Importing the same file again is safe: nothing is added twice.

## Moving to a new phone

1. On the old phone: **Settings → Backup and restore → Back up to a file**. Pick a password and save the file (for example to Google Drive).
2. On the new phone: install Sprout, then **Settings → Backup and restore → Restore from a backup**.
3. Connect SimpleFIN again with a new setup token.

Sprout can't recover a forgotten backup password.

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
- **"Paused to stay under SimpleFIN's daily limit"** or **"Paused after SimpleFIN asked Sprout to slow down"** — Wait a few hours. Sprout syncs again automatically.
- **An account says "Not updated since … — closed?"** — Your bank stopped sending it. If it's closed, hide it in Settings → Accounts.
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
- The transaction list is paged (Room Paging); day headers are list rows plus a floating copy of the current day, since true sticky headers need non-paged groups.
- Spending in hidden accounts is left out of budgets, the same way hidden accounts are left out of lists.
- Onboarding goes accounts → quick budget offer (only when there's spending history and no budgets yet) → notification explainer → Home.
- Budget threshold crossings are recorded even when budget alerts are off, so turning alerts on later never replays old ones.
- A transaction is only marked alerted (alertedAt) if its notification was actually shown.
- Recurring detection only looks at posted outflows in spending categories (not transfers or income). The expected amount is the latest charge, so price increases carry through. A charge with no payment for more than two cycles is treated as cancelled.
- A bill up to 5 days late still counts as due (shown as due today) because banks post late.
- CSV amounts use US decimals ("1,234.56"); European comma decimals aren't supported yet (backlog). Files over 10 MB are refused.
- Imported accounts' balances are the sum of their imported transactions.
- Imported rows are marked alerted at import time, so they can never trigger unusual-charge alerts.
- The CSV dedup key uses the description uppercased with single spaces, so case or spacing changes between exports don't create duplicates.
- Export cells starting with = + - @ (that aren't numbers) get a leading apostrophe against spreadsheet formula injection; import removes it again so round trips are exact.
- "Disconnect" offers to delete synced bank accounts and transactions; imported (manual) accounts are always kept.
- App lock uses Android's screen-lock confirmation (KeyguardManager) instead of BiometricPrompt, because BiometricPrompt needs the USE_BIOMETRIC permission and the security checklist allows only internet, network state, notifications and WorkManager's permissions. It still offers fingerprint on phones that use it for the screen lock.
- App lock and "hide in recents" are off by default, to keep everyday use simple.
- Rate-limit warnings are recognized by their wording (e.g. "exceeded", "too many requests", "rate limit"), since the Bridge sends them as plain text; Sprout then pauses all syncing for 12 hours.
- "Closed" accounts are only flagged (missing from successful syncs for 3+ days), never changed automatically, so no database migration is needed.
- The cash forecast uses checking, savings and cash accounts counted in totals; everyday spending is the budget left when budgets exist (bills then only from unbudgeted categories, like safe to spend), otherwise the last 30 days' pace excluding bills.
- Paychecks are found with the same rules as bills, applied to inflows in Income categories.
- Signing-key secrets are deliberately not mapped in CI: the committed key is always used.

---

## For developers

- Build: `./gradlew :core:test testDebugUnitTest lintDebug assembleRelease`
- Modules: `:core` (pure Kotlin: SimpleFIN client and parser, categorization, sync merge, money) and `:app` (Room, WorkManager, Compose UI).
- versionCode is `git rev-list --count HEAD`; versionName is `VERSION_NAME` in `version.properties`.
- CI (`.github/workflows/android.yml`) runs guard checks, tests, lint and an R8 release build, verifies the signing certificate, then publishes a GitHub Release.
- See SECURITY.md, PROGRESS.md, BACKLOG.md and CHANGELOG.md.
