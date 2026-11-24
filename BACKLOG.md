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

## 2. Security and data safety
- [x] (1.1.0) Optional app lock with fingerprint/PIN (BiometricPrompt) — *why: banking data on a shared or lost phone*
- [x] (1.1.0) FLAG_SECURE option to hide Sprout from screenshots and the recent-apps preview — *why: amounts visible in the app switcher*
- [x] (1.2.0) Local encrypted backup and restore to a user-chosen file (password-protected) — *why: survive a phone change without cloud backup*
- [ ] Pin the SimpleFIN Bridge certificate chain (with a safe fallback) — *why: defense against a compromised CA*

## 3. Sync reliability
- [x] (0.2.0/0.6.0) Per-connection problems on Home with a "Fix in Bridge" link — *why: quicker diagnosis*
- [x] (1.3.0) Detect SimpleFIN rate-limit warnings in "errors" and slow down automatically — *why: the Bridge disables tokens past the warning level*
- [x] (1.3.0) Flag accounts missing from syncs for 3+ days as possibly closed (no schema change) — *why: totals stay right*
- [ ] Use transacted_at for posted transactions' display date when the bank provides it (setting) — *why: dates match receipts*

## 4. Test coverage
- [ ] Compose UI tests (Robolectric) for the detail sheet, review flow and budget edit dialog — *why: guard the main correction paths*
- [ ] SyncWorker tests with work-testing (retry, Retry-After chain, revoked cancel) — *why: work package coverage is low*
- [ ] 10k-transaction generated SyncMerger fixture — *why: merge performance regression guard*
- [ ] Migration test template ready for schema version 2 — *why: the first migration must be easy and safe*

## 5. UX polish and accessibility
- [ ] Transaction detail: link to its recurring charge — *why: jump from a charge to its bill*
- [ ] Undo snackbar after "Always use this" recategorization — *why: easy fix for a mis-tap that moved many transactions*
- [ ] Filter chips on Transactions for account, category and month — *why: find things fast*
- [ ] Budgets: "Show all" to budget categories with no spending yet; category detail month switcher — *why: plan ahead and look back*
- [ ] Category icons (iconKey) in chips and grids — *why: faster scanning*
- [ ] TalkBack and 200% font pass on every screen with screenshot tests (Roborazzi) — *why: accessibility regressions are easy to miss*
- [ ] Onboarding: offer Quick budget setup right after the first sync — *why: spec flow; faster to value*

## 6. Performance and battery
- [ ] Baseline profile for faster cold start — *why: opens instantly*
- [ ] Skip the 6-hourly sync in battery saver unless it's been 24 h — *why: battery*
- [ ] Debounce budget/recurring recomputation after bulk recategorization — *why: smooth on big histories*

## 7. New budgeting features
- [ ] Spending trends chart on Home (this month vs last 3 months) — *why: see direction at a glance*
- [ ] Savings goals with progress — *why: popular in budgeting apps, motivating*
- [ ] Cash-flow forecast to month end (balance − upcoming bills + expected income) — *why: "will I make it to payday?"*
- [ ] Bill calendar view — *why: see bills by date*
- [ ] Budget rollover (unused amount carries to next month, opt-in per category) — *why: flexible budgets*
- [ ] Split a transaction across categories — *why: a Target run is groceries + household*
- [ ] Tags on transactions — *why: track trips or projects across categories*
- [ ] Home-screen widget (Glance) with safe to spend — *why: check without opening the app*
- [ ] Custom-currency lookup (GET the currency URL) — *why: show points/crypto accounts properly*
- [ ] CSV import: European comma decimals and a Category column — *why: more bank formats*
