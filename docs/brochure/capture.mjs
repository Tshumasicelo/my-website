// Screenshots of ASPECTS with realistic (made-up) demo data, for the brochure.
//   node docs/brochure/capture.mjs            -> docs/brochure/shots/*.png
// Uses the Playwright install from tests/e2e (cd tests/e2e && npm ci).
// Charts: set CHART_JS to a local chart.umd.js (npm pack chart.js@4.4.1) when
// jsDelivr is unreachable; otherwise the real CDN file is used.
import { spawn } from "node:child_process";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import fs from "node:fs";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, "../..");
const playwright = createRequire(path.join(repo, "tests/e2e/package.json"))("playwright");
const OUT = process.argv[2] || path.join(here, "shots");
const CHART = process.env.CHART_JS || "";
fs.mkdirSync(OUT, { recursive: true });
const server = spawn(process.execPath, [path.join(repo, "tests/e2e/server.mjs"), repo, "8830"], { stdio: "ignore" });
await new Promise(r => setTimeout(r, 700));
const browser = await playwright.chromium.launch();
const sleep = ms => new Promise(r => setTimeout(r, ms));

/* ---------- demo data: three months of a South African professional ---------- */
function demoState() {
  const now = new Date(), Y = now.getFullYear(), M = now.getMonth();
  const at = (mOff, d, h = 9, min = 0) => new Date(Y, M + mOff, d, h, min).toISOString();
  const tx = [];
  let n = 0;
  const add = (type, amount, category, note, ts, goalId) => tx.push({ id: "d" + (n++), type, amount, category, note, ts, ...(goalId ? { goalId } : {}) });
  for (const m of [-2, -1, 0]) {
    add("income", 18500, "General", "Salary", at(m, 1, 8));
    add("expense", 6200, "Housing", "Rent", at(m, 1, 9));
    add("expense", 199, "Bills", "Netflix", at(m, 3, 7));
    add("expense", 450, "Health", "Gym membership", at(m, 2, 7));
    add("goal", 1500, "General", "", at(m, 2, 10), "g1");
    add("goal", 1000, "General", "", at(m, 2, 10, 5), "g2");
    add("goal", 800, "General", "", at(m, 2, 10, 10), "g3");
  }
  const spend = [
    [-2, 4, "Food", "Groceries – Checkers", 1180], [-2, 6, "Transport", "Uber to work", 145], [-2, 9, "Bills", "Electricity (prepaid)", 899],
    [-2, 12, "Food", "Groceries – Woolworths", 960], [-2, 15, "Leisure", "Movies with friends", 320], [-2, 19, "Transport", "Taxi fare", 260],
    [-2, 22, "Food", "Groceries – Pick n Pay", 1240], [-2, 26, "Tech", "Phone screen repair", 650], [-2, 28, "Leisure", "Braai supplies", 410],
    [-1, 5, "Food", "Groceries – Checkers", 1320], [-1, 7, "Transport", "Petrol", 700], [-1, 10, "Bills", "Electricity (prepaid)", 899],
    [-1, 13, "Health", "Pharmacy", 230], [-1, 14, "Food", "Groceries – Woolworths", 880], [-1, 18, "Leisure", "Dinner out", 540],
    [-1, 21, "Transport", "Uber to work", 165], [-1, 24, "Food", "Groceries – Pick n Pay", 1150], [-1, 27, "Tech", "Wireless earbuds", 799],
    [0, 4, "Food", "Groceries – Checkers", 1260], [0, 6, "Transport", "Petrol", 650], [0, 8, "Bills", "Electricity (prepaid)", 899],
    [0, 11, "Food", "Groceries – Woolworths", 920], [0, 13, "Leisure", "Concert tickets", 680], [0, 15, "Bills", "Airtime & data", 249],
    [0, 17, "Transport", "Taxi fare", 240], [0, 19, "Food", "Groceries – Pick n Pay", 1090], [0, 21, "Health", "Doctor's visit", 520],
    [0, 23, "Leisure", "Weekend coffee", 95], [0, 24, "Food", "Takeaway pizza", 210], [0, 25, "Transport", "Uber to work", 138]
  ];
  spend.forEach(([m, d, c, note, amt]) => add("expense", amt, c, note, at(m, d, 12 + (d % 7), (d * 7) % 60)));
  add("income", 2500, "General", "Freelance design job", at(-1, 16, 15));
  add("income", 3200, "General", "Freelance design job", at(0, 18, 15));
  add("income", 950, "General", "Sold old phone", at(0, 9, 13));

  const today = new Date(Y, M, now.getDate());
  const due = d => new Date(Y, M, now.getDate() + d).toLocaleDateString("en-CA");
  const ago = (d, h = 10) => new Date(Y, M, now.getDate() - d, h).toISOString();
  return {
    version: 2, theme: "dark", tx, invalidTx: [], lastBackup: ago(3),
    budgets: { Food: 5000, Transport: 1500, Leisure: 1200, Bills: 1500, Tech: 800, Health: 1200 },
    recurring: [
      { id: "r1", type: "income", amount: 18500, category: "General", note: "Salary", day: 1, lastYM: today.toISOString().slice(0, 7) },
      { id: "r2", type: "expense", amount: 6200, category: "Housing", note: "Rent", day: 1, lastYM: today.toISOString().slice(0, 7) },
      { id: "r3", type: "expense", amount: 199, category: "Bills", note: "Netflix", day: 3, lastYM: today.toISOString().slice(0, 7) }
    ],
    goals: [
      { id: "g1", name: "New laptop", target: 15000, created: at(-2, 1) },
      { id: "g2", name: "Emergency fund", target: 10000, created: at(-2, 1) },
      { id: "g3", name: "Cape Town trip", target: 4000, created: at(-2, 1) }
    ],
    tasks: [
      { id: "t1", title: "Call landlord about the geyser", done: false, priority: "high", due: due(0), created: ago(2), doneAt: null, amount: 0, category: "General", poms: 1, order: 0 },
      { id: "t2", title: "Renew car licence disc", done: false, priority: "high", due: due(1), created: ago(4), doneAt: null, amount: 504, category: "Transport", poms: 0, order: 1 },
      { id: "t3", title: "Compare medical aid quotes", done: false, priority: "med", due: due(4), created: ago(3), doneAt: null, amount: 0, category: "Health", poms: 2, order: 2 },
      { id: "t4", title: "Cancel unused streaming trial", done: false, priority: "low", due: null, created: ago(5), doneAt: null, amount: 0, category: "General", poms: 0, order: 3 },
      { id: "t5", title: "Meal-prep lunches for the week", done: true, priority: "med", due: null, created: ago(2), doneAt: ago(0, 8), amount: 0, category: "Food", poms: 1, order: 4 },
      { id: "t6", title: "Sell old phone online", done: true, priority: "low", due: null, created: ago(9), doneAt: ago(1, 18), amount: 0, category: "General", poms: 0, order: 5 }
    ],
    streak: { count: 6, best: 11, last: today.toLocaleDateString("en-CA") },
    xp: 290,
    pomo: { taskId: "t3", endsAt: null, mode: "focus", completed: 7, remaining: 25 * 60 }
  };
}

