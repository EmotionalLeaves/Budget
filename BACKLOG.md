# Backlog

Prioritized. Phase 1 milestones come first (see PROGRESS.md); items below are grouped by the 7 priority tiers.

## Phase 1 milestones
- [x] M2 (0.2.0) – Reliable sync: backfill for new connections, Retry-After, offline state, partial-sync note — *why: sync is the app's lifeline*
- [x] M3 (0.3.0) – Categorization: detail sheet, one-tap recategorize + "Always use" rules, uncategorized review, category/rule management
- [ ] M4 (0.4.0) – Budgets and safe to spend — *why: the core value of the app*
- [ ] M5 (0.5.0) – Full dashboard — *why: one glance answers "am I OK?"*
- [ ] M6 (0.6.0) – Notifications — *why: warn before overspending*
- [ ] M7 (0.7.0) – Recurring charges and bills — *why: no surprise charges*
- [ ] M8 (0.8.0) – CSV import — *why: works for banks SimpleFIN can't reach*
- [ ] M9 (0.9.0) – CSV export, disconnect, delete all data, about/licenses — *why: user owns their data*
- [ ] M10 (0.10.0 → 1.0.0) – Hardening and v1.0 checklist

## 1. Bugs and crashes
- [x] Carry `excludedFromBudget` over from a pending row to its posted row (0.2.0)

## 2. Security and data safety
- [ ] Disconnect: delete access URL + Keystore key, keep data, offer to delete data — *why: user control over the connection*
- [ ] Add Kover coverage report to CI (target ≥80% on domain/data) — *why: spec requirement; finds untested paths*

## 3. Sync reliability
- [x] Backfill a connection added later in Bridge with the first-sync window (0.2.0)
- [x] Respect Retry-After when scheduling the WorkManager retry (0.2.0)
- [ ] Show per-connection problems in the sync status line chip — *why: quicker diagnosis*

## 4. Test coverage
- [ ] Compose UI tests for the detail sheet and review flow (Robolectric) — *why: guard the main correction path*
- [ ] ViewModel tests for connect flow error messages — *why: copy is part of the spec*
- [ ] 10k-transaction generated fixture through SyncMerger — *why: performance regression guard*

## 5. UX polish and accessibility
- [ ] Undo snackbar after recategorizing with "Always use" — *why: easy to fix a mis-tap that moved many transactions*
- [ ] Category icons (iconKey) shown in chips and grid — *why: faster scanning*
- [ ] Paged transaction list (Room Paging) instead of the 3,000-row cap — *why: smooth with 20,000 transactions*
- [ ] Filter chips on Transactions (account, category, month, pending, uncategorized) — *why: find things fast*
- [ ] TalkBack pass on all screens — *why: accessibility*

## 6. Performance and battery
- [ ] Baseline profile for faster cold start — *why: opens instantly*

## 7. New budgeting features
- [ ] Custom-currency lookup (GET the currency URL) — *why: show points/crypto accounts properly*
- [ ] Spending trends chart — *why: see direction over months*
- [ ] Local encrypted backup and restore to a user-chosen file — *why: survive phone changes*
