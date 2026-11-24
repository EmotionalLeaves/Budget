# Progress

## Needs user
- Nothing right now.

## Current phase
Phase 1 – building v1.0. M1–M7 released or in CI. M8 (0.8.0) ready.

## Current task
M8 (v0.8.0): push after v0.7.0 is green. Next: M9 CSV export and settings completion.

## Recent changes
1. 2026-11-24 – v0.8.0: RFC 4180 parser + delimiter/header/date-format detection, amount parsing, deterministic dedup ids (19 tests, 6 fixture formats incl. malformed), ImportRepository (4 tests), import UI with mapping, preview, manual accounts and summary.
2. 2026-11-24 – v0.7.0: RecurringDetector (18 tests: cadences, irregular/drift rejection, Jan 31→Feb 28/29, price increase, stopped subs), RecurringRepository (never resurrects dismissed, keeps user edits), Bills tab, manual bills, Home next 3 bills, recurring charges feed safe to spend.
3. 2026-11-24 – v0.6.0: notification channels, budget 80/100% alerts (BudgetAlerts, budget_alerts table), large/unusual alerts (UnusualDetector, alertedAt), sync-problem alerts, private lock-screen visibility, deep links, permission explainer, settings toggles + threshold; 20 core + 7 Robolectric tests.
4. 2026-11-24 – v0.5.0: Home dashboard (spent vs budget, safe to spend, top 5 categories), paged transaction list with floating day header and pending filter, LargeDataTest (20k rows, index usage via EXPLAIN QUERY PLAN).
5. 2026-11-24 – v0.4.0: pure BudgetMath (25 tests incl. refunds, bills, last day, local midnight, DST), BudgetRepository, Budgets tab, safe-to-spend card with breakdown, quick setup, category detail with Canvas 6-month chart.
6. 2026-11-24 – CI fix: v0.3.0 run failed because Robolectric downloaded its Android jar at test time (IOException). Now resolved via a Gradle configuration (cached/retried) and Robolectric runs offline; mavenLocal allowed for that one module.
7. 2026-11-24 – v0.3.0: transaction detail sheet, one-tap recategorize with learned EXACT rules, uncategorized review, keyword rules, category management, rule re-application (CategoryRepository + 8 tests).
8. 2026-11-24 – v0.2.0 released (CI green).
9. 2026-11-24 – v0.2.0: new-account backfill (account= filter), Retry-After honored (tested decision function), offline label, partial-sync note, excludedFromBudget carried pending→posted.
10. 2026-11-24 – v0.1.0 released: CI green, APK sprout-v0.1.0-1.apk published.
11. 2026-11-24 – Scaffolded project (`:core` pure Kotlin + `:app`), version catalog, Gradle wrapper 8.11.1, AGP 8.7.3, Kotlin 2.1.0.
12. 2026-11-24 – SimpleFIN: token decoding, claim, access URL parsing, v1/v2 parser with errlist + errors, MockWebServer tests.
13. 2026-11-24 – Merchant normalization + built-in rules + resolver (60+ tests), sync merge with dedup and pending→posted.
14. 2026-11-24 – Room v1 schema with every spec entity; Keystore-encrypted access URL in DataStore; settings DataStore.
15. 2026-11-24 – Sync engine with error matrix, daily request budget, SyncLog; WorkManager periodic (6 h) and one-time sync.

## Known issues
- The repo is private, so GitHub Actions minutes are limited (2,000/month free). Keep pushes meaningful.
- Kover coverage not yet wired into CI (backlog).

## Next steps
1. Confirm CI green and release v0.1.0 exists with the APK.
2. Start M2 (v0.2.0): reliable sync polish and the full error matrix in the UI.
