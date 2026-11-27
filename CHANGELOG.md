# Changelog

All notable changes to Sprout. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions follow semver.

## [2.54.0] - 2026-11-27

Round 54: search answers "how much?".

### Features
- **Search totals:** any search or filter on Activity shows how many purchases match and what they add up to ("19 matches · $1,561.15 out · $4,250.00 in"). "hy-vee last month" now tells you exactly what you spent there.

### Polish
- While you search, the swipe tip and the "new since you last looked" banner step aside so results come first.

## [2.53.0] - 2026-11-27

Round 53: pull to sync on Activity.

### Features
- **Activity** now syncs when you pull down on the list, same as Home. If Sprout synced a moment ago it tells you how fresh the data is; pull again to sync anyway.
- **Empty Home** shows what you'll get once your money's in (safe to spend, bills found for you, purchases sorted for you), instead of a mostly blank page.

## [2.52.0] - 2026-11-27

Round 52: calm loading.

### Polish
- Stats, Reports, Net worth, Year view, Budgets, Quick setup and Sort purchases show a soft placeholder while their numbers load instead of a blank or "Adding things up…". Loads that finish in a blink show nothing, so there's no flicker.

## [2.51.0] - 2026-11-27

Round 51: search by time.

### Features
- **Search understands dates:** add "today", "yesterday", "this week", "last month", "this year", a month ("october", "nov 2025") to any search. They mix with words and amounts: "hy-vee last month", ">50 october".
- Under the search box Sprout says how it read your search ("Showing Last month · Oct 1 – Oct 31 · over $50"), and a one-time tip mentions dates and amounts.

## [2.50.0] - 2026-11-26

Round 50: adding a cash purchase takes fewer taps.

### Features
- **Add a transaction** fills in the category from the name as you type ("Hy-Vee" → Groceries, "Casey's" → Gas & Fuel), using your own rules and habits first. It says "Guessed from the name"; tap any category to override, and it stops guessing once you've picked.

## [2.49.0] - 2026-11-26

Round 49: Year view that shows patterns, not just rent.

### Features
- **Year view** has a new switch: **By amount** (as before) or **Within each category**, which shades each row against its own biggest month, so an expensive month of eating out stands out even next to rent. Sprout remembers which one you picked.

## [2.48.0] - 2026-11-26

Round 48: screens open the way you left them.

### Features
- **Remembered views:** Stats keeps your period and tab (Overview, Flow, Patterns…), Reports keeps its range, and Bills keeps List or Calendar, even after Sprout is closed.

### Polish
- **Money health** on Home says Strong, Okay or Needs care next to the score, and labels its tip "To work on: …", so a good score with a warning no longer reads like a contradiction.

## [2.47.0] - 2026-11-26

Round 47: clearer numbers on Goals and Net worth.

### Polish
- **Goals** past their target no longer say "$6,420 of $1,200": a linked account reads "$1,200 target · Savings holds $6,420", and a hand-funded one says how far past the target it is.
- **Net worth** has a "Month by month" list showing each month's rise or drop next to its total.

## [2.46.0] - 2026-11-26

Round 46: a Bills tab that reads top to bottom.

### Polish
- **Bills** now has a title and a proper List | Calendar switch at the top; the 30-day total comes first, then price changes and your repeating-charges total.
- "To calendar" is now **Add to my calendar**, so it no longer looks like a second Calendar button.
- Price changes get an up or down arrow and read "$87.12 → $84.12".

## [2.45.0] - 2026-11-26

Round 45: empty screens that help.

### Polish
- **Activity with nothing in it** offers Connect a bank, Add a purchase and Import a CSV right there; a search with no results offers **Clear search and filters** (a tag's or account's page keeps its scope).
- **Stats** for a period with nothing in it says so and offers to look at 12 months instead of showing a row of zeros.

## [2.44.0] - 2026-11-26

Round 44: Compare months reads at a glance.

### Polish
- **Reports → Compare** groups categories into **Went up**, **Went down** and **About the same**, each on its own card, and every bar uses one shared scale, so a $34 change finally looks small next to rent.

## [2.43.0] - 2026-11-26

Round 43: Challenges.

### Polish
- **Challenges** opens with how many you're on track with (or an invitation when you have none), and each idea shows its length as a coloured chip in its category's colour.

## [2.42.0] - 2026-11-26

Round 42: fix a place in one tap.

### New
- **A place's page** (tap "Everything at …" on any purchase, or a place in Stats) shows its usual category with a **Change** button: pick a category and every purchase from that place moves, now and in future, with Undo. Purchases you set by hand to something else stay as they are.

### Polish
- The place's purchases sit on one grouped card.

## [2.41.0] - 2026-11-26

Round 41: one word per job.

### Polish
- The two Activity banners no longer both say "Review": new activity is "4 new since you last looked · Check", and purchases without a category are "12 purchases need a category · Sort". The sorting screen is called **Sort purchases** everywhere (its tile in More, its shortcut and its title), and says "All sorted" when you're done.

## [2.40.0] - 2026-11-26

Round 40: hints for the hidden moves.

