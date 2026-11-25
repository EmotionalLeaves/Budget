# Changelog

All notable changes to Sprout. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow semver.

## [1.9.1] - 2026-11-24

### Tests
- Background sync is now tested end to end: retries, the server's "try again later" timing, stopping after 3 retries, and pausing sync when access is revoked.

### Security
- Decided not to pin SimpleFIN's certificate (it would break sync whenever the server renews it). Sprout keeps trusting only your phone's built-in certificates over HTTPS.

 - 2026-11-24

### Added
- Settings → Sync → "Date transactions by purchase day": show each transaction on the day you made the purchase instead of the day the bank posted it (when your bank sends that date). Switching it re-dates your existing transactions; off by default.

### Tests
- Merging 10,000 transactions is now covered by a test (no duplicates, no extra work on repeat syncs).

## [1.8.0] - 2026-11-24

### Added
- Bills → Calendar: a month calendar showing which days bills land on and how much. Tap a day to see its bills; paid ones are marked ✓. Use the arrows to look ahead or back.

## [1.7.0] - 2026-11-24

Savings goals.

### Added
- Budgets → Goals: save toward anything. Give it a target and, if you like, a date; Sprout shows your progress and how much to set aside each month.
- Track a goal with an account's balance (like a savings account) or with amounts you add yourself.
- Home shows your first two goals.
- Goals are included in backups.

### Notes
- This update adds a new table to Sprout's database. Your existing data is kept as-is (tested before release).

## [1.6.0] - 2026-11-24

"Will I make it to the end of the month?"

### Added
- Home: a cash forecast for the end of the month: your checking and savings now, plus expected paychecks, minus bills still due and everyday spending. It warns if you could dip below zero before payday, and shows your next paycheck. Tap it to see the math.
- Home: how much you've spent so far this month compared with the same days last month.
- Sprout now recognizes regular paychecks (weekly, every 2 weeks, monthly) the same way it finds bills.

## [1.5.0] - 2026-11-24

### Added
- Category pages now have month arrows, so you can look back at any month's spending, merchants and transactions.
- Right after you connect your bank, Sprout offers budgets based on your recent spending. Tap "Use these budgets" or "Not now".

## [1.4.0] - 2026-11-24

Easier everyday use.

### Added
- Undo: after changing a category, tap Undo to put everything back, including other transactions moved by "Always use this" and any rule it created.
- Transactions: filter by account and by category.
- Budgets: "Show all categories" lets you add a budget to a category before you've spent anything in it.
- A transaction that's part of a bill now says so in its details.

## [1.3.0] - 2026-11-24

Gentler, smarter syncing.

### Added
- If SimpleFIN warns that Sprout is making too many requests, Sprout pauses syncing for 12 hours, so your access never gets turned off. "Sync now" explains the pause.
- Accounts your bank stopped reporting for 3+ days show "Not updated since … — closed?" on Home, with a hint to hide them.

## [1.2.0] - 2026-11-24

Backup and restore.

### Added
- Settings → Backup and restore: save everything (transactions, accounts, categories, budgets, rules and bills) to one password-protected file wherever you like, and restore it on this or a new phone.
- The file is encrypted with your password and can't be read or changed without it. It never contains your bank connection; reconnect SimpleFIN after restoring on a new phone.
- Restoring is all-or-nothing: if anything is wrong with the file, your current data stays exactly as it was.

## [1.1.0] - 2026-11-24

Privacy options.

### Added
- Settings → Privacy → "Lock Sprout with your screen lock": Sprout asks for your phone's PIN, pattern or fingerprint when you open it, and again after it's been in the background for a minute. Off by default.
- Settings → Privacy → "Hide in screenshots and recent apps": your balances don't show in the app switcher or in screenshots. Off by default.

## [1.0.1] - 2026-11-24

### Added
- If Sprout ever closes unexpectedly, Home shows a note next time with "Share details" (your choice of app, e.g. to paste into a GitHub issue) or "Dismiss". The report is saved only on your phone and has no amounts, descriptions or bank details.

### Fixed
- Home's budget and bills now switch to the new month at midnight on the 1st, even if the app stays open.
- "Today" and "Yesterday" labels on Transactions update after midnight.

## [1.0.0] - 2026-11-24

Sprout 1.0: everything from the 0.x releases, checked and ready for everyday use.

### What Sprout does
- Connects read-only to your US bank accounts through SimpleFIN Bridge and syncs about every 6 hours.
- Categorizes transactions automatically and learns from your corrections.
- Monthly budgets per category with a clear "safe to spend" number.
- Alerts for budgets, large or unusual charges, and connection problems.
- Finds subscriptions and bills and shows what's coming up.
- CSV import and export. Works with no bank connection at all.
- Everything stays on your phone: no servers, no analytics, no ads.

### Install or update
Download the .apk below and install it over your current Sprout. Never uninstall first.

## [0.10.0] - 2026-11-24

Hardening before 1.0.