async function newPage(opts = {}) {
  const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, acceptDownloads: true, ...opts });
  const page = await ctx.newPage();
  page.on("pageerror", e => console.log("pageerror:", e.message));
  // The real Chart.js (same bytes the integrity hash pins); fonts are installed locally.
  if (CHART) await page.route(/cdn\.jsdelivr\.net\/npm\/chart\.js/, r => r.fulfill({ contentType: "text/javascript", body: fs.readFileSync(CHART), headers: { "Access-Control-Allow-Origin": "*" } }));
  if (process.env.BLOCK_FONTS === "1") await page.route(/fonts\.(googleapis|gstatic)\.com/, r => r.abort());
  return { ctx, page };
}
async function openWithData(page, theme = "dark") {
  await page.goto("http://localhost:8830/");
  await page.evaluate(([s]) => localStorage.setItem("aspects.v2", JSON.stringify(s)), [{ ...demoState(), theme }]);
  await page.reload();
  await page.waitForFunction(() => !document.documentElement.classList.contains("splash-on"), null, { timeout: 9000 });
  await page.waitForFunction(() => typeof Chart !== "undefined");
  await sleep(900);
}
const shot = (page, name) => page.screenshot({ path: `${OUT}/${name}.png` });
async function view(page, v) { await page.click(`.tab[data-view="${v}"]`); await sleep(700); }
async function scrollTo(page, sel, offset = 120) {
  await page.evaluate(([s, o]) => { const el = document.querySelector(s); window.scrollTo(0, el.getBoundingClientRect().top + window.scrollY - o); }, [sel, offset]);
  await sleep(500);
}