### New
- **One-time tips** where a gesture isn't obvious: Activity explains swipe right (change category), swipe left (leave out of budgets) and long-press (select several); Review explains "Use this for all". Close a tip and it never comes back.

### Polish
- With large text, the Activity search box just says "Search" instead of a cut-off hint.

## [2.39.0] - 2026-11-26

Round 39: a safety net.

### Under the hood
- Every build now opens every main screen and every Explore tool with realistic data, and scrolls Home all the way down, before it can be released. A screen that crashes while drawing (like the goals card in 2.29) now stops the release instead of reaching your phone.

## [2.38.0] - 2026-11-26

Round 38: the bill calendar.

### Polish
- **Bills → Calendar**: the month sits on its own card, bill days carry a small coloured pill with the amount (green when paid, accent when still due), and the list below is one grouped card with a "10 paid · 2 due" summary.

## [2.37.0] - 2026-11-26

Round 37: Debt plan you can see.

### New
- **Debt plan → Your path to zero**: a chart of what you'll still owe each month until it's gone, with a dot where each debt is paid off.
- The payoff **Order** is a numbered list with how many months each debt takes and the month it's done.

## [2.36.0] - 2026-11-26

Round 36: category pages.

### Polish
- A category's page shows the last 6 months on a proper chart card (this month highlighted, a dashed line for what's usual, "Usually $201 a month"), places as a ranked list with visits, share of the category and a bar, and its transactions on one grouped card.

## [2.35.0] - 2026-11-26

Round 35: shortcuts.

### New
- **Long-press Sprout's icon** for shortcuts straight to **Sort purchases**, **Stats** and **Bills** (you can also drag them onto your home screen).

## [2.34.0] - 2026-11-26

Round 34: Tags worth opening.

### New
- **Tags** opens with your total tagged spending and your biggest tag, and offers **Quick ideas**: one tap makes #Coffee, #Fast food, #Gaming, #Delivery, #Travel or #Art supplies and tags every past and future purchase with the words that give them away.
- Each tag shows a bar of how it compares with your biggest, and a proper edit button.

### Under the hood
- Every build keeps its crash-decoding map for 90 days, so crash reports from your phone can be traced to the exact line.

## [2.33.0] - 2026-11-26

Round 33: a crash fix, and sorting that's easier to undo.

### Fixed
- **Home could crash** when it showed your savings goals: the goal card's tap action was passed into the slot for its footer. Fixed, and the goal row's parameters are reordered so it can't happen again.
- **Extra space above the title** on Year in review, Reports, Net worth, Year view, Challenges, Tags, a merchant's page, Debt plan and the review inbox: their top bars added the status-bar gap a second time.

### New
- **Review uncategorized** shows what you just did ("Carriage Park → Eating Out", plus how many similar ones were sorted) with a **Put back** button that undoes the pick and everything it sorted along with it.

### Polish
- Category buttons are tinted with each category's own colour and give a small squeeze when pressed.

## [2.32.0] - 2026-11-26

Round 32: a tidier More screen.

### Polish
- Settings rows are grouped on shared cards with thin dividers (Categories, Your data), with shorter descriptions and proper chevrons; "Delete all data" stands apart in red.
- When notifications are off, a clear warning card with a "Turn on notifications" button replaces the red line of text.

## [2.31.0] - 2026-11-26

Round 31: see where your money flows.

### New
- **Stats → Flow**: a money-flow (Sankey) chart for any period. Income on the left flows into your six biggest categories, everything else, and what you kept, each band drawn to scale with its amount and share. If you spent more than came in, the gap shows up as "From savings".

## [2.30.0] - 2026-11-26

Round 30: More and transaction details.

### Polish
- **More → Explore** is now a grid of coloured tiles (Stats, Reports, Goals, Challenges, Year in review, Net worth, Year view, Tags, Debt plan, and a new **Sort purchases** shortcut), so every tool is one glance and one tap away instead of a long list.
- **Transaction details**: your note, tags, receipts and "leave out of budgets" sit together on one "Your details" card; the note has a hint, and Split has an icon.

## [2.29.0] - 2026-11-26

Round 29: shared accounts and a calmer Activity list.

