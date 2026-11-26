# Progress

## Needs user
- Optional: paste a read-only update key in Settings → Updates (or add the `UPDATE_TOKEN` repo secret) (read-only, Budget only) so updates download inside the app. Steps in README → Update. Without it the app opens the Releases page.

## Current phase
Phase 2 – improve forever. v1.0.0 published.

## Current task
Round 16. User (2026-11-24): heavy visual and functional polish; each of the next 10 releases the biggest yet (#1 2.4.0 … #10 2.13.0, then 2.14.0); one release per round (update → visuals → features); never stop. Use the screenshot tour: ./gradlew :app:testDebugUnitTest --tests "*ScreenTour*" -Psprout.screens=/tmp/screens [-Psprout.dark=1] [-Psprout.only=dialogs|detail|detail2|settings|onboard].

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
0. 2026-11-26 – v2.21.0 (round 21): on-device auto-sort — ~2,000-entry merchant catalog (indexed), tiered matching, naive-Bayes + merchant-habit learner (CategorySource.LEARNED, no schema change), one-time history re-sort, Travel/Pets categories, Auto-sort card. A helper agent is doing a visual overhaul in a separate worktree (/home/claude/wt-a) for 2.22.0.
1. 2026-11-24 – v2.7.0 (round 8): pre-update snapshots + restore; day totals, budgets widget; merchant renaming (schema 7). Fixed a double-escaped regex before release (caught by tests).
2. 2026-11-24 – v2.6.0 (round 7): resumable update downloads, release history; Customize Home, screen transitions; monthly PDF report, paydays on calendar, edit cash transactions, CSV tags/splits.
3. 2026-11-24 – v2.5.0 (round 6): hands-free background installs, multi-version What's new; count-up amounts, skeletons, Black theme; manual cash transactions, challenges, money health.
4. 2026-11-24 – v2.4.0 (round 5, competitor round): beta channel; reports, year in review; review inbox, flex budgeting, pace, planned expenses, age of money, price changes, receipts, watchlist, cost to be me (schema 6).
5. 2026-11-24 – v2.3.0 (round 4): in-app PackageInstaller updates + pasted key + retry states; review/haptics/quick-setup visuals; 50/30/20, monthly recap.
6. 2026-11-24 – v2.2.0 (round 3): Wi-Fi background update download + restart-safe ready state; category hero ring, bill cards, search pill; spending calendar, bill reminders.
7. 2026-11-24 – v2.1.0 (round 2): update notifications + What's new + cleanup; redesigned welcome/sheet/budgets/goals/settings/empty states; Glance widget, merchant pages, debt planner.
8. 2026-11-24 – v2.0.0 (round 1 of the overhaul): self-updater, new design system + Home, tags (schema 5), insights, net worth, year view, amount search, subscriptions total, CSV overflow fix.
9. 2026-11-24 – v1.15.0: CSV comma decimals + bank Category column (CsvCategories); backlog refilled.
10. 2026-11-24 – v1.14.0: category icons (CategoryIcons, TransactionRow.categoryIcon).
11. 2026-11-24 – v1.13.0: battery-saver skip for periodic sync (SyncWorker.skipForBatterySaver + tests).
12. 2026-11-24 – v1.12.1: Compose UI tests (CorrectionUiTest: budget dialog, split dialog, list row).
13. 2026-11-24 – v1.12.0: split label in lists (TransactionRow.splitCount), rollover breakdown on category screen.
14. 2026-11-24 – v1.11.1: split-aware everydaySpending query (forecast) + test.
15. 2026-11-24 – v1.11.0: split transactions (Splits domain rules, transaction_splits table, MIGRATION_3_4, split-aware spending queries, backup).

## Known issues
- The repo is private, so GitHub Actions minutes are limited (2,000/month free). Keep pushes meaningful.
- App data-layer coverage is 79% because Keystore and notification code can't run under Robolectric (core is 97%).

## Next steps
1. Phase 2: work BACKLOG.md top-down by tier (bugs → security → sync → tests → UX → performance → features).
2. Each item: small change, tests, CI green, bump version (patch/minor), CHANGELOG/PROGRESS/BACKLOG.
3. Refill the backlog when fewer than 10 items remain.
