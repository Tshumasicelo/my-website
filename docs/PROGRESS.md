# ASPECTS: where we are

_Last updated 26 October 2026._

## Live now: version 1.2.0
Published from `main` by GitHub Pages at https://tshumasicelo.github.io/my-website/.
CI (`Check ASPECTS web app`) is green on `main` for WebKit (Safari's engine) and Chromium.

### What the app does
- **Money:** income and expenses with 8 categories, notes and dates; monthly
  budgets with warnings; recurring entries (salary, rent, subscriptions); every
  month kept forever, with an All time view and search; undo after deleting.
- **Micro-goals:** tasks with priorities, due dates and filters; tasks that cost
  money log an expense when ticked; a 25/5 focus timer; streaks and XP levels.
- **Savings goals:** progress rings, time-to-goal, add or take money back.
- **Reports:** Settings → Reports → Download PDF for a week, a month or all
  time. The PDF is written in the app (no library, works offline).
- **Backups in plain words:** Save a backup / Restore from a backup; restoring
  over existing records offers "Add what's missing" or "Replace everything".
- **Opening animation:** "Less guessing, more growing." Locked: it plays in
  full (4.6 s) on every open, restart and refresh. It can't be skipped or
  turned off, and shortcuts are ignored while it plays.
- **Safety:** data lives only on the device (localStorage key `aspects.v2`);
  unreadable or newer-format data is never overwritten; versioned migrations;
  tabs stay in sync; "Check for updates / Update now" in Settings.
- **UI:** no keyboard letters anywhere, a 🔍 search menu, dark/light/system themes.

## Decisions made with the owner
- The opening animation stays locked: no Settings switch, no skipping.
- PDF reports replaced the CSV spreadsheet export.
- Plain language for non-technical users (no "JSON", "CSV", "Import", "Merge").
- Keyboard-shortcut letters are hidden everywhere (the shortcuts still work on computers).

## Releasing a change
See `RELEASING.md`. In short:
1. Bump the version in `index.html` (APP_VERSION), `sw.js` (CACHE_VERSION) and `version.json`.
2. Run `node scripts/check-web-app.mjs`, plus the browser tests in `tests/e2e`
   (`npm ci`, then `BLOCK_CDN=1 node run.mjs` where the CDN is blocked).
3. Open a PR, wait for the green checks, then squash and merge.
4. Users get "Update now" in Settings; their data is untouched.

## Other material
- `docs/brochure/`: the 12-slide product overview PDF and how to rebuild it.
- `tv-launcher/`: ASPECTS TV, a separate Android TV launcher with its own CI and releases.

## Ideas for next time
- A shorter 5-slide brochure for social media, or slides as images.
- An iPhone check of the real Safari app: add entries, close and reopen;
  All time; Check for updates; Add to Home Screen (its data is separate from
  Safari's); open with Airplane mode on; Download PDF; Undo a delete.
- Possible app ideas: reminders for due tasks, a yearly summary report.