/* ---------- phone, dark ---------- */
{
  const { ctx, page } = await newPage();
  await openWithData(page);
  await shot(page, "overview");
  await scrollTo(page, "#monthTiles", 170); await shot(page, "overview-2");
  await view(page, "money"); await shot(page, "money");
  await scrollTo(page, "#catChart", 150); await shot(page, "money-2");
  await scrollTo(page, "#recurringList", 150); await shot(page, "money-3");
  await view(page, "tasks"); await shot(page, "tasks");
  await scrollTo(page, "#taskList", 190); await shot(page, "tasks-2");
  await view(page, "goals"); await shot(page, "goals");
  await view(page, "settings"); await shot(page, "settings");
  await view(page, "overview");
  await page.click('#view-overview [data-open-tx="expense"]'); await sleep(500);
  await page.fill("#txAmount", "245.50"); await page.selectOption("#txCategory", "Food"); await page.fill("#txNote", "Lunch with the team");
  await page.locator("#txAmount").blur(); await sleep(300);
  await shot(page, "add-expense");
  await page.keyboard.press("Escape"); await sleep(400);
  await page.click("#paletteBtn"); await sleep(400);
  await page.fill("#paletteInput", ""); await sleep(200);
  await shot(page, "palette");
  await page.keyboard.press("Escape"); await sleep(300);
  // A PDF report of this month
  await view(page, "settings");
  await page.selectOption("#reportPeriod", "m0");
  const [dl] = await Promise.all([page.waitForEvent("download"), page.click("#reportBtn")]);
  await dl.saveAs(`${OUT}/report.pdf`);
  await ctx.close();
}

/* ---------- phone, light ---------- */
{
  const { ctx, page } = await newPage({ colorScheme: "light" });
  await openWithData(page, "light");
  await shot(page, "overview-light");
  await view(page, "goals"); await shot(page, "goals-light");
  await ctx.close();
}

/* ---------- desktop ---------- */
{
  const { ctx, page } = await newPage({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 1.5 });
  await openWithData(page);
  await shot(page, "desktop-overview");
  await view(page, "money"); await shot(page, "desktop-money");
  await ctx.close();
}

/* ---------- opening animation frames ---------- */
{
  const { ctx, page } = await newPage();
  await page.addInitScript(() => { const st = window.setTimeout; window.setTimeout = (f, ms, ...a) => st(f, ms >= 4000 && ms <= 5000 ? 1e9 : ms, ...a); });
  await page.goto("http://localhost:8830/");
  await page.evaluate(() => document.getAnimations().forEach(a => a.pause()));
  for (const t of [350, 1000, 2400, 4400]) {
    await page.evaluate(t => document.getAnimations().forEach(a => { a.currentTime = t; }), t);
    await shot(page, "splash-" + t);
  }
  await ctx.close();
}

await browser.close();
server.kill();
console.log("done:", fs.readdirSync(OUT).join(" "));
