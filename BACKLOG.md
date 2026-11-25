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
- [x] (1.11.1) Forecast everyday spending follows split parts

## 2. Security and data safety
- [x] (1.1.0) Optional app lock with fingerprint/PIN (BiometricPrompt) — *why: banking data on a shared or lost phone*
- [x] (1.1.0) FLAG_SECURE option to hide Sprout from screenshots and the recent-apps preview — *why: amounts visible in the app switcher*
- [x] (1.2.0) Local encrypted backup and restore to a user-chosen file (password-protected) — *why: survive a phone change without cloud backup*
- [x] (1.9.1) Certificate pinning: decided against (see README Assumptions); system-only trust anchors stay
- [ ] Automatic encrypted backup reminder (monthly nudge, never automatic upload) — *why: phone loss is the main data risk*

## 3. Sync reliability
- [x] (0.2.0/0.6.0) Per-connection problems on Home with a "Fix in Bridge" link — *why: quicker diagnosis*
- [x] (1.3.0) Detect SimpleFIN rate-limit warnings in "errors" and slow down automatically — *why: the Bridge disables tokens past the warning level*
- [x] (1.3.0) Flag accounts missing from syncs for 3+ days as possibly closed (no schema change) — *why: totals stay right*
- [x] (1.9.0) Purchase-date option (transacted_at) for posted transactions — *why: dates match receipts*
- [x] (1.15.1) Per-account "updated X ago" on Home (per-account errors were already shown; a single-account retry would spend the same daily request budget as "Sync now", so it isn't worth a separate button)

## 4. Test coverage
- [x] (1.12.1) Compose UI tests: budget dialog, split dialog, list row semantics (detail sheet/review flow still open below)
- [ ] Compose UI tests for the detail sheet and review flow (need a fake AppContainer) — *why: guard recategorize + undo*
- [x] (1.9.1) SyncWorker tests with work-testing (retry, Retry-After chain, revoked cancel, KEEP)
- [x] (1.9.0) 10k-transaction generated SyncMerger test — *why: merge performance regression guard*
- [x] (1.7.0) First migration (1→2) with MigrationTest seeding realistic v1 data — *why: the first migration must be easy and safe*
- [x] (2.0.0) CSV fuzz tests (found and fixed an overflow)

## 5. UX polish and accessibility
- [x] (1.4.0) Transaction detail: link to its recurring charge — *why: jump from a charge to its bill*
- [x] (1.4.0) Undo snackbar after "Always use this" recategorization — *why: easy fix for a mis-tap that moved many transactions*
- [x] (1.4.0) Filter chips on Transactions for account and category (month: open) — *why: find things fast*
- [x] (1.4.0) Budgets: "Show all" categories
- [x] (1.5.0) Category detail month switcher — *why: plan ahead and look back*
- [x] (1.14.0) Category icons in chips and the picker
- [ ] TalkBack and 200% font pass on every screen with screenshot tests (Roborazzi) — *why: accessibility regressions are easy to miss*
- [x] (1.5.0) Onboarding: offer Quick budget setup right after the first sync — *why: spec flow; faster to value*
- [x] (1.12.0) Split label in the transaction list
- [x] (1.12.0) Rollover breakdown on the category screen
- [x] (2.0.0) Search by amount
- [x] (2.1.0) Welcome screen redesign
- [ ] Redesign Bills, Goals, Settings and the detail sheet in the new style — *why: consistency*
- [ ] Swipe actions on transactions (swipe to recategorize or tag) — *why: faster corrections*

## 6. Performance and battery
- [ ] Baseline profile for faster cold start — *why: opens instantly*
- [x] (1.13.0) Battery saver: scheduled sync waits until data is 24 h old
- [ ] Debounce budget/recurring recomputation after bulk recategorization — *why: smooth on big histories*

## 7. New budgeting features
- [x] (1.6.0) Spending trend on Home (this month vs same days last month) — *why: see direction at a glance*
- [x] (1.7.0) Savings goals with progress — *why: popular in budgeting apps, motivating*
- [x] (1.6.0) Cash-flow forecast to month end (balance − upcoming bills + expected income) — *why: "will I make it to payday?"*
- [x] (1.8.0) Bill calendar view — *why: see bills by date*
- [x] (1.10.0) Budget rollover (opt-in per category, schema 3 + MIGRATION_2_3)
- [x] (1.11.0) Split a transaction across categories (schema 4 + MIGRATION_3_4)
- [x] (2.0.0) Tags on transactions (schema 5)
- [x] (2.1.0) Home-screen widget (Glance)
- [ ] Custom-currency lookup (GET the currency URL) — *why: show points/crypto accounts properly*
- [x] (1.15.0) CSV import: comma decimals and the bank's Category column
- [ ] CSV export: one row per split part (or a "Split" column) — *why: spreadsheets match budgets*
- [x] (2.0.0) Year view heat grid
- [ ] Annual/irregular expenses sinking fund (e.g. car registration) as a goal type — *why: no surprise bills*
- [x] (2.1.0) Debt payoff planner
- [ ] Cash-flow calendar: projected balance per day for the next 30 days — *why: avoid overdrafts*
- [ ] Budget templates (50/30/20, zero-based) in quick setup — *why: a starting point*
- [x] (2.1.0) Merchant pages
- [x] (2.2.0) Bill reminders
- [ ] Spending challenges ("No eating out this week") with a streak — *why: motivation*
- [ ] Paycheck planner: split each paycheck into bills, budgets and savings — *why: give every dollar a job*
- [x] (2.3.0) 50/30/20 template
- [x] (2.3.0) Monthly recap card
- [x] (2.4.0) Compared with leading apps: see "Compared with leading apps" below

## Compared with leading apps (2026-11-24)

What the leaders do well, and what Sprout takes from it. Never copied: anything that moves money (Rocket Money's bill negotiation, cancellation service, automatic savings transfers) or needs a server (shared household sync). Sprout already has: safe to spend (PocketGuard's "In My Pocket"), rollover (Monarch), splits, tags, rules, recurring calendar, net worth, debt planner (YNAB loan planner, PocketGuard), goals, insights.

- [x] (2.4.0) Flex budgeting (Monarch): Fixed, Non-monthly and one Flex bucket instead of a cap per category; switch in Budgets
- [x] (2.4.0) Pace (PocketGuard Pace, Monarch forecasts): "on pace to spend $X by month end" per budget and overall
- [x] (2.4.0) Review inbox (Copilot): new transactions wait in "To review" until you mark them seen; badge on the tab
- [x] (2.4.0) Reports (Monarch, Copilot Cash Flow, YNAB reports): income vs spending per month, by category, by account, by tag, any date range
- [x] (2.4.0) Age of Money (YNAB): how many days old the money you spend is
- [x] (2.4.0) Sinking funds / targets (YNAB targets, Monarch non-monthly): "Car registration $240 by July" → monthly set-aside, counted in safe to spend
- [x] (2.4.0) Subscription price-increase alerts (Copilot, Rocket Money): "Netflix went up from $15.49 to $17.99"
- [x] (2.4.0) Year in review (Copilot): a yearly recap screen
- [x] (2.4.0) Receipt photos (Monarch, Copilot attachments): attach a photo to a transaction via the system photo picker (no permission)
- [x] (2.4.0) Watchlist (Rocket Money): pin categories or merchants to Home with this month's total
- [x] (2.4.0) Beta channel for updates: opt in to newer test builds
- [ ] Demo mode (Copilot): explore Sprout with sample data before connecting
- [x] (2.4.0) Cost to be me (YNAB): your total monthly obligations (bills + budgets) in one number
