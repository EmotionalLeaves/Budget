# Progress

## Needs user
- Nothing right now.

## Current phase
Phase 2 – improve forever. v1.0.0 published.

## Current task
Phase 2: bill calendar view; budget rollover; then refill backlog if low.

## v1.0 checklist (verified 2026-11-24)
- [x] F1–F9 acceptance criteria pass, with unit tests where specified (core: token/URL/parser/client, 60+ categorization tests, budget math, alerts, recurring, CSV 6 formats + round trip; app: sync, categories, budgets, alerts, recurring, import, export, connect, 20k-row data).
- [x] Every SimpleFIN error-matrix row handled and tested: claim 403, /accounts 403/402, con.auth, act.*, gen./legacy, network/timeout, 5xx/429 + Retry-After, malformed JSON, partial data, non-https/credential-less access URL.
- [x] Section 8 security checklist satisfied; CI guard checks (banned SDKs, destructive migration, credential URLs, secret files) and merged-manifest check active.
- [x] CI green on main: unit tests, coverage, lint, R8 release build, guard checks.
- [x] APK installs as an update: committed key, versionCode = commit count; CI verifies the certificate digest against the expected value and the previous release's APK.
- [x] Room schema JSON committed (app/schemas, versions 1–2); no destructive migration anywhere (guard + SchemaTest + MigrationTest).
- [x] Works with no network (cached data, "Offline" label) and with no SimpleFIN connection (skip onboarding, CSV import).
- [x] README phone-friendly and complete; SECURITY, CHANGELOG, PROGRESS, BACKLOG current.
- [x] BACKLOG.md holds 35 prioritized Phase 2 items.
- [x] GitHub Release v1.0.0 published with the APK and marked latest (this push).

## Recent changes
1. 2026-11-24 – v1.7.0: savings goals (GoalMath 7 tests, GoalRepository 3 tests, Goals screen, Home card); DB v2 with explicit MIGRATION_1_2 and MigrationTest proving v1 data survives; goals in backups.
2. 2026-11-24 – v1.6.0: CashFlow forecast (low point, next paycheck; 8 tests), income detection via RecurringDetector.detectIncome, spending trend vs same days last month, ForecastRepository (2 Robolectric tests), Home forecast card with breakdown.
3. 2026-11-24 – v1.5.0: category detail month switcher; onboarding Quick budget step (suggestions applied in one tap).
4. 2026-11-24 – v1.4.0: undoable recategorization (snapshot + rule restore, 2 tests), account/category filter chips, budgets show-all (BudgetMonth.allLines), bill link in transaction detail.
5. 2026-11-24 – v1.3.0: 12-hour pause after SimpleFIN rate-limit warnings (SyncErrors.isRateLimitWarning + SyncRepository test); stale/possibly-closed account flag on Home (AccountTypes.isStale).
6. 2026-11-24 – v1.2.0: password-encrypted backup/restore (BackupCrypto PBKDF2+AES-GCM with authenticated header, 9 tests; BackupRepository JSON codec, all-or-nothing restore, 5 tests).
7. 2026-11-24 – v1.1.0: optional app lock (KeyguardManager confirm, 60 s grace, AppLockPolicy tests) and FLAG_SECURE toggle.
8. 2026-11-24 – v1.0.1: local crash log (message-free, user-shared only, 3 tests); Home budget/bills and Transactions day labels roll over at midnight (todayFlow).
9. 2026-11-24 – v1.0.0 released (CI green).
10. 2026-11-24 – v1.0.0: v1.0 checklist verified and ticked; release notes; Phase 2 begins.
11. 2026-11-24 – v0.10.0 released; CI green including coverage and manifest check.
12. 2026-11-24 – v0.10.0: removed exported diagnostic receivers, manifest check script in CI, Kover coverage in CI (core 97.4%, app data layer 79.4% lines), ConnectRepositoryTest (5), backlog rewritten with 35 Phase 2 items.
13. 2026-11-24 – v0.9.0: CsvExport (round-trip + formula-injection tests), ExportRepository for 6 datasets via SAF, DataManager disconnect/delete-all, account rename/type, licenses; DataManagementTest (5 tests).
14. 2026-11-24 – v0.8.0: RFC 4180 parser + delimiter/header/date-format detection, amount parsing, deterministic dedup ids (19 tests, 6 fixture formats incl. malformed), ImportRepository (4 tests), import UI with mapping, preview, manual accounts and summary.
15. 2026-11-24 – v0.7.0: RecurringDetector (18 tests: cadences, irregular/drift rejection, Jan 31→Feb 28/29, price increase, stopped subs), RecurringRepository (never resurrects dismissed, keeps user edits), Bills tab, manual bills, Home next 3 bills, recurring charges feed safe to spend.

## Known issues
- The repo is private, so GitHub Actions minutes are limited (2,000/month free). Keep pushes meaningful.
- App data-layer coverage is 79% because Keystore and notification code can't run under Robolectric (core is 97%).

## Next steps
1. Phase 2: work BACKLOG.md top-down by tier (bugs → security → sync → tests → UX → performance → features).
2. Each item: small change, tests, CI green, bump version (patch/minor), CHANGELOG/PROGRESS/BACKLOG.
3. Refill the backlog when fewer than 10 items remain.
