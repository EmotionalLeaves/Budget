# Progress

## Needs user
- Nothing right now.

## Current phase
Phase 1 – building v1.0. M1–M4 done or in CI. M5 (0.5.0) ready locally.

## Current task
M5 (v0.5.0): push after the v0.4.0 run is green. Next: M6 notifications.

## Recent changes
1. 2026-11-24 – v0.5.0: Home dashboard (spent vs budget, safe to spend, top 5 categories), paged transaction list with floating day header and pending filter, LargeDataTest (20k rows, index usage via EXPLAIN QUERY PLAN).
2. 2026-11-24 – v0.4.0: pure BudgetMath (25 tests incl. refunds, bills, last day, local midnight, DST), BudgetRepository, Budgets tab, safe-to-spend card with breakdown, quick setup, category detail with Canvas 6-month chart.
3. 2026-11-24 – CI fix: v0.3.0 run failed because Robolectric downloaded its Android jar at test time (IOException). Now resolved via a Gradle configuration (cached/retried) and Robolectric runs offline; mavenLocal allowed for that one module.
4. 2026-11-24 – v0.3.0: transaction detail sheet, one-tap recategorize with learned EXACT rules, uncategorized review, keyword rules, category management, rule re-application (CategoryRepository + 8 tests).
5. 2026-11-24 – v0.2.0 released (CI green).
6. 2026-11-24 – v0.2.0: new-account backfill (account= filter), Retry-After honored (tested decision function), offline label, partial-sync note, excludedFromBudget carried pending→posted.
7. 2026-11-24 – v0.1.0 released: CI green, APK sprout-v0.1.0-1.apk published.
8. 2026-11-24 – Scaffolded project (`:core` pure Kotlin + `:app`), version catalog, Gradle wrapper 8.11.1, AGP 8.7.3, Kotlin 2.1.0.
9. 2026-11-24 – SimpleFIN: token decoding, claim, access URL parsing, v1/v2 parser with errlist + errors, MockWebServer tests.
10. 2026-11-24 – Merchant normalization + built-in rules + resolver (60+ tests), sync merge with dedup and pending→posted.
11. 2026-11-24 – Room v1 schema with every spec entity; Keystore-encrypted access URL in DataStore; settings DataStore.
12. 2026-11-24 – Sync engine with error matrix, daily request budget, SyncLog; WorkManager periodic (6 h) and one-time sync.
13. 2026-11-24 – Compose UI: onboarding/connect, Home, Transactions, Settings; Budgets/Bills placeholders.
14. 2026-11-24 – CI workflow: guard checks, tests, lint, R8 release, signing-cert check, release publishing. Docs.

## Known issues
- The repo is private, so GitHub Actions minutes are limited (2,000/month free). Keep pushes meaningful.
- Kover coverage not yet wired into CI (backlog).

## Next steps
1. Confirm CI green and release v0.1.0 exists with the APK.
2. Start M2 (v0.2.0): reliable sync polish and the full error matrix in the UI.
