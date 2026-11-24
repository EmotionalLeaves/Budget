# Changelog

All notable changes to Sprout. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow semver.

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