### Changed
- Removed two diagnostic components that libraries added and Sprout doesn't need, so only the app's main screen is open to other apps (plus Android's background-job service, which the system needs).
- Every build now checks the app's permissions and open components, and reports test coverage.

### Added
- More tests for connecting (used tokens keep your existing connection, invalid tokens never touch the network) and for exports never containing credentials.

## [0.9.0] - 2026-11-24

Your data, your control.

### Added
- Settings → Export CSV: transactions (all time or a date range), accounts, categories, budgets, merchant rules or bills. You choose where the file goes. Exports never include your bank connection. An exported transactions file imports back exactly.
- Settings → Disconnect: Sprout forgets your SimpleFIN connection and stops syncing. Keep your data, or also delete the synced bank accounts (imported ones stay).
- Settings → Delete all data: erases everything on this phone after two confirmations, then starts fresh.
- Rename accounts and change their type (checking, savings, credit and so on).
- About: source code link and open-source licenses.

## [0.8.0] - 2026-11-24

CSV import, for banks SimpleFIN can't reach.

### Added
- Settings → Import CSV (also on Home before you connect a bank): pick a CSV file from your phone. No storage permission needed.
- Sprout finds the Date, Description and Amount columns (or separate Debit and Credit columns) and the date format by itself; you can change any of them and flip signs.
- A preview of the first 10 rows before importing.
- Import into an existing imported account or create a new one (name and type).
- Importing the same or an overlapping file never creates duplicates.
- A summary shows how many rows were imported, skipped as duplicates, or couldn't be read (with reasons).
- Imported transactions get categories, count toward budgets and bills, and can trigger budget alerts (never "unusual charge" alerts).

## [0.7.0] - 2026-11-24

Bills and subscriptions.

### Added
- Sprout now spots subscriptions and bills (weekly, every 2 weeks, monthly, every 3 months, yearly) from your last 400 days, even after a price increase.
- Bills tab: what's due in the next 30 days with a total, what's still due this month, and what's already paid (✓).
- Suggested bills can be confirmed or dismissed (dismissed ones never come back). Tap any bill to change its amount, how often, next date or category.
- Add a bill yourself with the + button (rent, for example).
- Home shows your next 3 bills.
- Safe to spend now subtracts bills still due this month in categories without a budget.

## [0.6.0] - 2026-11-24

Notifications.

### Added
- Budget alerts when a category reaches 80% and when it reaches 100% of its budget (each once per month; if one charge crosses both, you only get the 100% alert).
- Alerts for large charges (default $250 or more) and unusual ones: much more than you usually spend at a merchant, or $150+ at a merchant you've never used. Never for your first sync or CSV imports.
- "Sync problems" alerts when a bank needs attention or SimpleFIN access is turned off.
- Amounts are hidden on the lock screen. Tapping an alert opens the category or transaction.
- After connecting on Android 13+, Sprout explains alerts before asking for permission. Everything works without them.
- Settings → Notifications: turn each kind on or off and set the large-charge amount.

## [0.5.0] - 2026-11-24

A fuller Home screen and a faster transaction list.

### Added
- Home now shows "Spent this month" against your total budget, the Safe to spend card, and your top 5 spending categories with mini progress bars. Tap a category to open it.
- Transactions: "Pending only" filter.

### Changed
- The transaction list now loads in pages, so it stays smooth with tens of thousands of transactions (it used to stop at 3,000). The current day stays pinned at the top while you scroll.

## [0.4.0] - 2026-11-24

Budgets and "safe to spend".

### Added
- Budgets tab: a monthly budget per category, with a progress bar that turns amber at 80% and red at 100%, and "$X left" or "$X over".
- Safe to spend this month: what's left in your budgets, minus bills still due in categories without a budget, with "≈ $Y/day". Tap it to see how it's worked out.
- Budgets can apply to every month or just this month. Switch months with the arrows.
- Quick setup: suggests a budget per category from your last 3 months of spending, rounded up to the next $10. Edit anything, then save.
- Category pages: this month vs budget, a 6-month spending chart, spending by merchant, transactions, and the rules for that category.
- "Not budgeted" list shows categories where you spent money without a budget, with an Add budget button.

### Notes
- Transfers and income never count as spending. Refunds reduce spending.

## [0.3.0] - 2026-11-24

Categories that learn from you.

### Added
- Tap any transaction to see its details, change its category, add a note, or exclude it from your budget.
- "Always use this for {merchant}" (on by default): Sprout remembers your choice, moves that merchant's other transactions, and uses it for future ones.
- Review uncategorized: a banner on Transactions shows how many need a category; tap Review to sort them one tap at a time.
- "Uncategorized only" filter on Transactions.
- Settings → Categories: add, rename, recolor or archive categories.
- Settings → Category rules: see what Sprout learned, change or delete rules, and add keyword rules ("contains").

## [0.2.0] - 2026-11-24

More reliable syncing.

### Added
- Banks you add later in SimpleFIN Bridge now get their full history (up to 90 days) on their first sync, not just the last 10 days.
- Home shows "Offline – showing data from …" when your phone has no internet.
- A note on Home when the last sync was only partly complete, with SimpleFIN's own message.

### Fixed
- When SimpleFIN asks Sprout to wait ("Retry-After"), Sprout now waits that long before trying again (between 15 minutes and 6 hours).
- "Exclude from budget" now stays on when a pending charge posts.

## [0.1.0] - 2026-11-24

First working version: connect your bank and see your transactions.

### Added
- Connect to your bank through SimpleFIN Bridge: paste a setup token, tap Connect. Friendly messages for used or invalid tokens.
- Your accounts, balances and transactions sync automatically about every 6 hours, plus pull-to-refresh on Home.
- Home: total balance (cash and credit), your accounts, recent transactions and when data was last updated.
- Transactions: every transaction grouped by day, with search.
- Automatic categories for common US merchants (groceries, gas, eating out, subscriptions and more).
- Warnings when SimpleFIN access is turned off, the subscription needs attention, or a bank needs you to sign in again.
- Settings: replace setup token, sync now, sync log, history window, include/hide accounts.
- "Skip — I'll import a CSV" to use the app without a bank connection.
- Privacy: read-only, encrypted connection storage, no backups, no analytics.
