# Progress

## Needs user
- Nothing right now.

## Current phase
Phase 2 – improve forever. v1.0.0 published.

## Current task
Phase 2 tier 1: local crash log (BACKLOG "Bugs and crashes").

## v1.0 checklist (verified 2026-11-24)
- [x] F1–F9 acceptance criteria pass, with unit tests where specified (core: token/URL/parser/client, 60+ categorization tests, budget math, alerts, recurring, CSV 6 formats + round trip; app: sync, categories, budgets, alerts, recurring, import, export, connect, 20k-row data).
- [x] Every SimpleFIN error-matrix row handled and tested: claim 403, /accounts 403/402, con.auth, act.*, gen./legacy, network/timeout, 5xx/429 + Retry-After, malformed JSON, partial data, non-https/credential-less access URL.
- [x] Section 8 security checklist satisfied; CI guard checks (banned SDKs, destructive migration, credential URLs, secret files) and merged-manifest check active.
- [x] CI green on main: unit tests, coverage, lint, R8 release build, guard checks.
- [x] APK installs as an update: committed key, versionCode = commit count; CI verifies the certificate digest against the expected value and the previous release's APK.
- [x] Room schema JSON committed (app/schemas, version 1); no destructive migration anywhere (guard + SchemaTest).
- [x] Works with no network (cached data, "Offline" label) and with no SimpleFIN connection (skip onboarding, CSV import).
- [x] README phone-friendly and complete; SECURITY, CHANGELOG, PROGRESS, BACKLOG current.
- [x] BACKLOG.md holds 35 prioritized Phase 2 items.
- [x] GitHub Release v1.0.0 published with the APK and marked latest (this push).

## Recent changes
1. 2026-11-24 – v1.0.0: v1.0 checklist verified and ticked; release notes; Phase 2 begins.
2. 2026-11-24 – v0.10.0 released; CI green including coverage and manifest check.
3. 2026-11-24 – v0.10.0: removed exported diagnostic receivers, manifest check script in CI, Kover coverage in CI (core 97.4%, app data layer 79.4% lines), ConnectRepositoryTest (5), backlog rewritten with 35 Phase 2 items.
4. 2026-11-24 – v0.9.0: CsvExport (round-trip + formula-injection tests), ExportRepository for 6 datasets via SAF, DataManager disconnect/delete-all, account rename/type, licenses; DataManagementTest (5 tests).
5. 2026-11-24 – v0.8.0: RFC 4180 parser + delimiter/header/date-format detection, amount parsing, deterministic dedup ids (19 tests, 6 fixture formats incl. malformed), ImportRepository (4 tests), import UI with mapping, preview, manual accounts and summary.
6. 2026-11-24 – v0.7.0: RecurringDetector (18 tests: cadences, irregular/drift rejection, Jan 31→Feb 28/29, price increase, stopped subs), RecurringRepository (never resurrects dismissed, keeps user edits), Bills tab, manual bills, Home next 3 bills, recurring charges feed safe to spend.
7. 2026-11-24 – v0.6.0: notification channels, budget 80/100% alerts (BudgetAlerts, budget_alerts table), large/unusual alerts (UnusualDetector, alertedAt), sync-problem alerts, private lock-screen visibility, deep links, permission explainer, settings toggles + threshold; 20 core + 7 Robolectric tests.
8. 2026-11-24 – v0.5.0: Home dashboard (spent vs budget, safe to spend, top 5 categories), paged transaction list with floating day header and pending filter, LargeDataTest (20k rows, index usage via EXPLAIN QUERY PLAN).
9. 2026-11-24 – v0.4.0: pure BudgetMath (25 tests incl. refunds, bills, last day, local midnight, DST), BudgetRepository, Budgets tab, safe-to-spend card with breakdown, quick setup, category detail with Canvas 6-month chart.
10. 2026-11-24 – CI fix: v0.3.0 run failed because Robolectric downloaded its Android jar at test time (IOException). Now resolved via a Gradle configuration (cached/retried) and Robolectric runs offline; mavenLocal allowed for that one module.
11. 2026-11-24 – v0.3.0: transaction detail sheet, one-tap recategorize with learned EXACT rules, uncategorized review, keyword rules, category management, rule re-application (CategoryRepository + 8 tests).
12. 2026-11-24 – v0.2.0 released (CI green).
13. 2026-11-24 – v0.2.0: new-account backfill (account= filter), Retry-After honored (tested decision function), offline label, partial-sync note, excludedFromBudget carried pending→posted.
14. 2026-11-24 – v0.1.0 released: CI green, APK sprout-v0.1.0-1.apk published.
15. 2026-11-24 – Scaffolded project (`:core` pure Kotlin + `:app`), version catalog, Gradle wrapper 8.11.1, AGP 8.7.3, Kotlin 2.1.0.

## Known issues
- The repo is private, so GitHub Actions minutes are limited (2,000/month free). Keep pushes meaningful.
- App data-layer coverage is 79% because Keystore and notification code can't run under Robolectric (core is 97%).

## Next steps
1. Phase 2: work BACKLOG.md top-down by tier (bugs → security → sync → tests → UX → performance → features).
2. Each item: small change, tests, CI green, bump version (patch/minor), CHANGELOG/PROGRESS/BACKLOG.
3. Refill the backlog when fewer than 10 items remain.
