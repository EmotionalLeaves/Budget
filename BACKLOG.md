# Backlog

Prioritized. Pick from the top tier that has open items. Each item has a one-line "why".

## Phase 1 milestones (done)
- [x] M1 (0.1.0) connect + transactions · [x] M2 (0.2.0) reliable sync · [x] M3 (0.3.0) categorization
- [x] M4 (0.4.0) budgets · [x] M5 (0.5.0) dashboard · [x] M6 (0.6.0) notifications · [x] M7 (0.7.0) bills
- [x] M8 (0.8.0) CSV import · [x] M9 (0.9.0) export and settings · [x] M10 (0.10.0) hardening → 1.0.0

## 1. Bugs and crashes
- [x] (1.0.1) Local crash log: catch uncaught exceptions to a file on the phone and show "Sprout closed unexpectedly — share details?" (no network) — *why: no crash reporting service, so the user needs a way to report crashes*
- [x] (1.0.1) Home's budget card keeps the month it was created with; refresh when the month changes while the app stays open — *why: wrong month after midnight on the 1st*
- [x] (1.0.1) Transactions list "today/yesterday" labels don't refresh after midnight — *why: stale headers*
- [ ] Forecast "everyday spending" ignores split parts (uses the parent category only) — *why: forecast accuracy*

## 2. Security and data safety
- [x] (1.1.0) Optional app lock with fingerprint/PIN (BiometricPrompt) — *why: banking data on a shared or lost phone*
- [x] (1.1.0) FLAG_SECURE option to hide Sprout from screenshots and the recent-apps preview — *why: amounts visible in the app switcher*
- [x] (1.2.0) Local encrypted backup and restore to a user-chosen file (password-protected) — *why: survive a phone change without cloud backup*
- [x] (1.9.1) Certificate pinning: decided against (see README Assumptions); system-only trust anchors stay

## 3. Sync reliability
- [x] (0.2.0/0.6.0) Per-connection problems on Home with a "Fix in Bridge" link — *why: quicker diagnosis*
- [x] (1.3.0) Detect SimpleFIN rate-limit warnings in "errors" and slow down automatically — *why: the Bridge disables tokens past the warning level*
- [x] (1.3.0) Flag accounts missing from syncs for 3+ days as possibly closed (no schema change) — *why: totals stay right*
- [x] (1.9.0) Purchase-date option (transacted_at) for posted transactions — *why: dates match receipts*

## 4. Test coverage
- [ ] Compose UI tests (Robolectric) for the detail sheet, review flow and budget edit dialog — *why: guard the main correction paths*
- [x] (1.9.1) SyncWorker tests with work-testing (retry, Retry-After chain, revoked cancel, KEEP)
- [x] (1.9.0) 10k-transaction generated SyncMerger test — *why: merge performance regression guard*
- [x] (1.7.0) First migration (1→2) with MigrationTest seeding realistic v1 data — *why: the first migration must be easy and safe*

## 5. UX polish and accessibility
- [x] (1.4.0) Transaction detail: link to its recurring charge — *why: jump from a charge to its bill*
- [x] (1.4.0) Undo snackbar after "Always use this" recategorization — *why: easy fix for a mis-tap that moved many transactions*
- [x] (1.4.0) Filter chips on Transactions for account and category (month: open) — *why: find things fast*
- [x] (1.4.0) Budgets: "Show all" categories
- [x] (1.5.0) Category detail month switcher — *why: plan ahead and look back*
- [ ] Category icons (iconKey) in chips and grids — *why: faster scanning*
- [ ] TalkBack and 200% font pass on every screen with screenshot tests (Roborazzi) — *why: accessibility regressions are easy to miss*
- [x] (1.5.0) Onboarding: offer Quick budget setup right after the first sync — *why: spec flow; faster to value*
- [ ] Mark split transactions in the list (small "Split" label) — *why: know at a glance*
- [ ] Rollover breakdown on the category screen (set amount, carried, available) — *why: explain the number*

## 6. Performance and battery
- [ ] Baseline profile for faster cold start — *why: opens instantly*
- [ ] Skip the 6-hourly sync in battery saver unless it's been 24 h — *why: battery*
- [ ] Debounce budget/recurring recomputation after bulk recategorization — *why: smooth on big histories*

## 7. New budgeting features
- [x] (1.6.0) Spending trend on Home (this month vs same days last month) — *why: see direction at a glance*
- [x] (1.7.0) Savings goals with progress — *why: popular in budgeting apps, motivating*
- [x] (1.6.0) Cash-flow forecast to month end (balance − upcoming bills + expected income) — *why: "will I make it to payday?"*
- [x] (1.8.0) Bill calendar view — *why: see bills by date*
- [x] (1.10.0) Budget rollover (opt-in per category, schema 3 + MIGRATION_2_3)
- [x] (1.11.0) Split a transaction across categories (schema 4 + MIGRATION_3_4)
- [ ] Tags on transactions — *why: track trips or projects across categories*
- [ ] Home-screen widget (Glance) with safe to spend — *why: check without opening the app*
- [ ] Custom-currency lookup (GET the currency URL) — *why: show points/crypto accounts properly*
- [ ] CSV import: European comma decimals and a Category column — *why: more bank formats*
- [ ] CSV export: one row per split part (or a "Split" column) — *why: spreadsheets match budgets*
