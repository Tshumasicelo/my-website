// End-to-end tests for ASPECTS: saved history survives upgrades, restarts,
// updates and bad data; import/export and the update flow behave.
//
//   cd tests/e2e && npm ci && npx playwright install webkit
//   BROWSER=webkit node run.mjs          (or chromium / firefox)
//
// It starts from BASELINE_REF (the build users had before these checks
// existed), fills it with data, then deploys the working-tree build over it,
// exactly like a GitHub Pages release. The server caches like Pages
// (max-age=600). Set BLOCK_CDN=1 where jsDelivr/Google Fonts are unreachable.
import { execSync, spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import * as playwright from "playwright";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const BROWSER = process.env.BROWSER || "chromium";
const BASELINE = process.env.BASELINE_REF || "c3e48d2";
const BLOCK_CDN = process.env.BLOCK_CDN === "1";
const PORT = Number(process.env.PORT || 8765);
const work = fs.mkdtempSync(path.join(os.tmpdir(), "aspects-e2e-"));
const site = path.join(work, "site"), profile = path.join(work, "profile");
const out = path.join(here, "output", BROWSER);
fs.mkdirSync(out, { recursive: true });

const BASE = "http://localhost:" + PORT + "/";
const KEY = "aspects.v2";
const results = [];
const check = (name, ok, detail = "") => { results.push({ name, ok: !!ok, detail }); console.log((ok ? "PASS " : "FAIL ") + name + (detail ? "  — " + detail : "")); };

/* ---------- deploying builds into the served folder ---------- */
function deploy(kind) {
  fs.mkdirSync(path.join(site, "icons"), { recursive: true });
  for (const f of fs.readdirSync(path.join(repo, "icons"))) fs.copyFileSync(path.join(repo, "icons", f), path.join(site, "icons", f));
  if (kind === "old") {
    for (const f of ["index.html", "sw.js", "manifest.webmanifest"])
      fs.writeFileSync(path.join(site, f), execSync(`git -C "${repo}" show ${BASELINE}:${f}`));
    fs.rmSync(path.join(site, "version.json"), { force: true });
    return;
  }
  let html = fs.readFileSync(path.join(repo, "index.html"), "utf8");
  let sw = fs.readFileSync(path.join(repo, "sw.js"), "utf8");
  let ver = fs.readFileSync(path.join(repo, "version.json"), "utf8");
  if (kind === "lagging") {   // version.json already says 9.9.9, the page isn't published yet
    ver = ver.replace('"version": "1.1.0"', '"version": "9.9.9"').replace(/"notes": "[^"]*"/, '"notes": "Test notes"');
  }
  if (kind === "next") {   // what the next real deploy looks like: all three bumped
    html = html.replace('const APP_VERSION = "1.1.0"', 'const APP_VERSION = "1.1.1"');
    sw = sw.replace('CACHE_VERSION = "v1.1.0"', 'CACHE_VERSION = "v1.1.1"');
    ver = ver.replace('"version": "1.1.0"', '"version": "1.1.1"');
  }
  fs.writeFileSync(path.join(site, "index.html"), html);
  fs.writeFileSync(path.join(site, "sw.js"), sw);
  fs.writeFileSync(path.join(site, "version.json"), ver);
  fs.copyFileSync(path.join(repo, "manifest.webmanifest"), path.join(site, "manifest.webmanifest"));
}

/* ---------- helpers ---------- */
const sleep = ms => new Promise(r => setTimeout(r, ms));
async function launch() {
  const ctx = await playwright[BROWSER].launchPersistentContext(profile, { headless: true, acceptDownloads: true });
  if (BLOCK_CDN) await ctx.route(/^https:\/\/(cdn\.jsdelivr\.net|fonts\.googleapis\.com|fonts\.gstatic\.com)\//, r => r.abort());
  return ctx;
}
const raw = page => page.evaluate(k => localStorage.getItem(k), KEY);
const state = async page => JSON.parse(await raw(page));
const toasts = page => page.evaluate(() => Array.from(document.querySelectorAll("#toasts .toast")).map(t => t.textContent).join(" | "));
async function waitToast(page, re, timeout = 6000) {
  const end = Date.now() + timeout;
  while (Date.now() < end) { const t = await toasts(page); if (re.test(t)) return t; await sleep(150); }
  return await toasts(page);
}
async function addViaUI(page, type, amount, note, date) {
  await page.evaluate(() => { document.querySelectorAll(".view").forEach(v => v.hidden = v.id !== "view-overview"); });
  await page.click(`#view-overview [data-open-tx="${type}"]`);
  await page.fill("#txAmount", String(amount));
  await page.fill("#txNote", note);
  if (date) await page.fill("#txDate", date);
  await page.click("#txConfirm");
  await sleep(200);
}
async function goto(page, url = BASE) { await page.goto(url, { waitUntil: "load" }); await sleep(600); }
const appVersion = page => page.evaluate(() => (typeof APP_VERSION !== "undefined" ? APP_VERSION : "old"));

const now = new Date();
const ymShift = n => { const d = new Date(now.getFullYear(), now.getMonth() + n, 10, 12); return d; };
const sept = ymShift(-1), aug = ymShift(-2), lastYear = new Date(now.getFullYear() - 1, 4, 3, 9);

/* ======================================================================== */
console.log("browser:", BROWSER, "· baseline:", BASELINE, "· cdn:", BLOCK_CDN ? "blocked" : "live");
deploy("old");
const server = spawn(process.execPath, [path.join(here, "server.mjs"), site, String(PORT)], { stdio: "ignore" });
await sleep(800);
let ctx = await launch();
let page = await ctx.newPage();
const errors = [];
page.on("pageerror", e => errors.push(e.message));

/* ---- 1. Data created on the version that is live today ---- */
await goto(page);
await page.evaluate(() => navigator.serviceWorker.ready);
await goto(page);   // now controlled by the old worker
check("old build: page controlled by service worker", await page.evaluate(() => !!navigator.serviceWorker.controller));
await page.evaluate(({ KEY, a, b, c }) => {
  const s = JSON.parse(localStorage.getItem(KEY));
  s.tx.push({ id: "hist-aug", type: "income", amount: 5000, category: "General", note: "August salary", ts: a });
  s.tx.push({ id: "hist-sep", type: "expense", amount: 1200.5, category: "Housing", note: "September rent", ts: b });
  s.tx.push({ id: "hist-old", type: "expense", amount: 99, category: "Food", note: "Last year's lunch", ts: c });
  localStorage.setItem(KEY, JSON.stringify(s));
}, { KEY, a: aug.toISOString(), b: sept.toISOString(), c: lastYear.toISOString() });
await goto(page);
await addViaUI(page, "expense", 42.25, "Coffee beans");
const before = await state(page);
check("old build: 4 transactions saved before upgrade", before.tx.length === 4, before.tx.length + " rows");

/* ---- 2. Deploy the new build; an existing user opens the app ---- */
deploy("new");
await goto(page);
let v = await appVersion(page);
if (v === "old") {
  // HTTP cache (max-age=600) can serve the old page; the old build's own toast is the way forward.
  const t = await waitToast(page, /new version is ready/i, 8000);
  check("upgrade path: old build announces the new version", /new version is ready/i.test(t), t);
  await Promise.all([page.waitForEvent("load", { timeout: 10000 }).catch(() => {}), page.click("#toasts .toast button")]);
  await sleep(1200);
  v = await appVersion(page);
}
check("upgrade: new build is running", v === "1.1.0", v);
const after = await state(page);
check("upgrade: every transaction survived", before.tx.every(t => after.tx.some(x => x.id === t.id && x.amount === t.amount && x.ts === t.ts && x.note === t.note && x.category === t.category)),
      after.tx.length + " rows");
check("upgrade: no rows set aside", after.invalidTx.length === 0);
let cachesNow = [];
for (let i = 0; i < 40; i++) {   // the quiet swap waits for the background version check (~2.5 s)
  cachesNow = await page.evaluate(() => caches.keys());
  if (!cachesNow.some(k => k.endsWith("v1.0.0"))) break;
  await sleep(250);
}
check("upgrade: old caches replaced by v1.1.0", cachesNow.some(k => k.endsWith("v1.1.0")) && !cachesNow.some(k => k.endsWith("v1.0.0")), cachesNow.join(","));

if (!BLOCK_CDN) {
  let charted = false;
  for (let i = 0; i < 40 && !charted; i++) { charted = await page.evaluate(() => typeof Chart !== "undefined"); if (!charted) await sleep(250); }
  check("charts: pinned Chart.js passes its integrity check and loads", charted);
}

/* ---- 3. Balance & history ---- */
const sum = after.tx.reduce((a, t) => a + (t.type === "income" ? t.amount : -t.amount), 0);
const shown = (await page.textContent("#balanceDisplay")).replace(/[R,]/g, "");
check("calc: balance equals ledger sum", Math.abs(Number(shown) - sum) < 0.005, shown + " vs " + sum.toFixed(2));
await page.click('.tab[data-view="money"]');
const opts = await page.$$eval("#monthPicker option", o => o.map(x => x.value));
const ym = d => d.toLocaleDateString("en-CA").slice(0, 7);
check("history: picker lists past months and All time", ["all", ym(aug), ym(sept), ym(lastYear)].every(x => opts.includes(x)), opts.join(","));
await page.selectOption("#monthPicker", ym(sept));
check("history: September shows its rent", /September rent/.test(await page.textContent("#txList")));
await page.selectOption("#monthPicker", "all");
check("history: All time lists every row", (await page.textContent("#txCount")).startsWith("4 "), await page.textContent("#txCount"));
check("history: older-year rows show the year", (await page.textContent("#txList")).includes(String(lastYear.getFullYear())));
await page.fill("#txSearch", "salary");
check("history: search works across all time", (await page.textContent("#txCount")).startsWith("1 "));
await page.fill("#txSearch", "");

/* ---- 4. Full browser restart ---- */
await ctx.close();
ctx = await launch();
page = await ctx.newPage();
page.on("pageerror", e => errors.push(e.message));
await goto(page);
check("restart: all rows still there after closing the browser", (await state(page)).tx.length === 4);

/* ---- 5. Undo delete ---- */
await page.click('.tab[data-view="money"]');
await page.selectOption("#monthPicker", "all");
await page.click('#txList [data-del-tx="hist-sep"]');
check("delete: row removed", !(await state(page)).tx.some(t => t.id === "hist-sep"));
await page.click("#toasts .toast button");
await sleep(200);
check("delete: Undo restores and saves it", (await state(page)).tx.some(t => t.id === "hist-sep"));

/* ---- 6. CSV ---- */
await page.evaluate(KEY => { const s = JSON.parse(localStorage.getItem(KEY)); s.tx.push({ id: "f1", type: "expense", amount: 1, category: "General", note: "=HYPERLINK(\"x\")", ts: new Date().toISOString() }); localStorage.setItem(KEY, JSON.stringify(s)); }, KEY);
await goto(page);
await page.click('.tab[data-view="settings"]');
const [csvDl] = await Promise.all([page.waitForEvent("download"), page.click("#csvBtn")]);
const csv = fs.readFileSync(await csvDl.path(), "utf8");
const lines = csv.trim().split(/\r\n/);
check("csv: header + one line per transaction", lines.length === 6 && lines[0].includes("Date,Time,Type"), lines.length + " lines");
check("csv: formula text is neutralised", csv.includes(`"'=HYPERLINK(""x"")"`));
check("csv: amounts stay numeric", /,1200\.50,-1200\.50,/.test(csv));

/* ---- 7. JSON backup, merge, replace, undo ---- */
const [jsonDl] = await Promise.all([page.waitForEvent("download"), page.click("#exportBtn")]);
const backup = JSON.parse(fs.readFileSync(await jsonDl.path(), "utf8"));
check("backup: contains every transaction", backup.tx.length === 5);
check("backup: last-backup date recorded", !!(await state(page)).lastBackup);
const incoming = JSON.parse(JSON.stringify(backup));
incoming.tx.push({ id: "from-phone", type: "income", amount: 300, category: "General", note: "Sold old phone", ts: sept.toISOString() });
incoming.tx.push({ id: "copy-other-id", type: "expense", amount: 1200.5, category: "Housing", note: "September rent", ts: sept.toISOString() }); // same content, new id
incoming.tx.push({ type: "expense", amount: "abc", ts: "yesterday" });   // malformed
const incPath = path.join(work, "incoming.json");
fs.writeFileSync(incPath, JSON.stringify(incoming));
await page.setInputFiles("#importFile", incPath);
await sleep(300);
check("import: asks merge or replace", /Merge or replace/.test(await page.textContent("#confirmTitle")));
check("import: summary mentions unreadable rows", /1 unreadable/.test(await page.textContent("#confirmBody")));
check("import: Replace locked until REPLACE typed", await page.isDisabled("#confirmYes"));
await page.focus("#confirmPhrase");
await page.keyboard.press("Enter");
check("import: Enter does not trigger Merge or Replace", await page.evaluate(() => document.getElementById("confirmModal").classList.contains("open")));
await page.click("#confirmAlt");
await sleep(300);
let s = await state(page);
check("merge: adds only the new row, skips duplicates", s.tx.length === 6 && s.tx.some(t => t.id === "from-phone"), s.tx.length + " rows; " + await toasts(page));
check("merge: malformed row kept aside, not counted", s.invalidTx.length === 1);
await page.setInputFiles("#importFile", incPath);
await sleep(300);
await page.fill("#confirmPhrase", "replace");
const [safety] = await Promise.all([page.waitForEvent("download"), page.click("#confirmYes")]);
check("replace: downloads a safety copy first", /aspects-before-import-/.test(safety.suggestedFilename()));
await sleep(300);
s = await state(page);
check("replace: data now equals the backup", s.tx.length === 7);   // backup had 5 + 2 valid extra
const undoBtn = await page.$("#toasts .toast button");
await undoBtn.click();
await sleep(300);
check("replace: Undo puts previous data back", (await state(page)).tx.length === 6);

/* ---- 8. Two tabs never overwrite each other ---- */
const tabB = await ctx.newPage();
await goto(tabB);
await addViaUI(page, "income", 11, "From tab A");
await sleep(300);
await addViaUI(tabB, "income", 22, "From tab B");
await sleep(300);
s = await state(page);
check("multi-tab: both tabs' entries kept", s.tx.some(t => t.note === "From tab A") && s.tx.some(t => t.note === "From tab B"), s.tx.length + " rows");
await tabB.close();

/* ---- 9. Update check & Update now (GitHub Pages lag simulated) ---- */
deploy("lagging");
await page.click('.tab[data-view="settings"]');
await page.click("#checkUpdateBtn");
await sleep(800);
check("update: newer version detected", !(await page.isHidden("#updateNowBtn")) && /9\.9\.9/.test(await page.textContent("#updateStatus")));
check("update: release notes shown", /Test notes/.test(await page.textContent("#updateNotes")));
const rowsBefore = (await state(page)).tx.length;
await Promise.all([page.waitForURL(/updated=/).catch(() => {}), page.click("#updateNowBtn")]);
await sleep(1500);
check("update: URL cleaned after reload", !/updated=/.test(page.url()), page.url());
let t = await waitToast(page, /isn't being served yet|Running version/);
check("update: honest message when the new build isn't live yet", /isn't being served yet/.test(t), t);
deploy("new");
await page.click('.tab[data-view="settings"]');
await page.click("#checkUpdateBtn");
t = await waitToast(page, /latest version/);
check("update: up-to-date reported", /latest version \(1\.1\.0\)/.test(t), t);

/* ---- 10. A real next deploy (1.1.1) picked up by an open app ---- */
deploy("next");
await page.click("#checkUpdateBtn");
await sleep(2500);
check("next deploy: Update now offered", !(await page.isHidden("#updateNowBtn")), await page.textContent("#updateStatus"));
await Promise.all([page.waitForURL(/updated=|localhost:8765\/$/, { timeout: 10000 }).catch(() => {}), page.click("#updateNowBtn")]);
await sleep(2500);
check("next deploy: now running 1.1.1", (await appVersion(page)) === "1.1.1", await appVersion(page));
t = await waitToast(page, /Running version/);
check("next deploy: confirms data intact", /Running version 1\.1\.1 — all \d+ transactions are here/.test(t), t);
check("next deploy: no data lost", (await state(page)).tx.length === rowsBefore);

/* ---- 11. Offline ---- */
await ctx.setOffline(true);
await goto(page);
check("offline: app opens from the offline copy", (await appVersion(page)) === "1.1.1");
check("offline: data present", (await state(page)).tx.length === rowsBefore);
await page.click('.tab[data-view="settings"]');
await page.click("#checkUpdateBtn");
check("offline: check explains it is offline", /offline/i.test(await waitToast(page, /offline/i)));
await ctx.setOffline(false);
deploy("new");   // back to the 1.1.0 build for the remaining tests

/* ---- 12. Unreadable data is never overwritten ---- */
await page.evaluate(KEY => localStorage.setItem(KEY, '{"tx":[{"id":"x"'), KEY);
await goto(page, BASE + "?updated=1");
const keys = await page.evaluate(() => Object.keys(localStorage));
const rescue = keys.find(k => k.startsWith("aspects.v2.unreadable-"));
check("corrupt: original kept under a rescue key", rescue && (await page.evaluate(k => localStorage.getItem(k), rescue)) === '{"tx":[{"id":"x"', rescue);
check("corrupt: user is told", /could not be read/.test(await waitToast(page, /could not be read/)));
await page.click('.tab[data-view="settings"]');
check("corrupt: download button offered", !(await page.isHidden("#rescueBtn")));

/* ---- 13. Data from a newer schema is never overwritten ---- */
const newer = JSON.stringify({ version: 3, tx: [{ id: "n1", type: "income", amount: 10, ts: new Date().toISOString(), currency: "ZAR" }], futureField: true });
await page.evaluate(([KEY, v]) => localStorage.setItem(KEY, v), [KEY, newer]);
await goto(page);
await addViaUI(page, "income", 5, "should not save");
check("newer schema: stored data left untouched", (await raw(page)) === newer);
check("newer schema: no false \"recorded\" message", !/recorded/.test(await toasts(page)));
check("newer schema: user told saving is off", /Not saved|newer version/.test(await toasts(page)), await toasts(page));

/* ---- 14. Hand-edited rows are repaired or set aside ---- */
await page.evaluate(KEY => localStorage.setItem(KEY, JSON.stringify({ version: 2, tx: [
  { id: "a", type: "expense", amount: "25.50", ts: new Date().toISOString() },
  { id: "b", type: "bogus", amount: 5, ts: new Date().toISOString() },
  { id: "c", type: "income", amount: 10, ts: "10:32:15 AM" } ] })), KEY);
await goto(page);
s = await state(page);
check("validation: numeric string repaired", s.tx.length === 1 && s.tx[0].amount === 25.5);
check("validation: unreadable rows kept in invalidTx", s.invalidTx.length === 2 && s.invalidTx.some(t => t.id === "b"));
check("validation: balance correct", (await page.textContent("#balanceDisplay")).includes("-R25.50"), await page.textContent("#balanceDisplay"));

/* ---- 15. v1 data still migrates, legacy keys removed only after saving ---- */
await page.evaluate(() => { localStorage.clear(); localStorage.setItem("aspects_balance", "150"); localStorage.setItem("aspects_logs", JSON.stringify([{ type: "income", amount: 100 }])); });
await goto(page);
s = await state(page);
check("v1: imported with opening balance", s.tx.length === 2 && Math.abs(s.tx.reduce((a, t) => a + (t.type === "income" ? t.amount : -t.amount), 0) - 150) < 0.005);
check("v1: legacy keys cleared after save", await page.evaluate(() => localStorage.getItem("aspects_balance") === null));

await page.setViewportSize({ width: 390, height: 900 });
await page.click('.tab[data-view="settings"]');
await sleep(400);
await page.screenshot({ path: path.join(out, "settings-phone.png"), fullPage: true });
await page.click('.tab[data-view="money"]');
await page.selectOption("#monthPicker", "all");
await page.screenshot({ path: path.join(out, "money-all-time-phone.png") });

/* ---- 16. Erase all removes safety copies too ---- */
await page.evaluate(KEY => localStorage.setItem(KEY + ".unreadable-1", "x"), KEY);
await page.click('.tab[data-view="settings"]');
await page.click("#nukeBtn");
await page.fill("#confirmPhrase", "ERASE");
await page.click("#confirmYes");
await sleep(300);
check("erase: safety copies removed", await page.evaluate(() => !Object.keys(localStorage).some(k => k.startsWith("aspects.v2."))));

check("no uncaught page errors", errors.length === 0, errors.join(" / "));
await ctx.close();
server.kill();
fs.rmSync(work, { recursive: true, force: true });

const failed = results.filter(r => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
process.exit(failed.length ? 1 : 0);