### New
- **Shared accounts stay live.** When a joint account is seen through two logins (yours and a partner's), Sprout shows it once. If the login whose copy it kept stops syncing for two days (an expired token, say) while the other keeps going, it switches to the live copy and carries your categories and notes over.

### Polish
- **Activity**: each day's purchases sit together on one rounded card with soft dividers, under a clean day heading with the day's totals. Banners are quieter cards with an icon and a tonal button.

## [2.28.0] - 2026-11-26

Round 28: a richer look.

### Polish
- **Hero cards** on Home, Budgets, Bills, Reports and Goals have layered gradients with soft rings, an animated progress ring, and pill badges ("5 days left", "Next: Target · Dec 3").
- **Bills**: a 30-day timeline of what's coming in the hero, and date tiles tinted by category on every bill.
- **Reports**: a cash-flow chart with gradient bars, a scale and gridlines, and a cleaner category donut.
- **Goals**: one summary hero ("Saved toward goals", overall %) above progress rings for each goal.
- **Home**: a tidier review card and stat tiles, and a money-health ring.
- The Auto-sort card's numbers line up evenly.

## [2.27.0] - 2026-11-26

Round 27: fixes from your real transactions.

### Fixed
- **"Visa Debit" is no longer treated as a store.** Your bank writes card purchases as "18370 VISA DEBIT PURC Carriage Park…", and Sprout took "Visa Debit" as the merchant, so all of them looked like one place: "Use this for all 41" and one pick sorted every card purchase into the same category. Sprout now reads the real place name after the bank's wording (Carriage Park, Salvage Warehouse…). After the update it re-reads every description, removes "always" rules that were made for bank wording, and re-sorts. "Use this for all" only appears for a real place.
- **Your bank connected twice.** Every transaction showed up twice (the same bank linked again gets new ids), doubling spending. Sprout now spots a copy of an account (same name, same transactions), hides it, keeps the one that still syncs, and carries your categories and notes over. If it ever hides one you want, show it again in Settings and it stays visible.
- Toast restaurants are recognized even without the "*" ("PURC TST COPAL MEXIC"), and so are Visa's international service fees, Discover/Capital One card payments, Tello, Google Play purchases, Paddle, Squarespace, Copyleaks, Surfshark, Instant Gaming, Roblox, TP-Link, Kikoff, Kabredlo's, LES bill pay, Valvoline (VIOC) and cash-advance apps like AtlasFinancial (as transfers, since they're borrowed money).
- Paychecks win over the employer's name ("ROUND ONE ENTERT PAYROLL" is income, not entertainment).

### New
- **The same look on every phone.** Sprout is scaled to fit your screen's width, so a smaller or bigger phone shows the same layout as a typical 1080-pixel phone (your font size setting still applies).

## [2.26.0] - 2026-11-26

Round 26: Lincoln, all of it.

### New
- **About 2,000 Lincoln businesses recognized by name**, from restaurants, coffee shops and bars (Lazlo's, Yia Yia's, the Mill, Zoo Bar…) to salons, gyms, clinics, dentists, vets, auto shops, apartment complexes, hotels, venues and stores. Collected from public Lincoln business listings (Downtown Lincoln and Haymarket directories, the malls, local guides). Your history is re-sorted once to pick them up.
- **Older history loads by itself.** If you were on the old 90-day setting, Sprout switches to "Everything the bank has" and fetches older months a few at a time over the next syncs (Force sync speeds it up).
- **Stats got two more views**: a whole year of days as a heatmap (Patterns), and money in vs. out for each of the last 12 months (Trends).

## [2.25.0] - 2026-11-26

Round 25: Stats.

### New
- **Stats** (More → Stats): your spending from new angles, for this month, last month, 3 months, 12 months or all time.
  - **Overview**: what you spent (and how that compares with the same point last period), your pace for the month, earned, net, daily average, number of purchases, typical and average purchase, no-spend days, savings rate, your biggest purchase, and a running-total chart against the period before.
  - **Patterns**: which day of the week and which part of the month you spend most, and purchase sizes (count and total for under $10, $10–50, $50–200, over $200).
  - **Places**: your top places by money spent or by visits, with the average per visit. Tap one to open it.
  - **Trends**: a 12-month line for every category, with what's usual and how this month compares.

## [2.24.0] - 2026-11-26

Round 24: all your history, sync when you want, and re-sort on demand.

### New
- **No more 90-day limit.** Settings → Sync → History to fetch now goes up to 6 months, 1 year, 2 years or "Everything the bank has". SimpleFIN only sends 90 days per request, so Sprout fetches older history in 90-day steps, a few each sync, and stops when your bank has nothing older. Settings shows how far back it's loaded.
- **Force sync.** Settings → Sync → Force sync skips the 15-minute wait, and on Home, pulling down again right after "Synced 3 min ago" syncs anyway. It still stops short of SimpleFIN's 24-a-day limit, because going over can switch your access off.
- **Re-sort button** (✨) next to search in Activity: runs auto-sort over everything you haven't set by hand.
- **Auto-sort these**: select purchases and let Sprout decide for them, even ones you set by hand (with Undo). A single purchase has **Let Sprout decide** too.
- A transaction's category now says where it came from: you, your rule, auto-sorted, learned from your picks, or money between your accounts.

## [2.23.0] - 2026-11-26

Round 23: much smarter auto-sort.

### New
- **Sort a place once and you're done.** For a place Sprout's list doesn't know (your local coffee shop, a market stall), one pick is now enough: every other purchase there follows. (For brands the list knows, it still takes two picks to override it.)
- **Review goes biggest place first.** Uncategorized purchases are grouped by where you spent, so "Use this for all 23 from Scooter's" clears the whole lot. It tells you how few places are actually left to decide on.
- **Hundreds more words recognized**: cuisines and restaurant words (pho, shawarma, izakaya, taphouse…), clinics and specialists (orthodontics, chiropractic, dentistry…), salons, campgrounds, auto shops, mini marts, bowling lanes, public works and more.
- Your history is re-sorted once more after this update to pick all of that up.

## [2.22.0] - 2026-11-26

Round 22: transfers sort themselves.

### New
- **Transfers between your own accounts are spotted automatically**: money that leaves one account and lands in another (same amount to the cent, within 4 days) becomes a Transfer on both sides, so moving money to savings no longer looks like spending plus income.

## [2.21.0] - 2026-11-26

Round 21: Sprout sorts your purchases for you.

### New
- **Auto-sort that actually knows where you shop.** Sprout now recognizes about 2,000 stores, chains and brands out of the box (Lincoln and Nebraska places like Runza, Hy-Vee, Super Saver, Scooter's, Casey's, LES, Black Hills Energy and StarTran, plus national chains), and words that give a place away ("taqueria", "pharmacy", "hotel"). Most card purchases land in the right category with no work from you.
- **It learns from you.** Put a merchant in a category twice and the rest of that merchant's purchases follow. Sort a couple of similar places ("art class", "coffee") and new ones with the same words get sorted too. It all runs on your phone, and it never changes anything you set yourself.
- **Your history is re-sorted once** after this update, so the old Uncategorized pile shrinks right away.
- New **Travel** and **Pets** categories (added to existing installs too).
- **Category rules** now opens with an Auto-sort card: how many purchases were sorted for you, how many of your picks it learns from, what's left, and a "Re-sort now" button.
- Picking a category tells you when similar purchases were re-sorted too, and Undo puts them back.
- **Review uncategorized** offers a one-tap "Looks like Eating Out" guess based on what you've picked before, and tells you how many similar purchases it sorted along the way.
- Online-shop, software and restaurant checkouts (Shopify, FastSpring, Paddle, Toast) are recognized from their payment prefix.
- CSV imports map bank categories like "Travel", "Hotels" and "Pets" to the new categories.

## [2.20.0] - 2026-11-25

### New
- **Account options** on an account's page (⋮): rename it, count it in (or leave it out of) your total balance, or hide it, without a trip to Settings.
- Account pages no longer repeat the account's name on every row.

## [2.19.0] - 2026-11-25

Round 20: every account gets its own page.

### New
- **Account pages**: tap any account on Home. You get its balance, when it last updated, a 6-month balance line, money in and out this month, and all its transactions, with search, filters and selection.

## [2.18.0] - 2026-11-25

Round 19: a gentler first connection.

### Polish
- **Setup token screen**: a back arrow to the welcome screen, and "Connect" stays off until a token is pasted.
- A short **"What happens next"** card explains that the token becomes a read-only link stored encrypted on your phone, and that the first sync can take a minute.

## [2.17.0] - 2026-11-25

Round 18: tidier pages.

### Polish
- **A tag's page** has a real header: "#Gifts", how many transactions, and the total. The review and uncategorized banners stay on Activity, where they belong.
- **Challenges**: ideas you're already doing aren't offered again, and each idea shows its category icon.

## [2.16.0] - 2026-11-25

Round 17: fairness fixes and steadier releases.

### Fixed
- **Challenges start when you tap start.** Spending earlier that same day no longer fails a challenge you just began.
- **Savings goals**: each goal is one clean card again (it was a card inside a card), and "Add money" no longer shows on a goal you've already reached.

### Updates
- Publishing a new version now retries if GitHub has a brief outage, so a release can't go missing the way 2.13.0's did.

## [2.15.0] - 2026-11-25

Round 16: Sprout works with big text.

### Accessibility
- Tested at 200% text size (Settings → Display → Font size on your phone). Fixed everything that broke:
  - Number tiles (Net worth, Spent, Month end, Reports, Compare, Merchant, Net worth) **stack into a column** instead of cutting "$7,775" to "$7,7…".
  - Budget cards move "$207.54 left" under the name, so names like "Groceries" are never split mid-word.
  - The Budgets header wraps its Goals and Quick setup buttons onto their own line instead of breaking "BUDGETS" in two.
  - Transaction names can use two lines instead of being cut short.
- Normal-size layouts are unchanged.

## [2.14.0] - 2026-11-25

(GitHub was briefly down, so the 2.13.0 and 2.14.0 release pages couldn't be published. Everything in them arrived with 2.15.0.)

Round 15: big cleanups and your own order.

### New
- **Select all**: while selecting in Activity, "Select all" picks everything your search and filters match (up to 2,000). Example: search "venmo", Select all → Category → Person-to-Person. Undo puts it all back.
- **Reorder categories**: More → Categories → Reorder, then use the arrows. Your order is used everywhere you pick a category.

## [2.13.0] - 2026-11-25

Round 14: do several at once, and a Sunday summary.

### New
- **Select several transactions**: in Activity, long-press a row, then tap others. The bar at the top lets you change their **category**, **tag** them, **leave them out of budgets**, or **mark them reviewed** in one go, each with Undo. Back (or ✕) stops selecting. TalkBack users get "Select" in the actions menu.
- **Weekly summary** (More → Notifications, off until you turn it on): Sunday morning, "You spent $412 this week, $88 less than last week. Biggest: Hy-Vee, $87."

## [2.12.0] - 2026-11-25

Round 13: calmer transaction details, smarter search, and friendlier first-run screens.

### Auto-update
- Settings → Updates shows when Sprout last checked ("You're up to date. Checked 2 h ago."), so you can tell at a glance that updates are flowing.

### Polish
- **Transaction details** are much calmer. The amount, date, account and the bank's own wording sit together at the top. The category is one row with a **Change** button; the full grid only opens when you tap it (or when the transaction is uncategorized). Picking a category closes it again.
- **First run**: with no balance yet, Home and Budgets no longer shout "$0.00". The empty Activity screen says what you can do (connect, import, or tap Add). The "Import a CSV" link is centered under its button.
- **Add a transaction**: accounts wrap onto a new line instead of running off the screen, and the date has a label.

### Smarter search
- Activity search now also finds **categories** ("groceries"), **tags** ("#travel" or just "travel") and **merchants you renamed** ("Corner coffee").

## [2.11.0] - 2026-11-25

Round 12: polish, from a third screenshot pass (settings, detail pages, dark mode).

### Polish
- **More → settings** are now grouped in cards that match the rows above them. Every switch has a short plain-English line under it ("When a budget reaches 80% and 100%"), and long labels got shorter.
- If notifications are off, the Notifications card says so in red: none of the alerts can reach you until you turn them on.
- Accounts in settings show their type and balance.
- **Dates in lists**: the review inbox, Home's recent list and a category's transactions show the date on each row. On a merchant's page every row was "Hy-Vee, Hy-Vee, Hy-Vee…"; now each row leads with its date instead.
- **Categories** shows what each one cost this month ("Groceries · $192 this month").
- **Budgets → Not budgeted** rows have icons and a quiet "Set budget" button. The names no longer look like links.
- **Money health** details: each part is colored by how it's doing (green, amber, red), with more breathing room.
- Net worth: the tiles say "since last month" and "in total" instead of a bare "up".
- Category icons stay readable in dark mode everywhere, including the category list.

### Fixed
- A test checked a challenge against a hard-coded date and would have failed every build from today on. It now uses the real date.

## [2.10.0] - 2026-11-24

Round 11: more polish, from a second pass of real screenshots (detail screens, sheets, dialogs, first launch).

### Auto-update
- **On mobile data?** Tapping Update asks once: download now, or **Wait for Wi-Fi**, and Sprout updates itself as soon as you're on Wi-Fi. The update card shows what it's waiting for (Wi-Fi or charging), with Cancel.

### Polish
- **Fixed:** on Backup and restore, the automatic-snapshots card sat above the screen title. It's now below the backup buttons, where it belongs.
- Welcome screen: the hero card lines up with the buttons under it.
- Import CSV: a short "Good to know" card (columns are found automatically, re-importing is safe, nothing leaves your phone).
- Year view follows the scroll to the very end, so the newest month and the average are in view.
- Accessibility: Activity rows offer "Change category" and "Leave out of budget" in TalkBack's actions menu, since swiping isn't available there.
- Cards can sit flush in screens that already have margins, so edges line up everywhere.

## [2.9.0] - 2026-11-24

Round 10 — plus a full visual and functional polish pass, screen by screen, in light and dark.

### Auto-update
- Background downloads show a quiet progress bar in the notification shade.
- "While charging" is remembered across restarts, and the update card has a Cancel for it.

### Polish (every main screen was reviewed from real screenshots)
- **Fixed:** in the review inbox, the swipe label "Reviewed" showed through every row. Swipe labels now only appear while you swipe.
- Dark mode: deep category colors (brown, teal, purple) were hard to see on dark backgrounds; they're now lifted automatically.
- Stat tiles in a row are always the same height. The Home "Spent" note is shorter so it fits on one line.
- Home: the month-end forecast matches the other cards. The backup reminder moved below your money (with an icon). If you have accounts but no bank connected, the header says so instead of "No bank connected".
- Until payday counts checking and cash only, not savings, so the daily number is realistic. It shows a calm day count instead of a nearly-empty ring.
- Bills: a summary card in the same style as Home, a tidier "To calendar" button, today circled in the calendar, and paid/due icons in the bill list.
- Flex budgets: no more confusing negative "safe to spend" when no flex amount is set. It explains what to do, and says "Over by" when you're over.
- More: every row has an icon, sections no longer have stray dividers, and "No bank connected" is a proper card.
- Reports: range labels fit; "same" instead of "$0 (0%)"; a legend for the comparison bars; Year in review uses ranked cards.
- Year view opens on your latest months, and long category names wrap at hyphens instead of mid-word.
- Budgets: the section header lines up with its buttons. Goals and Tags have a button right in their empty state.
- Text fields in dialogs use the full width.
- Activity's search hint fits on one line.

### New features
- **Remind me to cancel**: on any bill, turn it on and get a notification 3 days before it renews. Sprout never cancels anything itself; it reminds you.
- **To calendar**: add every bill to your phone's calendar as repeating events (an .ics file, made on the phone).
- **Saved searches** in Activity: set filters, tap "☆ Save search", and it becomes a one-tap chip.
- **Compare months**: Reports → Compare. This month vs last, by category, biggest change first.
- **Pick your payday**: if more than one income repeats, choose which one "Until payday" counts to.
- **Category icons**: choose from 37 icons for any category (Categories → tap one). Icons show in the category list too.

## [2.8.0] - 2026-11-24

Round 9 — the biggest yet.

### Auto-update
- **Update while charging**: on the update card tap "While charging" and Sprout downloads and installs the new version by itself the next time your phone is plugged in (no tap needed on Android 12+).
- Sprout now checks for updates **twice a day** instead of once, so fixes reach you the same day.
- The install step says plainly that a safety copy of your data is saved first.

### New look
- **Swipe actions in Activity**: swipe a transaction right to change its category, or left to leave it out of budgets (swipe again to count it back). Both have Undo, and the row always springs back, so a stray swipe never loses anything.
- A small celebration when your review inbox reaches zero.
- New **Until payday** card on Home (turn it off in Customize Home).

### New features
- **Until payday** (paycheck planner): cash now, minus the bills due before your next paycheck, shown as "$X a day" until payday — or "Short $X" in red if the bills won't fit. Tap the card to see each bill.
- **Rules by amount**: keyword rules can have "at least" and "at most" amounts. Example: Venmo $500 or more → Rent, every other Venmo → Transfers.
- **Auto-tag rules**: Tags → Auto-tag. "Everything containing DELTA gets #Travel" — tags your old transactions right away, and new ones after every sync or import. Removing a tag from one transaction by hand sticks.
- **Live preview** while you write a rule: "Matches 14 transactions" updates as you type.
- **Backup reminder**: a calm Home card a week after you start (then monthly) offering to save an encrypted backup somewhere off the phone. "In 2 weeks" snoozes it.

### Data safety
- Database version 8 adds two optional amount columns to rules (your existing rules keep matching any amount) and a table for auto-tag rules. Updating keeps all your data (tested from every earlier version). Backups include both.

## [2.7.0] - 2026-11-24

Round 8.

### Auto-update
- **Safety snapshot before every update**: once an update is downloaded, Sprout saves a copy of all your data on the phone before it installs. If anything ever looks wrong after an update, Settings → Backup → "Automatic snapshots" puts everything back in one tap. The last 3 are kept; you can also make one any time.

### New look
- Activity's day headers show each day's totals (money out, and money in when there is some).
- A second home-screen widget, **Budgets**: your top budgets with progress bars (follows "Hide amounts").

### New features
- **Rename merchants** (like Monarch and Copilot): on any merchant page tap Rename to turn "SQ *JOES CFE 1234" into "Joe's Coffee". The new name shows everywhere in Sprout: lists, insights, recaps and year in review. Rules still match the bank's text. Clear the name to go back.

### Data safety
- Database version 7 adds one table for your merchant names. Updating keeps all your data (tested from every earlier version). Backups include merchant names.

## [2.6.0] - 2026-11-24

Round 7.

### Auto-update
- **Downloads resume**: if an update download breaks off (bad signal, Wi-Fi drop), the next try picks up where it stopped instead of starting over. The whole file is still checked before installing.
- **Release history**: Settings → Updates → "Release history" shows the notes for recent versions.

### New look
- **Customize Home**: at the bottom of Home, turn cards on or off (stats, money health, challenges, insights, charts, bills, goals, accounts…) for a calmer screen.
- Smooth transitions between screens.

### New features
- **Monthly PDF report**: Reports → PDF makes a one-page summary of this month (totals, budgets vs spending with bars, where it went, biggest purchase) and opens the share sheet to save, print or send it. Made on your phone.
- **Paydays on the bill calendar**: expected paychecks show in green on the calendar and in a list under it, next to your bills.
- **Edit cash transactions**: fix the amount, name or date of anything you added by hand.
- **CSV export** now includes each transaction's tags and split parts.

 - 2026-11-24

Round 6, bigger again.

### Auto-update
- **Hands-free updates** (Android 12 and later): with the update key saved and "Install updates by themselves" on, Sprout downloads new versions on Wi-Fi and installs them in the background. You get a "Sprout updated" note afterwards. If Android ever needs a tap, a notification lets you finish in one.
- Skipped a few versions? "What's new" now lists every version you missed, each under its own heading.

### New look
- Big amounts count up smoothly when they change.
- Home shows soft placeholders while it loads instead of a blank screen.
- A new **Black** theme (Settings → Appearance), easy on OLED screens and on your eyes at night.

### New features
- **Add transactions by hand**: the new "Add" button on Activity records cash spending (or money in) with amount, what it was, category, date and a note. It goes into a "Cash" wallet whose balance Sprout keeps. You can delete these entries; bank transactions stay as the bank sent them.
- **Challenges**: start "No eating out for 7 days", "Groceries under $100 this week", "3 no-spend days this week" or your own, and watch the progress on Home. Win, or try again.
- **Money health score** on Home: a 0–100 guide made of your savings rate, emergency fund (months of spending in cash), Age of Money and budget pace, each with a tip. Tap it for the breakdown.

 - 2026-11-24

Round 5, the biggest yet: the best ideas from the leading budgeting apps (YNAB, Monarch, Copilot, Rocket Money, PocketGuard), done Sprout's way: private, read-only, on your phone.

### Auto-update
- **Test builds channel**: Settings → Updates → "Get test builds too" to get newer builds before they're a release. Updates are now compared by build number, so nothing is ever offered twice or skipped.

### New look
- **Reports** (like Monarch and Copilot's Cash Flow): income vs spending per month, the share of each category as a donut, and spending by account, for this month, 3, 6 or 12 months. Totals for what came in, what went out and what you kept.
- **Year in review** (like Copilot): your year's spending, what you kept, month-by-month bars, top categories, top places, lightest and biggest month, biggest purchase and no-spend days. Flip back through earlier years.
- Home gets quick links (Reports, Year in review, Net worth, Tags, Debt plan, Goals), an Age of Money card and your watchlist.
- A badge on the Activity tab shows how many new transactions are waiting for review.

### New features
- **Review inbox** (like Copilot): new transactions wait in "To review" until you've seen them. Fix a category, then swipe right or tap ✓, or "All reviewed". Undo is one tap. Your history and imports don't fill it up.
- **Flex budgeting** (like Monarch): switch Budgets to "Flex" for one amount for everyday spending, with fixed costs (rent, bills, subscriptions; you choose) tracked separately. Safe to spend follows it. Your category budgets are kept if you switch back.
- **Pace** (like PocketGuard): each budget shows "On pace for $X" and warns early if you're heading over.
- **Planned expenses** (like YNAB targets and Monarch's non-monthly expenses): add a planned expense such as "Car registration, $240, due June". Sprout works out a little to set aside each month and keeps it out of safe to spend.
- **Age of Money** (like YNAB): how many days old the money you spend is. Aim for 30+.
- **Price change alerts** (like Copilot and Rocket Money): "Netflix went up from $15.49 to $17.99" in Insights and on the Bills tab.
- **Receipt photos**: attach photos to any transaction with Android's photo picker (no permission needed). They're kept privately in Sprout; tap to view or delete.
- **Watchlist** (like Rocket Money): "Pin to Home" on any category or merchant page to see this month's total on Home.
- **What your month costs** (like YNAB's "Cost to be me"): your budgets plus bills outside them, as one number.

### Data safety
- Database version 6 adds a review date to transactions, a type to goals, and a receipts table. Updating keeps all your data (tested from every earlier version), and everything you already have counts as reviewed. Backups include review dates and goal types; receipt photos aren't in backups.

 - 2026-11-24

Round 4 of the overhaul.

### Auto-update
- **Updates install inside Sprout.** No browser, no Downloads folder: tap Update, then Install, and Android's short confirmation appears on top of Sprout. On Android 12 and later, once Sprout has installed one update itself, later updates can install without even that step. A notification offers to reopen Sprout when it's done.
- **Paste your update key in the app**: Settings → Updates → "Update inside Sprout". Sprout checks the key with GitHub before saving it, and stores it encrypted on your phone. No repo settings needed.
- The update card shows the download size, and offers "Try again" if a download breaks off or "Install again" if an install is cancelled.

### New look
- The review flow shows progress ("12 done") with a bar and a big card for each transaction, animating to the next one.
- A small vibration when you pick a category.
- Quick setup can switch between "From my spending" and a 50/30/20 plan.

### New features
- **50/30/20 budgets**: Quick setup splits your average income into needs (50%), wants (30%) and savings (20%), shared across categories by what you usually spend.
- **Monthly recap**: in the first week of each month, Home shows how last month went: spent vs income, the change from the month before, where most money went, the biggest purchase and no-spend days.

 - 2026-11-24

Round 3 of the overhaul.

### Auto-update
- On Wi-Fi, Sprout can download a new version in the background so it's ready to install the moment you open the app ("Download updates on Wi-Fi in the background" in Settings → Updates; needs the update key).
- A downloaded update is still offered after Sprout restarts, and is cleaned up once installed.

### New look
- Category pages open with a progress ring around the category's icon, and each merchant links to its own page.
- Bills are cards with a date badge (month over day).
- A rounded search bar on the Activity tab.

### New features
- **Spending calendar** on Home: this month by day, darker days cost more, and ✓ marks your no-spend days (with a count).
- **Bill reminders**: turn on "Bill reminders (the day before)" in Settings → Notifications for a heads-up like "Rent is due tomorrow". Amounts stay hidden on the lock screen.

 - 2026-11-24

Round 2 of the overhaul.

### Auto-update
- A quiet daily check: when a new version is out you get one "Sprout X is ready" notification (turn it off with the automatic-check switch in Settings → Updates).
- After updating, a "What's new" sheet shows this version's notes once. Settings → Updates → "What's new in this version" shows them any time.
- The downloaded update file is cleaned up after installing.

### New look
- Redesigned welcome screen, transaction sheet (big icon and amount), budget cards with an edit button, savings goals with progress rings, illustrated empty screens, and Settings rows as cards with clear section labels.
- The Budgets tab opens with the same "Safe to spend" dial as Home.

### New features
- **Home-screen widget**: long-press your home screen → Widgets → Sprout to see safe to spend and your daily amount at a glance. It follows "Hide amounts".
- **Merchant pages**: open any transaction and tap "Everything at …" for this year's total, average per visit, a 6-month chart and every purchase there.
- **Debt payoff plan** (Settings → Explore): enter what you can pay each month and see your debt-free date, total interest, the payoff order and how much you save vs minimums. Choose "Least interest" or "Quick wins". A plan only: Sprout never makes payments.

 - 2026-11-24

A big round: Sprout updates itself, looks completely new, and does a lot more.

### Auto-update
- Sprout checks for new versions (at most every 6 hours, and from Settings → Updates). A calm card on Home says "Sprout X is ready" with what's new: tap **Update**, then **Install**. Your data stays. The first time, Android asks you to allow Sprout to install updates. Downloads are checked before installing.
- Until a read-only update key is set up for this private repo (see the README), the card opens the Releases page instead.

### New look
- A new design: Sprout's own evergreen palette, the Manrope typeface (built in), rounded cards and smooth animations, in light and dark. Settings → Appearance lets you pick Light, Dark or your phone's setting, or use your wallpaper colors.
- New Home: a greeting, a big "Safe to spend" card with a progress ring and your daily amount, quick stats (net worth, spending vs last month, month-end cash), a "Where it went" donut chart, a 6-month spending chart, upcoming bills you can swipe through, and your accounts with icons.
- Transactions show their category's icon, with cleaner rows. Budgets are cards with icons and thicker progress bars.
- The eye on Home hides amounts for using Sprout in public (or turn it on in Settings).

### New features
- **Insights**: plain-language notes on Home, like "Eating Out is above usual", "Most spent at Target" or "Keeping 20% of your income".
- **Net worth over time**: tap Net worth on Home for a 12-month chart and month-by-month numbers.
- **Year view**: every category, every month, as a heat grid (Settings → Explore).
- **Tags**: add tags like "Chicago trip" to any transaction, see totals per tag, and filter by tag.
- **Search by amount**: type 42.10, >100, <5 or 20-50 in the search box.
- **Subscriptions total** at the top of Bills: what your repeating charges cost per month and per year.
- Accounts on Home show when each was last updated.

### Fixed
- A CSV with an absurdly long number no longer stops the import (found by new random-input tests).

### Data safety
- Database version 5 adds two tables for tags. Updating keeps all your data (tested from every earlier version). Backups include tags; older backups still restore.

 - 2026-11-24

### Added
- CSV import understands European amounts like "1.234,56" (detected automatically; there's a "Comma decimals" switch if it guesses wrong).
- CSV import uses your bank's own Category column when the file has one: "Food & Drink" becomes Eating Out, "Gas/Automotive" becomes Gas & Fuel, a "Pets" category matches yours by name. Your own merchant rules still win. You can pick or clear the column on the import screen.

 - 2026-11-24

### Added
- Category icons: every built-in category has its own icon (a cart for Groceries, a fork and knife for Eating Out…) in the category picker and on transactions, so you can find things faster. Categories you add get a tag icon.

 - 2026-11-24

### Changed
- Easier on your battery: while Battery Saver is on, the 6-hourly background sync waits until your data is a day old. Pull-to-refresh and "Sync now" always sync right away.

 - 2026-11-24

### Tests
- The budget editor, the split dialog and transaction list labels are now tested by tapping through them automatically, so fixes to these screens can't quietly break them.

 - 2026-11-24

### Added
- Split transactions show a small "+2 split" label in the list (and TalkBack says "split across categories").
- A category with rollover on explains its number: "Set $300 · rolled over +$180 · available $480".

 - 2026-11-24

### Fixed
- The month-end forecast now follows split transactions: a part moved to Transfers no longer counts as everyday spending.

 - 2026-11-24

### Added
- Split a transaction across categories. Open a posted transaction and tap "Split across categories", then move part of it (say $30 of a $100 Target run) to another category. The rest stays in the transaction's own category, so the parts always add up. Budgets, safe to spend, category history and the category's transaction list all follow the split. "Remove split" puts it back.

### Data safety
- Database version 4 adds one new table for splits. Updating keeps all your data (tested from every earlier version). Backups include splits; older backups still restore.

 - 2026-11-24

### Added
- Budget rollover. Tap a budget and turn on "Roll over leftover money": what you don't spend this month is added to next month's budget, and overspending comes out of it. Each budget shows "(incl. $X rolled over)" when it applies. Off by default, set per category.

### Data safety
- Database version 3 adds one new table for rollover settings. Updating keeps all your transactions, budgets, rules, goals and settings (tested from version 1 and 2). Backups now include rollover settings; older backups still restore.

 - 2026-11-24

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
