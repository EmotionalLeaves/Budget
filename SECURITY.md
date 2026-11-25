# Security and privacy

Sprout is a read-only budgeting app. This page explains, in plain language, how it protects your data.

## It can never move money
There is no code for payments, transfers, bill pay or trading. Sprout only ever makes two kinds of request:
one POST to claim a setup token (once), and GETs to read your accounts.

## Your data stays on your phone
- No backend server, no analytics, no crash reporting, no ads, no cloud sync.
- Financial data has one network destination: your SimpleFIN server. The only other host is api.github.com (and GitHub's download CDN) for the self-updater, which fetches release metadata and the APK and sends nothing about you. The optional read-only update key is sent only to api.github.com over HTTPS (unit-tested) and is stripped before following the CDN redirect. Downloads are checked against GitHub's SHA-256 digest, and Android refuses an APK signed with any other key.
- Android backups are turned off (`allowBackup="false"` plus rules that exclude everything), so your data isn't copied to Google's cloud or to a new phone.
- HTTPS only: plain HTTP is blocked by the network security config.

## How the bank connection is stored
- The SimpleFIN access URL is encrypted with AES-256-GCM using a key in the Android Keystore (alias `sprout_simplefin_key`). The key can't be exported from the phone.
- Only the ciphertext is stored, in a separate file (`secure_store`). It's never in the database or in plain text.
- It's decrypted in memory only when a sync runs.
- If it can't be decrypted (for example the key was reset), Sprout asks you to reconnect. It never falls back to plain text.
- The access URL and setup token are never logged, shown, exported, put in notifications or sent anywhere but the SimpleFIN host. `AccessUrl.toString()` prints `AccessUrl(***)`, and every network error message goes through a redactor.
- Credentials are sent in an `Authorization: Basic` header; they are removed from the request URL. Redirects are never followed.

## Backups
Backups are files you create and place yourself. Each is encrypted with AES-256-GCM using a key derived from your password (PBKDF2-HMAC-SHA256, 600,000 iterations, random salt); the header is authenticated, so any change to the file is detected. Backups never include the SimpleFIN access URL. Restores happen in one database transaction.

## Optional protections
- **App lock**: asks for your phone's screen lock on open and after a minute in the background.
- **Hide in screenshots and recent apps** (FLAG_SECURE).

## Crash reports
If Sprout crashes, a short report is saved in the app's private storage: exception class names and code locations only, never exception messages (which could contain transaction text). It is shown to you on the next start and leaves the phone only if you tap Share and choose where to send it.

## Permissions
Only internet, network state, notifications and REQUEST_INSTALL_PACKAGES (so the updater can open Android's installer; Android still asks you every time) (plus what WorkManager needs for background sync: wake lock, boot completed, foreground service). No storage, contacts, location, phone or SMS. Only the launcher screen is exported, plus WorkManager's job service, which Android binds and protects with the system-only BIND_JOB_SERVICE permission. Library diagnostic receivers are removed. A non-exported FileProvider shares only cache/updates/ with the system installer. CI checks the merged manifest on every build.

## Signing key (why it's in the repo)
Every APK is signed with the same key, committed at `keystore/ci-release.keystore` with public, documented passwords (`sprout-ci`, alias `sprout`).
- This is **not a secret** and protects nothing confidential.
- It exists only so every new APK installs as an update over the old one. A different key would force an uninstall, which deletes your data.
- Certificate SHA-256: `abaa721dd2e60684a8aebbad2e7e737a49ca3ed3bab8efec6d2d43fb9f3a4512`. CI fails if a build's certificate is different, or differs from the previous release.
- Because anyone with repo access could sign an APK with this key, **only install Sprout from this repo's Releases page.**

## Automatic checks in CI
Every build fails if it finds: analytics/crash/ads SDKs, destructive database migrations, credentials inside URLs, or tracked secret files (`*.env`, `google-services.json`, `local.properties`).

## Database updates never wipe data
Every database change ships with an explicit migration and a test proving existing data survives. Destructive migration is forbidden.

## Deleting data
"Disconnect" deletes the stored access URL and its Keystore key and stops background sync; it keeps your data unless you also choose to delete synced bank data. "Delete all data" (two confirmations) clears every database table, all settings and the Keystore key.

## Exports
CSV exports are written only where you choose (Android's file picker). They contain only data columns, never the access URL or any credential (a test checks every export kind). Text that a spreadsheet could run as a formula is prefixed with an apostrophe.

## Reporting a problem
Open an issue in this repo. Never paste a setup token, access URL or bank data into an issue.
