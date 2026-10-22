# Releasing an ASPECTS web update

ASPECTS is served by GitHub Pages from this repository. Users' data lives in
their own browser (`localStorage` key `aspects.v2`) — a release never touches it.

## Every release

1. Make your changes to `index.html` (and `sw.js` if needed).
2. Bump the version **in all three places**, to the same number:
   - `index.html` → `const APP_VERSION = "1.2.0";`
   - `sw.js` → `const CACHE_VERSION = "v1.2.0";`
   - `version.json` → `"version": "1.2.0"`, plus `released` and a one-line `notes`
     (shown to users under *Settings → Install & updates*).
3. Check locally: `node scripts/check-web-app.mjs`
   (the **Check ASPECTS web app** workflow runs the same check on every push).
4. Merge to the branch GitHub Pages publishes from.

Within a few minutes, open copies offer **Update now**; others pick up the new
version the next time they open the app online.

## Changing the shape of saved data

Never rename the `aspects.v2` key. Instead:

1. Bump `SCHEMA_VERSION` in `index.html`.
2. Add a step to `MIGRATIONS`, keyed by the version it upgrades **from**.

On first load the app keeps an untouched copy (`aspects.v2.pre-v<N>`), runs the
steps, and checks that no transactions went missing. If anything fails, it
keeps the original and stops saving rather than overwrite it.
