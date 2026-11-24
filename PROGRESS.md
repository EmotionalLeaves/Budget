# Progress

## Needs user
- Nothing right now.

## Current phase
Phase 1 – building v1.0. Milestone M1 (v0.1.0): connect and list transactions.

## Current task
M1: first push. Waiting for CI to go green and publish release v0.1.0 with the APK.

## Recent changes
1. 2026-11-24 – Scaffolded project (`:core` pure Kotlin + `:app`), version catalog, Gradle wrapper 8.11.1, AGP 8.7.3, Kotlin 2.1.0.
2. 2026-11-24 – SimpleFIN: token decoding, claim, access URL parsing, v1/v2 parser with errlist + errors, MockWebServer tests.
3. 2026-11-24 – Merchant normalization + built-in rules + resolver (60+ tests), sync merge with dedup and pending→posted.
4. 2026-11-24 – Room v1 schema with every spec entity; Keystore-encrypted access URL in DataStore; settings DataStore.
5. 2026-11-24 – Sync engine with error matrix, daily request budget, SyncLog; WorkManager periodic (6 h) and one-time sync.
6. 2026-11-24 – Compose UI: onboarding/connect, Home, Transactions, Settings; Budgets/Bills placeholders.
7. 2026-11-24 – CI workflow: guard checks, tests, lint, R8 release, signing-cert check, release publishing. Docs.

## Known issues
- The repo is private, so GitHub Actions minutes are limited (2,000/month free). Keep pushes meaningful.
- Kover coverage not yet wired into CI (backlog).
- Transactions list capped at 3,000 rows until paging lands.

## Next steps
1. Confirm CI green and release v0.1.0 exists with the APK.
2. Start M2 (v0.2.0): reliable sync polish and the full error matrix in the UI.
